/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package com.fortysevendeg.exercises.controllers

import com.fortysevendeg.exercises.app._
import com.fortysevendeg.exercises.persistence.domain.SaveUserProgress
import com.fortysevendeg.exercises.services.free.{ UserOps, UserProgressOps }
import com.fortysevendeg.exercises.services.interpreters.ProdInterpreters
import com.fortysevendeg.exercises.utils.StringUtils.ExerciseType
import com.fortysevendeg.shared.free.ExerciseOps
import doobie.imports._
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, BodyParsers, Controller }
import shared.{ ExerciseEvaluation, User }

import com.fortysevendeg.exercises.services.interpreters.FreeExtensions._

import scalaz.concurrent.Task

class ExercisesController(
    implicit
    exerciseOps:     ExerciseOps[ExercisesApp],
    userOps:         UserOps[ExercisesApp],
    userProgressOps: UserProgressOps[ExercisesApp],
    T:               Transactor[Task]
) extends Controller with JsonFormats with AuthenticationModule with ProdInterpreters {

  def evaluate(libraryName: String, sectionName: String): Action[JsValue] =
    AuthenticationAction(BodyParsers.parse.json) { request ⇒
      request.body.validate[ExerciseEvaluation] match {
        case JsSuccess(evaluation, _) ⇒

          userOps.getUserByLogin(request.userId).runTask match {
            case Xor.Right(Some(user)) ⇒
              val eval = for {
                exerciseEvaluation ← exerciseOps.evaluate(evaluation = evaluation)
                _ ← userProgressOps.saveUserProgress(
                  mkSaveProgressRequest(user.id, evaluation, exerciseEvaluation.isRight)
                )
              } yield exerciseEvaluation

        eval.runTask.fold(
          e ⇒ BadRequest(s"Evaluation failed : $e"),
          _.fold(
            _.fold(
              e ⇒ BadRequest(s"Compilation error : ${e.getMessage}"),
              e ⇒ BadRequest(s"Runtime error : ${e.getMessage}")
            ),
            v ⇒ Ok(s"Evaluation succeeded : $v")
          )
        )

    }

  private[this] def mkSaveProgressRequest(userId: Long, evaluation: ExerciseEvaluation, success: Boolean) =
    new SaveUserProgress.Request(
      userId = userId,
      libraryName = evaluation.libraryName,
      sectionName = evaluation.sectionName,
      method = evaluation.method,
      version = evaluation.version,
      exerciseType = ExerciseType fromString evaluation.exerciseType,
      args = evaluation.args,
      succeeded = success
    )
}
