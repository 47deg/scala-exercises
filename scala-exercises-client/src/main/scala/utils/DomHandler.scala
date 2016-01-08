package utils

import org.scalajs.dom
import org.scalajs.dom.raw.{ HTMLDivElement, HTMLElement, HTMLInputElement }
import org.scalajs.jquery.{ jQuery ⇒ $, JQuery }

object DomHandler {

  def insertInputs = for {
    code ← getCodeBlocks
    text = getTextInCode(code)
    replaced = replaceInputByRes(text)
  } yield $(code).html(replaced)

  def activeInputs(
    onkeyup: (String, Seq[String]) ⇒ Unit,
    onblur: String ⇒ Unit) = allInputs.map(input ⇒ {
    setInputWidth(input)
    $(input)
      .keyup((e: dom.Event) ⇒ {
        setInputWidth(input)
        for {
          methodName ← methodParent(input)
          exercise ← findExerciseByMethod(methodName)
          inputsValues = getInputsValues(exercise)
        } yield onkeyup(methodName, inputsValues)
      })
      .blur((e: dom.Event) ⇒
        for {
          methodName ← methodParent(input)
        } yield onblur(methodName)
      )
  })

  val resAssert = """(?s)\((res[0-9]*)\)""".r

  def allExercises: Seq[HTMLElement] = $(".exercise").divs filter isMethodDefined

  def getMethodAttr(e: HTMLElement): String = $(e).attr("data-method").trim

  def isMethodDefined(e: HTMLElement): Boolean = getMethodAttr(e).nonEmpty

  def getMethodsList: Seq[String] = allExercises map getMethodAttr

  def methodName(e: HTMLElement): Option[String] = Option(getMethodAttr(e)) filter (_.nonEmpty)

  def methodParent(input: HTMLInputElement): Option[String] = methodName($(input).closest(".exercise").getDiv)

  def allInputs: Seq[HTMLInputElement] = $(".exercise-code>input").inputs

  def findExerciseByMethod(method: String): Option[HTMLElement] = allExercises.find(methodName(_) == Option(method))

  def getInputsValues(exercise: HTMLElement): Seq[String] = inputsInExercise(exercise).map(_.value)

  def inputsInExercise(exercise: HTMLElement): Seq[HTMLInputElement] = $(exercise).find("input").inputs

  def getCodeBlocks: Seq[HTMLElement] = $("pre code").elements

  def getTextInCode(code: HTMLElement): String = $(code).text

  def replaceInputByRes(text: String): String = resAssert.replaceAllIn(text, """(<input type="text" data-res="$1"/>)""")

  def getInputLength(input: HTMLInputElement): Int = $(input).value.toString.length

  def setInputWidth(input: HTMLInputElement) = $(input).width(inputSize(getInputLength(input)))

  def inputSize(length: Int) = length match {
    case 0 ⇒ 12d
    case _ ⇒ (12 + (length + 1) * 7).toDouble
  }

  implicit class JQueryOps(j: JQuery) {

    def elements: Seq[HTMLElement] = all[HTMLElement]

    def divs: Seq[HTMLDivElement] = all[HTMLDivElement]

    def inputs: Seq[HTMLInputElement] = all[HTMLInputElement]

    def getDiv: HTMLDivElement = get[HTMLDivElement]

    def all[A <: dom.Element]: Seq[A] = j.toArray().collect { case d: A ⇒ d }

    def get[A <: dom.Element]: A = j.get().asInstanceOf[A]

  }

}
