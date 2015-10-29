//
// DDB - for great syncing of data between server and clients

package ddb

class DSignal<T> : DReactor() {

  fun <T> onEmit (fn :(T) -> Unit) :DConnection {
    return addCons(object : Cons(this) {
      @Suppress("UNCHECKED_CAST")
      override fun notify (a1: Any, a2: Any, a3: Any) :Unit = fn(a1 as T)
    })
  }

  fun emit (value :T) {
    val av = value as Any
    notify(av, av, av)
  }
}
