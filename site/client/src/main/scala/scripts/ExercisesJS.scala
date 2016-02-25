package scripts

import rx._
import utils.DomHandler._
import scala.scalajs.js
import scala.concurrent.ExecutionContext.Implicits.global

import shared.IO
import IO._

import model._
import model.Exercises._
import actions._
import ui.UI
import state.State
import effects.Effects

object ExercisesJS extends js.JSApp {

  def loadInitialData: IO[State] = for {
    library ← getLibrary
    section ← getSection
    methods ← getMethodsList
  } yield methods.map(m ⇒ ClientExercise(library = library, section = section, method = m))

    def updateExerciseList(ex: List[ClientExercise]): IO[Unit] = io(exercises() = ex)

    case class ClientExercise(method: String, arguments: Seq[String] = Nil) {
      def isFilled: Boolean = !arguments.exists(_.isEmpty) && arguments.nonEmpty
    }

    def triggerAction(action: Action): IO[Unit] = {
      actions() = action
      val oldState = states()
      val newState = State.update(oldState, action)
      setState(newState)
    }

    val effects = Obs(states, skipInitial = true) {
      Effects.perform(states(), actions()).foreach(m ⇒ {
        m.foreach(triggerAction(_).unsafePerformIO())
      })
    }
    val ui = Obs(states, skipInitial = true) {
      UI.update(states(), actions()).unsafePerformIO()
    }

    val program = for {
      _ ← loadInitialData flatMap setState
      replacements ← inputReplacements
      _ ← replaceInputs(replacements)
      _ ← highlightCodeBlocks
      _ ← onInputKeyUp((method: String, arguments: Seq[String]) ⇒ {
        triggerAction(UpdateExercise(method, arguments))
      }, (method: String) ⇒ {
        triggerAction(CompileExercise(method))
      })
      _ ← onButtonClick((method: String) ⇒ {
        triggerAction(CompileExercise(method))
      })
      _ ← onInputBlur((method: String) ⇒ io {
        // TODO: when exercise canbecompiled ~> Compileit
      })
    } yield ()

    program.unsafePerformIO()

  }
