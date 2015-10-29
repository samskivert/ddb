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
      override fun notify (a1: Any, a2: Any, a3: Any) {
        if ((a1 as KProperty<*>).name == prop.name) fn(a2 as T)
      }
    })
  }

  fun <T> onChange (prop :KProperty<T>, fn :(T, T) -> Unit) :DConnection =
    addCons(object : Cons(this) {
      @Suppress("UNCHECKED_CAST")
      override fun notify (a1 :Any, a2 :Any, a3 :Any) {
        if ((a1 as KProperty<*>).name == prop.name) fn(a2 as T, a3 as T)
      }
    })

  /** Returns a reference to this entity's companion singleton. */
  abstract fun companion () :DCompanion<DEntity>

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
      println("Emitting change $prop $oldval -> $newval")
    notify(prop, newval as Any, oldval as Any)
  }
}
