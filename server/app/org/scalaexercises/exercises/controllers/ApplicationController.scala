/*
 * Copyright 2014-2020 47 Degrees Open Source <https://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalaexercises.exercises.controllers

import cats.effect.IO
import cats.implicits._
import github4s.Github
import org.scalaexercises.algebra.exercises.ExerciseOps
import org.scalaexercises.algebra.progress.{UserExercisesProgress, UserProgressOps}
import org.scalaexercises.algebra.user.UserOps
import org.scalaexercises.exercises.Secure
import org.scalaexercises.exercises.services.ExercisesService
import org.scalaexercises.exercises.utils.ConfigUtils
import org.scalaexercises.types.exercises.{Contribution, Contributor}
import org.scalaexercises.types.github.{Authorize, Repository}
import play.api.cache.AsyncCacheApi
import play.api.mvc._
import play.api.routing.JavaScriptReverseRouter
import play.api.{Configuration, Logger, Mode}
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

class ApplicationController(config: Configuration, components: ControllerComponents)(
    cache: AsyncCacheApi
)(implicit
    executionContext: ExecutionContext,
    exerciseOps: ExerciseOps[IO],
    userOps: UserOps[IO],
    userProgressOps: UserProgressOps[IO],
    userExercisesProgress: UserExercisesProgress[IO],
    cs: ContextShift[IO],
    service: ExercisesService,
    configUtils: ConfigUtils,
    mode: Mode
) extends BaseController
    with AuthenticationModule {

  lazy val topLibraries: List[String] =
    config.getOptional[Seq[String]]("exercises.top_libraries") map (_.toList) getOrElse Nil

  val MainRepoCacheKey = "scala-exercises.repo"

  lazy val clientResource = BlazeClientBuilder[IO](executionContext).resource

  private lazy val logger = Logger(this.getClass)

  private[this] def getAuthorizeUrl(clientId: String, redirectUri: String) = {
    clientResource.use { client =>
      Github[IO](client, None).auth
        .authorizeUrl(clientId, redirectUri, List.empty)
        .flatMap(response =>
          IO.fromEither(response.result.map(auth => Authorize(auth.url, auth.state)))
        )
    }
  }

  private[this] def getRepository(owner: String, repo: String) = {
    clientResource.use { client =>
      Github[IO](client, sys.env.get("GITHUB_TOKEN")).repos
        .get(owner, repo)
        .flatMap(response =>
          IO.fromEither(
            response.result.map(repo =>
              Repository(
                subscribers = repo.status.subscribers_count.getOrElse(0),
                stargazers = repo.status.stargazers_count,
                forks = repo.status.forks_count
              )
            )
          )
        )
        .redeem(
          { _ =>
            logger.error("Error fetching scala-exercises repository information")
            None
          },
          repo => repo.some
        )
    }
  }

  /**
   * cache the main repo stars, forks and watchers info for 30 mins
   */
  private[this] def scalaexercisesRepo: IO[Option[Repository]] = {
    IO.fromFuture(IO(cache.get[Repository](MainRepoCacheKey)))(cs).flatMap {
      case repo if repo.nonEmpty => IO.pure(repo)
      case None                  => getRepository(configUtils.githubSiteOwner, configUtils.githubSiteRepo)
    }
  }

  def index =
    Secure(Action.async { implicit request =>
      (for {
        authorize <- getAuthorizeUrl(configUtils.githubAuthId, configUtils.callbackUrl)
        libraries <- exerciseOps.getLibraries.map(service.reorderLibraries(topLibraries, _))
        user      <- userOps.getUserByLogin(request.session.get("user").getOrElse(""))
        progress  <- userExercisesProgress.fetchMaybeUserProgress(user)
        repo      <- scalaexercisesRepo
        result = (libraries, user, request.session.get("oauth-token"), progress, authorize) match {
          case (libraries, user, Some(_), progress, _) =>
            Ok(
              views.html.templates.home.index(
                user = user,
                libraries = libraries,
                progress = progress,
                repo = repo.getOrElse(Repository(0, 0, 0)),
                config = config
              )
            )
          case (libraries, None, None, progress, authorize) =>
            Ok(
              views.html.templates.home
                .index(
                  user = None,
                  libraries = libraries,
                  progress = progress,
                  redirectUrl = Option(authorize.url),
                  repo = repo.getOrElse(Repository(0, 0, 0)),
                  config = config
                )
            ).withSession("oauth-state" -> authorize.state)
          case (libraries, Some(user), None, _, _) => Unauthorized("Session token not found")
        }
      } yield result).unsafeToFuture()

    })

  def library(libraryName: String) =
    Secure(Action.async { implicit request =>
      val ops = for {
        library <- exerciseOps.getLibrary(libraryName)
        user    <- userOps.getUserByLogin(request.session.get("user").getOrElse(""))
        section <- user.flatTraverse(userProgressOps.getLastSeenSection(_, libraryName))
      } yield (library, user, section)

      (ops map {
        case (Some(library), _, Some(sectionName)) if library.sectionNames.contains(sectionName) =>
          Redirect(s"$libraryName/$sectionName")
        case (Some(library), _, _) if library.sectionNames.nonEmpty =>
          Redirect(s"$libraryName/${library.sectionNames.head}")
        case (None, _, _) => NotFound("Library not found")
      }).unsafeToFuture()
    })

  def section(libraryName: String, sectionName: String) =
    Secure(Action.async { implicit request =>
      val ops =
        for {
          authorize <- getAuthorizeUrl(configUtils.githubAuthId, configUtils.callbackUrl)
          library   <- exerciseOps.getLibrary(libraryName)
          section   <- exerciseOps.getSection(libraryName, sectionName)
          contributors = toContributors(
            section.fold(List.empty[Contribution])(s => s.contributions)
          )
          user        <- userOps.getUserByLogin(request.session.get("user").getOrElse(""))
          libProgress <- userExercisesProgress.fetchMaybeUserProgressByLibrary(user, libraryName)
        } yield (
          library,
          section,
          user,
          request.session.get("oauth-token"),
          libProgress,
          authorize,
          contributors
        )

      (ops map {
        case (Some(l), Some(s), user, Some(token), libProgress, _, contributors) =>
          Ok(
            views.html.templates.library.index(
              library = l,
              section = s,
              user = user,
              progress = libProgress,
              contributors = contributors,
              config = config
            )
          )
        case (Some(l), Some(s), user, None, libProgress, authorize, contributors) =>
          Ok(
            views.html.templates.library.index(
              library = l,
              section = s,
              user = user,
              progress = libProgress,
              redirectUrl = Option(authorize.url),
              contributors = contributors,
              config = config
            )
          ).withSession("oauth-state" -> authorize.state)
        case (Some(l), None, _, _, _, _, _) => NotFound("Section not found")
        case (None, _, _, _, _, _, _)       => NotFound("Library not found")
        case (_, _, _, _, _, _, _)          => NotFound("Library and section not found")

      }).unsafeToFuture()
    })

  def javascriptRoutes =
    Secure(Action { implicit request =>
      import routes.javascript._
      Ok(
        JavaScriptReverseRouter("jsRoutes")(
          ExercisesController.evaluate,
          UserProgressController.fetchUserProgressBySection
        )
      ).as("text/javascript")
    })

  private def toContributors(contributions: List[Contribution]): List[Contributor] =
    contributions.map(c => Contributor(c.author, c.authorUrl, c.avatarUrl)).distinct

  def onHandlerNotFound(route: String) =
    Action { implicit request =>
      if (route.endsWith("/"))
        MovedPermanently("/" + request.path.take(request.path.length - 1).dropWhile(_ == '/'))
      else
        NotFound
    }

  override protected def controllerComponents: ControllerComponents = components
}
