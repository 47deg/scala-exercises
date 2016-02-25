/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

import com.fortysevendeg.exercises.persistence.domain.{ UserQueries ⇒ Q }
import com.fortysevendeg.exercises.persistence.repositories.UserRepository._
import doobie.contrib.specs2.analysisspec.AnalysisSpec
import doobie.util.query.Query
import doobie.util.update.Update
import org.specs2.mutable.Specification
import shared.User
import test.database.DatabaseInstance

class UserQueriesSpec
    extends Specification
    with AnalysisSpec
    with DatabaseInstance {

  check(Query[Unit, User](Q.all))
  check(Query[Long, User](Q.findById))
  check(Query[String, User](Q.findByLogin))
  check(Update[UpdateParams](Q.update))
  check(Update[InsertParams](Q.insert))
  check(Update[Long](Q.deleteById))
  check(Update[Unit](Q.deleteAll))
}