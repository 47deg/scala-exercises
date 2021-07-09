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

package org.scalaexercises.exercises.support

import cats.effect.IO
import doobie.util.transactor.Transactor
import play.api.db.evolutions._
import play.api.db.{Database, Databases}
import com.dimafeng.testcontainers._

import scala.concurrent.ExecutionContext

trait DatabaseInstance {

  val role: String   = "scala-exercises"
  lazy val container = PostgreSQLContainer().configure(_.withUsername(role))

  implicit val ContextShiftIO: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)

  def createDatabase(): Database = {
    Databases(
      driver = container.driverClassName,
      url = container.jdbcUrl,
      name = "default",
      config = Map(
        "user"     -> container.username,
        "password" -> container.password
      )
    )
  }

  def evolve(db: Database): Database = {
    Evolutions.applyEvolutions(db)
    db
  }

  def createTransactor(db: Database): Transactor[IO] =
    Transactor.fromDriverManager[IO](
      container.driverClassName,
      container.jdbcUrl,
      container.username,
      container.password
    )

  lazy val databaseTransactor: Transactor[IO] = createTransactor(evolve(createDatabase()))
}
