//
// DDB - for great syncing of data between server and clients

package ddb

import kotlin.reflect.KProperty

/** An entity distributed between client and server.
  *
  * TODO: more details.
  */
abstract class DEntity (val id :Long) : DReactor() {

  fun <T> onEmit (prop :KProperty<T>, fn :(T) -> Unit) :DConnection {
    return addCons(object : Cons(this) {
      @Suppress("UNCHECKED_CAST")
      override fun notify (p :KProperty<*>, a1: Any, a2: Any) {
        if (areEqual(p, prop)) fn(a1 as T)
      }
    })
  }

  fun <T> onChange (prop :KProperty<T>, fn :(T, T) -> Unit) :DConnection =
    addCons(object : Cons(this) {
      @Suppress("UNCHECKED_CAST")
      override fun notify (p :KProperty<*>, a1 :Any, a2 :Any) {
        if (areEqual(p, prop)) fn(a1 as T, a2 as T)
      }
    })

  /** Returns a reference to this entity's companion singleton. */
  abstract val companion :DCompanion<DEntity>

  inner class DValue<T> (initVal :T) {
    private var _curval = initVal
    operator fun getValue (thisRef :Any?, property : KProperty<*>) :T = _curval
    operator fun setValue (thisRef :Any?, property :KProperty<*>, newval :T) {
      val oldval = _curval
      _curval = newval
      emitChange(property, oldval, newval)
    }
  }

  /** Defines a reactive component of this entity. */
  protected fun <T> dvalue (schemaVers :Int, initVal :T) : DValue<T> {
    // TODO: registering with schema (using version), etc.
    return DValue(initVal)
  }

  /** Reports to listeners when a property has changed. */
  private fun <T> emitChange (prop :KProperty<*>, oldval :T, newval :T) {
    notify(prop, newval as Any, oldval as Any)
  }

  private fun areEqual (prop0 :KProperty<*>, prop1 :KProperty<*>) :Boolean =
    prop0.name == prop1.name // TODO
}
