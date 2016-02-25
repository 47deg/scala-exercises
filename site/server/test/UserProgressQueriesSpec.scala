import doobie.contrib.specs2.analysisspec.AnalysisSpec
import doobie.util.query.Query
import doobie.util.update.Update
import org.specs2.mutable.Specification
import shared.UserProgress
import com.fortysevendeg.exercises.persistence.domain.{ UserProgressQueries ⇒ Q }
import test.database.DatabaseInstance

class UserProgressQueriesSpec
    extends Specification
    with AnalysisSpec
    with DatabaseInstance {

  check(Query[Unit, UserProgress](Q.all))
  check(Query[Long, UserProgress](Q.findById))
  check(Query[Long, UserProgress](Q.findByUserId))
  check(Query[(Long, String, String, String, Int), UserProgress](Q.findByExerciseVersion))
  check(Update[(String, String, String, Int, String, String, Boolean, Long)](Q.update))
  check(Update[(Long, String, String, String, Int, String, String, Boolean)](Q.insert))
  check(Update[Long](Q.deleteById))
  check(Update[Unit](Q.deleteAll))
}
