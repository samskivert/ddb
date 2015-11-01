//
// DDB - for great syncing of data between server and clients

package ddb

import kotlin.reflect.KProperty

class DSignal<T> : DReactor() {

  fun <T> onEmit (fn :(T) -> Unit) :DConnection {
    return addCons(object : Cons(this) {
      @Suppress("UNCHECKED_CAST")
      override fun notify (p : KProperty<*>, a1: Any, a2: Any) :Unit = fn(a1 as T)
    })
  }

  fun emit (value :T) {
    val av = value as Any
    notify(UnusedProp, av, av)
  }

  companion object {
    // we need to supply some KProperty when notifying our listeners, but signals don't
    // actually use their properties, so we just use this placeholder
    val UnusedProp = Cons::next
  }
}
