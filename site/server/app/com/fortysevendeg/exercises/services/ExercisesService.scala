/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package com.fortysevendeg.exercises.services

import com.fortysevendeg.exercises.Exercises
import com.fortysevendeg.exercises.MethodEval

import cats.data.Xor
import cats.data.Ior
import cats.std.option._
import cats.syntax.flatMap._

/** Main entry point and service for libraries, categories and exercises discovery + evaluation
  */
object ExercisesService {
  lazy val methodEval = new MethodEval()

  val (errors, runtimeLibraries) = Exercises.discoverLibraries(cl = ExercisesService.getClass.getClassLoader)
  val (libraries, librarySections) = {
    val libraries1 = colorize(runtimeLibraries)
    errors.foreach(error ⇒ Logger.warn(s"$error")) // TODO: handle errors better?
    (
      libraries1.map(convertLibrary),
      libraries1.map(library0 ⇒ library0.name → library0.sections.map(convertSection)).toMap
    )
  }

  def section(libraryName: String, name: String): Option[shared.Section] =
    librarySections.get(libraryName) >>= (_.find(_.name == name))

  def evaluate(evaluation: shared.ExerciseEvaluation): shared.ExerciseEvaluation.Result = {
    val res = methodEval.eval(
      evaluation.method,
      evaluation.args
    )
    Logger.info(s"evaluation for $evaluation: $res")
    res.toSuccessXor.bimap(
      _.bimap(_.foldedException, _.e),
      _ ⇒ Unit
    )
  }

  def reorderLibraries(topLibNames: List[String], libraries: List[shared.Library]): List[shared.Library] = {
    val libsByName = libraries.groupBy(_.name)
    val topLibs = for {
      name ← topLibNames
      lib ← libsByName.get(name).getOrElse(Nil)
    } yield lib
    val restLibs = libraries.filterNot(topLibs.contains(_))

    topLibs ++ restLibs
  }
}

sealed trait RuntimeSharedConversions {
  import com.fortysevendeg.exercises._

  // not particularly clean, but this assigns colors
  // to libraries that don't have a default color provided
  // TODO: make this nicer
  def colorize(libraries: List[Library]): List[Library] = {
    libraries
    val autoPalette = List(
      "#00587A",
      "#44BBFF",
      "#EBF680",
      "#66CC99",
      "#FCA65F",
      "#112233",
      "#FC575E",
      "#CDCBA6",
      "#37465D",
      "#DD6F47",
      "#6AB0AA",
      "#008891",
      "#0F3057"
    )

    val (_, res) = libraries.foldLeft((autoPalette, Nil: List[Library])) { (acc, library) ⇒
      val (colors, librariesAcc) = acc
      if (library.color.isEmpty) {
        val (color, colors0) = colors match {
          case head :: tail ⇒ Some(head) → tail
          case Nil          ⇒ None → Nil
        }
        colors0 → (DefaultLibrary(
          name = library.name,
          description = library.description,
          color = color,
          sections = library.sections
        ) :: librariesAcc)
      } else
        colors → (library :: librariesAcc)
    }
    res.reverse
  }

  def convertLibrary(library: Library) =
    shared.Library(
      name = library.name,
      description = library.description,
      color = library.color getOrElse "black",
      sections = library.sections map convertSection
    )

  def convertSection(section: Section) =
    shared.Section(
      name = section.name,
      description = Option(section.description),
      exercises = section.exercises.map(convertExercise)
    )

  def convertExercise(exercise: Exercise) =
    shared.Exercise(
      method = exercise.qualifiedMethod,
      name = Option(exercise.name),
      description = exercise.description,
      code = Option(exercise.code),
      explanation = exercise.explanation
    )

}
