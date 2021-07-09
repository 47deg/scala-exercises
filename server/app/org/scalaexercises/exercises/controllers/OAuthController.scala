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
import org.scalaexercises.algebra.user.UserOps
import org.scalaexercises.exercises.Secure
import org.scalaexercises.exercises.utils.ConfigUtils
import org.scalaexercises.types.github.{GithubUser, OAuthToken}
import org.scalaexercises.types.user.UserCreation
import play.api.mvc.{AnyContent, BaseController, ControllerComponents}
import play.api.{Configuration, Mode}
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext

class OAuthController(conf: Configuration, components: ControllerComponents)(implicit
    executionContext: ExecutionContext,
    cs: ContextShift[IO],
    userOps: UserOps[IO],
    configUtils: ConfigUtils,
    mode: Mode
) extends BaseController {

  lazy val clientResource = BlazeClientBuilder[IO](executionContext).resource

  private[this] def getAccessToken(code: String, state: String) =
    clientResource.use { client =>
      Github[IO](client, None).auth
        .getAccessToken(
          configUtils.githubAuthId,
          configUtils.githubAuthSecret,
          code,
          configUtils.callbackUrl,
          state
        )
        .flatMap(response =>
          IO.fromEither(response.result.map(token => OAuthToken(token.access_token)))
        )
    }

  def callback(
      codeOpt: Option[String] = None,
      stateOpt: Option[String] = None
  ): Secure[AnyContent] =
    Secure(Action.async { implicit request =>
      (codeOpt, stateOpt, request.session.get("oauth-state")).tupled
        .fold(IO.pure(BadRequest("Missing `code` or `state`"))) { case (code, state, oauthState) =>
          if (state == oauthState) {
            getAccessToken(code, state).map { ot =>
              Redirect(configUtils.successUrl).withSession("oauth-token" -> ot.accessToken)
            }
          } else IO.pure(BadRequest("Invalid github login"))
        }
        .unsafeToFuture()
    })

  def createUserRequest(githubUser: GithubUser): UserCreation.Request =
    UserCreation.Request(
      login = githubUser.login,
      name = githubUser.name,
      githubId = githubUser.login,
      pictureUrl = githubUser.avatar,
      githubUrl = githubUser.url,
      email = githubUser.email
    )

  private[this] def getAuthUser(accessToken: Option[String]): IO[GithubUser] =
    clientResource.use { client =>
      Github[IO](client, accessToken).users
        .getAuth()
        .flatMap(response =>
          IO.fromEither(
            response.result.map(user =>
              GithubUser(
                login = user.login,
                name = user.name,
                avatar = user.avatar_url,
                url = user.html_url,
                email = user.email
              )
            )
          )
        )
    }

  def success(): Secure[AnyContent] =
    Secure(Action.async { implicit request =>
      request.session
        .get("oauth-token")
        .fold(IO.pure(Unauthorized("Missing OAuth token"))) { accessToken =>
          val ops = for {
            ghuser <- getAuthUser(accessToken.some)
            user   <- userOps.getOrCreate(createUserRequest(ghuser))
          } yield (ghuser, user)

          ops.map { case (ghu, u) =>
            Redirect(request.headers.get("referer") match {
              case Some(url) if !url.contains("github") => url
              case _                                    => "/"
            }).withSession("oauth-token" -> accessToken, "user" -> ghu.login)
          }
        }
        .unsafeToFuture()
    })

  def logout(): Secure[AnyContent] = Secure(Action(Redirect("/").withNewSession))

  override protected def controllerComponents: ControllerComponents = components
}
