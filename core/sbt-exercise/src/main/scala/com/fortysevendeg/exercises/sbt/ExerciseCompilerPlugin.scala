
package com.fortysevendeg.exercises
package sbt

import _root_.sbt._
import _root_.sbt.Keys._

import scala.io.Codec
import scala.util.{ Try, Success, Failure }

import java.io.File
import java.net.URLClassLoader

object ExerciseCompilerKeys {
  val compileExercises = TaskKey[Seq[File]]("compileExercises", "Compile scala exercises")
  val CompileExercises = config("compile-exercises") extend (Compile) hide
}

object ExerciseCompilerPlugin extends AutoPlugin {
  import ExerciseCompilerKeys._

  override def requires = plugins.JvmPlugin

  override def trigger = noTrigger

  val autoImport = ExerciseCompilerKeys

  override def projectSettings: Seq[Setting[_]] =
    inConfig(Compile)(exerciseSettings) ++
      inConfig(CompileExercises)(Defaults.configSettings) ++
      inConfig(CompileExercises)(unmanagedSourceDirectories in Compile <++= sourceDirectories in (Compile, compileExercises)) ++
      dependencySettings

  private def scalacEncoding(options: Seq[String]): String = {
    val i = options.indexOf("-encoding") + 1
    if (i > 0 && i < options.length) options(i) else "UTF-8"
  }

  def compileExercisesTask = Def.task {
    (compile in CompileExercises).value
    ExerciseCompiler.compile(
      (sourceDirectories in compileExercises).value,
      (target in compileExercises).value,
      (fork in compileExercises).value,
      Codec(scalacEncoding(scalacOptions.value)),
      streams.value.log
    )
    Nil
  }

  def exerciseSettings: Seq[Setting[_]] = Seq(
    sourceDirectories in compileExercises := Seq(sourceDirectory.value / "exercises"),
    sources in compileExercises <<= (sourceDirectories in compileExercises) map (_ ** "*" get),
    watchSources in Defaults.ConfigGlobal <++= sources in compileExercises,
    target in compileExercises := crossTarget.value / "exercises" / Defaults.nameForSrc(configuration.value.name),
    compileExercises := compileExercisesTask.value,
    sourceGenerators <+= compileExercises,
    managedSourceDirectories <+= target in compileExercises,
    fork in compileExercises := false
  )

  def dependencySettings: Seq[Setting[_]] = Seq(
    libraryDependencies += "com.47deg" %% "definitions" % "0.0.0"
  )

}

object ExerciseCompiler {

  private val COMPILER_MAIN = "com.fortysevendeg.exercises.compiler.CompilerMain"

  private val NullLogger = new AbstractLogger {
    def getLevel: Level.Value = Level.Error
    def setLevel(newLevel: Level.Value) {}
    def getTrace = 0
    def setTrace(flag: Int) {}
    def successEnabled = false
    def setSuccessEnabled(flag: Boolean) {}
    def control(event: ControlEvent.Value, message: ⇒ String) {}
    def logAll(events: Seq[LogEvent]) {}
    def trace(t: ⇒ Throwable) {}
    def success(message: ⇒ String) {}
    def log(level: Level.Value, message: ⇒ String) {}
  }

  def compile(
    sourceDirectories: Seq[File],
    targetDirectory:   File,
    fork:              Boolean,
    codec:             Codec,
    log:               Logger
  ) {

    val exercisePaths = sourceDirectories.map(_.getPath)

    log.info(s"Launching exercise compiler (fork = $fork), classpath:")
    Meta.compilerClasspath.foreach { entry ⇒
      log.info(s"~ $entry")
    }

    def logExitCode(exitCode: Int) {
      log.info(s"~> exit code $exitCode")
    }

    fork match {
      case true ⇒
        val options = ForkOptions(bootJars = Meta.compilerClasspath)
        val exitCode = Fork.java(options, COMPILER_MAIN +: exercisePaths)

        logExitCode(exitCode)

      case false ⇒
        val loader = new URLClassLoader(Path.toURLs(Meta.compilerClasspath), null)
        val res = for {
          compilerMainClass ← Try(loader.loadClass(COMPILER_MAIN))
          compilerMain ← Try(compilerMainClass.getMethod("main", classOf[Array[String]]))
        } yield {
          val previous = TrapExit.installManager()
          val exitCode = TrapExit(compilerMain.invoke(
            null, exercisePaths.toArray[String].asInstanceOf[Array[String]]
          ), NullLogger)
          TrapExit.uninstallManager(previous)

          logExitCode(exitCode)
        }

        res match {
          case Failure(e) ⇒ log.error(s"~ error ${e.getMessage}")
          case _          ⇒
        }
    }
  }

}
