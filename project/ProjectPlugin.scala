import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys
import com.alejandrohdezma.sbt.github.SbtGithubPlugin

object ProjectPlugin extends AutoPlugin {

  override def trigger: PluginTrigger = allRequirements

  override def requires: Plugins = SbtGithubPlugin

  object autoImport {

    lazy val V = new {
      val bootstrap: String                 = "3.3.7"
      val cats: String                      = "2.6.1"
      val catsEffect: String                = "2.5.1"
      val commonsio: String                 = "2.10.0"
      val doobie: String                    = "0.13.4"
      val exercisesCats: String             = "0.6.4"
      val exercisesCirce: String            = "0.6.4"
      val exercisesDoobie: String           = "0.6.4"
      val exercisesFetch: String            = "0.6.4"
      val exercisesFpinscala: String        = "0.6.4"
      val exercisesMonocle: String          = "0.6.4"
      val exercisesScalacheck: String       = "0.6.4"
      val exercisesScalatutorial: String    = "0.6.4"
      val exercisesShapeless: String        = "0.6.4"
      val exercisesStdlib: String           = "0.6.4"
      val github4s: String                  = "0.29.0"
      val highlightjs: String               = "10.1.2"
      val jsDependencyJquery: String        = "3.4.1"
      val jsDependencyScalajsdom: String    = "1.1.0"
      val jsDependencyScalajsjquery: String = "1.0.0"
      val jsDependencyScalatags: String     = "0.9.4"
      val jsDependencyUtest: String         = "0.7.10"
      val knockoff: String                  = "0.9.0"
      val monix: String                     = "3.4.0"
      val newrelic: String                  = "5.13.0"
      val postgres: String                  = "42.2.8"
      val runtime: String                   = "0.6.4"
      val scalacheck: String                = "1.14.3"
      val scalacheckShapeless: String       = "1.2.5"
      val scalajsscripts: String            = "1.2.0"
      val scalatest: String                 = "3.2.9"
      val scalatestplusScheck: String       = "3.2.2.0"
      val shapeless: String                 = "2.3.3"
      val testcontainers: String            = "0.39.5"
      val upickle: String                   = "1.4.0"
      val webjars: String                   = "2.8.8"
    }

  }

  override def projectSettings: Seq[Def.Setting[_]] =
    Seq(
      scmInfo := Some(
        ScmInfo(
          url("https://github.com/scala-exercises/scala-exercises"),
          "scm:git:https://github.com/scala-exercises/scala-exercises.git",
          Some("scm:git:git@github.com:scala-exercises/scala-exercises.git")
        )
      ),
      scalacOptions ~= (_ filterNot (_ == "-Xfuture")),
      javacOptions ++= Seq("-encoding", "UTF-8", "-Xlint:-options"),
      fork in Test := false,
      parallelExecution in Test := false,
      cancelable in Global := true,
      ScoverageKeys.coverageFailOnMinimum := false
    )

}
