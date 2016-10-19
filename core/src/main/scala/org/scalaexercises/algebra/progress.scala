/*
 * scala-exercises-server
 * Copyright (C) 2015-2016 47 Degrees, LLC. <http://www.47deg.com>
 */

package org.scalaexercises.algebra.progress

import org.scalaexercises.algebra.exercises.ExerciseOps
import org.scalaexercises.types.user._
import org.scalaexercises.types.exercises._
import org.scalaexercises.types.progress._

import cats.Monad
import cats.implicits._
import cats.free._

/** Users Progress Ops GADT
  */
sealed trait UserProgressOp[A]

final case class GetLastSeenSection(
  user:    User,
  library: String
) extends UserProgressOp[Option[String]]

final case class GetExerciseEvaluations(
  user:    User,
  library: String,
  section: String
) extends UserProgressOp[List[UserProgress]]

final case class UpdateUserProgress(
  userProgress: SaveUserProgress.Request
) extends UserProgressOp[UserProgress]

/** Exposes User Progress operations as a Free monadic algebra that may be combined with other Algebras via
  * Coproduct
  */
class UserProgressOps[F[_]](
    implicit
    I:  Inject[UserProgressOp, F],
    EO: ExerciseOps[F],
    FM: Monad[Free[F, ?]]
) {
  def saveUserProgress(userProgress: SaveUserProgress.Request): Free[F, UserProgress] =
    Free.inject[UserProgressOp, F](UpdateUserProgress(userProgress))

  def getExerciseEvaluations(user: User, library: String, section: String): Free[F, List[UserProgress]] =
    Free.inject[UserProgressOp, F](GetExerciseEvaluations(user, library, section))

  def getLastSeenSection(user: User, library: String): Free[F, Option[String]] =
    Free.inject[UserProgressOp, F](GetLastSeenSection(user, library))

  def getSolvedExerciseCount(user: User, library: String, section: String): Free[F, Int] =
    getExerciseEvaluations(user, library, section).map(tried ⇒ tried.count(_.succeeded))

  def fetchMaybeUserProgress(user: Option[User]): Free[F, OverallUserProgress] = {
    user.fold(anonymousUserProgress)(fetchUserProgress)
  }

  private[this] def anonymousUserProgress: Free[F, OverallUserProgress] = for {
    libraries ← EO.getLibraries
    libs = libraries.map(l ⇒ {
      OverallUserProgressItem(
        libraryName = l.name,
        completedSections = 0,
        totalSections = l.sections.size
      )
    })
  } yield OverallUserProgress(libraries = libs)

  def getCompletedSectionCount(user: User, library: Library): Free[F, Int] =
    library.sections.traverseU(isSectionCompleted(user, library.name, _)).map(
      _.count(identity)
    )

  private[this] def isSectionCompleted(user: User, libraryName: String, section: Section): Free[F, Boolean] =
    getSolvedExerciseCount(user, libraryName, section.name).map(solvedExercises ⇒
      solvedExercises == section.exercises.size)

  def fetchUserProgress(user: User): Free[F, OverallUserProgress] = {
    def getLibraryProgress(library: Library): Free[F, OverallUserProgressItem] =
      getCompletedSectionCount(user, library).map { completedSections ⇒
        OverallUserProgressItem(
          libraryName = library.name,
          completedSections = completedSections,
          totalSections = library.sections.size
        )
      }

    for {
      allLibraries ← EO.getLibraries
      libraryProgress ← allLibraries.traverseU(getLibraryProgress)
    } yield OverallUserProgress(libraries = libraryProgress)
  }

  def fetchMaybeUserProgressByLibrary(user: Option[User], libraryName: String): Free[F, LibraryProgress] = {
    user.fold(anonymousUserProgressByLibrary(libraryName))(fetchUserProgressByLibrary(_, libraryName))
  }

  private[this] def anonymousUserProgressByLibrary(libraryName: String): Free[F, LibraryProgress] = {
    for {
      lib ← EO.getLibrary(libraryName)
      sections = lib.foldMap(_.sections.map(s ⇒
        SectionProgress(
          sectionName = s.name,
          succeeded = false
        )))
    } yield LibraryProgress(
      libraryName = libraryName,
      sections = sections
    )
  }

  def fetchUserProgressByLibrary(user: User, libraryName: String): Free[F, LibraryProgress] = {
    def getSectionProgress(section: Section): Free[F, SectionProgress] =
      isSectionCompleted(user, libraryName, section).map { completed ⇒
        SectionProgress(
          sectionName = section.name,
          succeeded = completed
        )
      }

    for {
      maybeLib ← EO.getLibrary(libraryName)
      libSections = maybeLib.foldMap(_.sections)
      sectionProgress ← libSections.traverseU(getSectionProgress)
    } yield LibraryProgress(
      libraryName = libraryName,
      sections = sectionProgress
    )
  }

  def fetchUserProgressByLibrarySection(
    user:        User,
    libraryName: String,
    sectionName: String
  ): Free[F, SectionExercises] = {
    for {
      maybeSection ← EO.getSection(libraryName, sectionName)
      evaluations ← getExerciseEvaluations(user, libraryName, sectionName)
      sectionExercises = maybeSection.foldMap(_.exercises)
      exercises = sectionExercises.map(ex ⇒ {
        val maybeEvaluation = evaluations.find(_.method == ex.method)
        ExerciseProgress(
          methodName = ex.method,
          args = maybeEvaluation.foldMap(_.args),
          succeeded = maybeEvaluation.fold(false)(_.succeeded)
        )
      })
      totalExercises = sectionExercises.size
    } yield SectionExercises(
      libraryName = libraryName,
      sectionName = sectionName,
      exercises = exercises,
      totalExercises = totalExercises
    )
  }
}

/** Default implicit based DI factory from which instances of the UserOps may be obtained
  */
object UserProgressOps {

  implicit def instance[F[_]](
    implicit
    I:  Inject[UserProgressOp, F],
    EO: ExerciseOps[F],
    FM: Monad[Free[F, ?]]
  ): UserProgressOps[F] = new UserProgressOps[F]

}
