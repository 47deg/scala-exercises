package stdlib

/** Section Foo
  *
  * This is a Section
  */
object FooSection extends exercise.Section {

  /** Exercise foo 1 */
  def foo1(value: String) {
    println(s"foo 1: $value")
  }

  /** Exercise foo 2 */
  def foo2(value: String) {
    println(s"foo 2: $value")
    FooBarHelper.help()
  }

}
