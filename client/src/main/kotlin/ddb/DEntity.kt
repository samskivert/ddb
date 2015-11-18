//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import react.*

/** An entity that is distributed between client and server.
  *
  * TODO: more details.
  */
abstract class DEntity (val id :Long) : DReactor() {

  /** Allows an entity to communicate with the [DDB] that is hosting it. */
  interface Host {
    /** Notifies our host that this entity has changed. */
    fun onChange (entity :DEntity, propId :Short, value :Any) :Unit
  }

  /** Identifies entity types. */
  interface Meta <out E : DEntity> {
    val entityName :String
    fun create (id :Long) :E
  }

  /** Returns a reference to this entity's meta singleton. */
  abstract val meta :Meta<DEntity>

  /** Registers `fn` to be called when `prop` changes. */
  fun <T> onEmit (prop :KProperty<T>, fn :(T) -> Unit) :Connection {
    return addCons(object : Cons(this) {
      override fun notify (p :KProperty<*>, a1: Any, a2: Any) {
        if (areEqual(p, prop)) fn(uncheckedCast<T>(a1))
      }
    })
  }

  /** Registers `fn` to be called when `prop` changes. */
  fun <T> onChange (prop :KProperty<T>, fn :(T, T) -> Unit) :Connection =
    addCons(object : Cons(this) {
      override fun notify (p :KProperty<*>, a1 :Any, a2 :Any) {
        if (areEqual(p, prop)) fn(uncheckedCast<T>(a1), uncheckedCast<T>(a2))
      }
    })

  /** Returns a [ValueView] for `prop`, which exports `prop` as a reactive value. */
  fun <T> view (prop :KProperty<T>) :ValueView<T> = object : AbstractValue<T>() {
    override fun get () = uncheckedCast<KProperty1<DEntity,T>>(prop).get(this@DEntity)
    @Suppress("NO_REFLECTION_IN_CLASS_PATH")
    override fun toString () = prop.toString()

    override protected fun connectionAdded () {
      super.connectionAdded()
      if (_conn == Closeable.Util.NOOP) {
        _conn = onChange(prop) { nv, ov -> notifyChange(nv, ov) }
      }
    }
    override protected fun connectionRemoved () {
      super.connectionRemoved()
      if (!hasConnections()) _conn = Closeable.Util.close(_conn)
    }
    private var _conn = Closeable.Util.NOOP
  }

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
  protected fun <T> dvalue (initVal :T) : DValue<T> = DValue(initVal)

  // implementation details, please to ignore
  fun _init (host :Host, szer :DEntitySerializer<*>) {
    _host = host
    _szer = uncheckedCast<DEntitySerializer<DEntity>>(szer)
  }
  internal fun apply (change :DMessage.PropChange) {
    _szer.apply(this, change.propId, change.value)
  }

  private fun <T> emitChange (prop :KProperty<*>, oldval :T, newval :T) {
    _host.onChange(this, _szer.id(prop.name), newval as Any)
    notify(prop, newval, oldval as Any)
  }

  private fun areEqual (prop0 :KProperty<*>, prop1 :KProperty<*>) :Boolean =
    prop0.name == prop1.name // TODO

  private var _host = NoopHost
  private var _szer = NoopSzer

  companion object {
    val NoopHost = object : Host {
      override fun onChange (entity :DEntity, propId :Short, value :Any) {}
    }
    val NoopSzer = object : DEntitySerializer<DEntity>(DEntity::class.java) {
      override fun create (buf :ByteBuffer) :DEntity = throw AssertionError()
      override fun read (pcol :DProtocol, buf :ByteBuffer, obj :DEntity) {}
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :DEntity) {}
      override fun id (propName :String) = 0.toShort()
      override fun apply (ent :DEntity, propId :Short, value :Any) {}
    }
  }
}
