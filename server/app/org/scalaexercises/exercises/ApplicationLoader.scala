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

package org.scalaexercises.exercises

import _root_.controllers._
import cats.effect.{ConcurrentEffect, IO}
import com.typesafe.config.ConfigFactory
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor
import org.scalaexercises.algebra.exercises.ExerciseOps
import org.scalaexercises.algebra.progress._
import org.scalaexercises.algebra.user.UserOps
import org.scalaexercises.exercises.controllers._
import org.scalaexercises.exercises.services.ExercisesService
import org.scalaexercises.exercises.services.handlers._
import org.scalaexercises.exercises.utils.ConfigUtils
import play.api.ApplicationLoader.Context
import play.api._
import play.api.cache.caffeine.CaffeineCacheComponents
import play.api.db.evolutions.{DynamicEvolutions, EvolutionsComponents}
import play.api.db.{DBComponents, HikariCPComponents}
import play.api.libs.ws._
import play.api.libs.ws.ahc.AhcWSClient
import play.api.mvc.EssentialFilter
import router.Routes

import scala.concurrent.Future
import cats.effect.Resource

class ExercisesApplicationLoader extends ApplicationLoader {
  def load(context: Context) = {
    val mode = context.environment.mode.toString.toLowerCase
    new Components(
      context.copy(
        initialConfiguration = context.initialConfiguration
          .withFallback(Configuration(ConfigFactory.load(s"application.$mode.conf")))
      )
    ).application
  }
}

class Components(context: Context)
    extends BuiltInComponentsFromContext(context)
    with DBComponents
    with CaffeineCacheComponents
    with EvolutionsComponents
    with HikariCPComponents {

  applicationEvolutions.start()

  override lazy val dynamicEvolutions: DynamicEvolutions = new DynamicEvolutions

  private lazy val threadPool =
    configuration.getOptional[Int]("play.db.prototype.hikaricp.maximumPoolSize").getOrElse(32)

  lazy val transactorResource = (for {
    ec <- ExecutionContexts.fixedThreadPool[IO](threadPool)
    cs = IO.contextShift(ec)
    blocker <- Resource.unit[IO]
  } yield Transactor.fromDataSource[IO](dbApi.database("default").dataSource, ec, blocker)(
    implicitly,
    cs
  )).allocated

  lazy implicit val trans: Transactor[IO] = transactorResource.map(_._1).unsafeRunSync

  implicit val wsClient: WSClient = AhcWSClient()

  implicit val mode = environment.mode

  implicit def cs: ContextShift[IO] = IO.contextShift(controllerComponents.executionContext)

  implicit def ce: ConcurrentEffect[IO] = IO.ioConcurrentEffect(cs)

  implicit val bodyParserAnyContent = controllerComponents.parsers.anyContent

  implicit lazy val configUtils = ConfigUtils(configuration)

  implicit lazy val service = new ExercisesService(environment.classLoader)

  lazy val generateRoutes: Routes = {
    implicit val userOps: UserOps[IO]                    = new UserOpsHandler[IO]
    implicit val userProgressOps: UserProgressOps[IO]    = new UserProgressOpsHandler[IO]
    implicit val exerciseOps: ExerciseOps[IO]            = new ExerciseOpsHandler[IO]()
    implicit val userProgress: UserExercisesProgress[IO] = new UserExercisesProgress[IO]

    val applicationController =
      new ApplicationController(configuration, controllerComponents)(defaultCacheApi)
    val exercisesController    = new ExercisesController(configuration, controllerComponents)
    val userController         = new UserController(controllerComponents)
    val oauthController        = new OAuthController(configuration, controllerComponents)
    val userProgressController = new UserProgressController(controllerComponents)
    val loaderIOController     = new LoaderIOController(configuration, controllerComponents)
    val sitemapController      = new SitemapController(controllerComponents)

    new Routes(
      httpErrorHandler,
      sitemapController,
      loaderIOController,
      applicationController,
      userController,
      exercisesController,
      assets,
      oauthController,
      userProgressController
    )
  }

  lazy val assets = new _root_.controllers.Assets(
    httpErrorHandler,
    new DefaultAssetsMetadata(
      AssetsConfiguration.fromConfiguration(configuration, mode),
      environment.resource(_),
      fileMimeTypes
    )
  )

  override lazy val router = generateRoutes

  applicationLifecycle.addStopHook({ () =>
    transactorResource.flatMap(_._2).unsafeRunSync()
    Future(wsClient.close())
  })

  override def httpFilters: Seq[EssentialFilter] = Seq.empty
}
