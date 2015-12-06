//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty
import react.*
import ddb.util.*

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

  /** The base class for a [DEntity] companion class. */
  abstract class Meta <out E : DEntity> {
    /** Returns a simple (unique) string name for entities of this type. */
    abstract val entityName :String

    /** Creates an uninitialized entity instance of this type. */
    abstract fun create (id :Long) :E

    /** Returns a delegate for a [DEntity] property of simple type. */
    inline fun <reified T : Any> prop (kprop :KMutableProperty1<*,T>) =
      ValueProp<T>(kprop, T::class.java)

    /** Returns a delegate for a [DEntity] property of list type. */
    inline fun <reified T : Any> listProp (kprop :KMutableProperty1<*,List<T>>) =
      ListProp<T>(kprop, T::class.java)

    /** Returns a delegate for a [DEntity] property of list type. */
    inline fun <reified K : Any, reified V : Any> mapProp (kprop :KMutableProperty1<*,Map<K,V>>) =
      MapProp<K,V>(kprop, K::class.java, V::class.java)

    /** Metadata for a [DEntity] property. Handles change notifications and serialization. */
    abstract class Prop<T> (rawProp :KMutableProperty1<*,T>) {
      val kprop = uncheckedCast<KMutableProperty1<DEntity,T>>(rawProp)
      var id :Short = 0.toShort() // assigned during DSerializer init

      class Delegate<T> (val prop :Prop<T>, initVal :T) {
        private var _curval = initVal

        operator fun getValue (thisRef :Any?, property :KProperty<*>) :T = _curval
        operator fun setValue (thisRef :Any?, property :KProperty<*>, newval :T) {
          val oldval = _curval
          _curval = newval
          (thisRef!! as DEntity).emitChange(prop, oldval, newval)
        }
      }
      operator fun invoke (initVal :T) = Delegate<T>(this, initVal)

      abstract fun read (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) :Unit
      abstract fun write (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) :Unit

      override fun toString () = "${kprop.name}@$id"
    }

    /** Metadata for a simple value property. */
    class ValueProp<T:Any> (kprop :KMutableProperty1<*,T>, val vtype :Class<T>) : Prop<T>(kprop) {
      override fun read (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        kprop.set(entity, buf.getValue(pcol, vtype))
      }
      override fun write (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        buf.putValue(pcol, vtype, kprop.get(entity))
      }
    }

    /** Metadata for a list property. */
    class ListProp<T:Any> (kprop :KMutableProperty1<*,List<T>>,
                           val etype :Class<T>) : Prop<List<T>>(kprop) {

      override fun read (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        kprop.set(entity, buf.getList(pcol, etype))
      }
      override fun write (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        buf.putList(pcol, etype, kprop.get(entity))
      }
    }

    /** Metadata for a map property. */
    class MapProp<K:Any,V:Any> (kprop :KMutableProperty1<*,Map<K,V>>,
                                val ktype :Class<K>, val vtype :Class<V>) : Prop<Map<K,V>>(kprop) {

      override fun read (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        kprop.set(entity, buf.getMap(pcol, ktype, vtype))
      }
      override fun write (pcol :DProtocol, buf :ByteBuffer, entity :DEntity) {
        buf.putMap(pcol, ktype, vtype, kprop.get(entity))
      }
    }
  }

  /** Returns a reference to this entity's meta singleton. */
  abstract val meta :Meta<DEntity>

  /** Returns the list of all properties in this entity (including supertypes). */
  val props :List<Meta.Prop<*>>
    get () = _szer.allProps

  /** Registers `fn` to be called when `prop` changes. */
  fun <T> onEmit (prop :Meta.Prop<T>, fn :(T) -> Unit) :Connection {
    return addCons(object : Cons(this) {
      override fun notify (key :Any, a1: Any, a2: Any) {
        if (key == prop) fn(uncheckedCast<T>(a1))
      }
    })
  }

  /** Registers `fn` to be called when `prop` changes. */
  fun <T> onChange (prop :Meta.Prop<T>, fn :(T, T) -> Unit) :Connection =
    addCons(object : Cons(this) {
      override fun notify (key :Any, a1 :Any, a2 :Any) {
        if (key == prop) fn(uncheckedCast<T>(a1), uncheckedCast<T>(a2))
      }
    })

  /** Returns a [ValueView] for `prop`, which exports `prop` as a reactive value. */
  fun <T> view (prop :Meta.Prop<T>) :ValueView<T> = object : AbstractValue<T>() {
    override fun get () = prop.kprop.get(this@DEntity)
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

  override fun toString () :String {
    val sb = StringBuilder().append(meta.entityName).append("@").append(id).append("[")
    for (ii in props.indices) {
      if (ii > 0) sb.append(", ")
      val kprop = props[ii].kprop
      sb.append(kprop.name).append("=").append(kprop.get(this))
    }
    return sb.append("]").toString()
  }

  // implementation details, please to ignore
  fun _init (host :Host, szer :DEntitySerializer<*>) {
    _host = host
    _szer = uncheckedCast<DEntitySerializer<DEntity>>(szer)
  }
  internal fun apply (change :DMessage.PropChange) {
    assert(_szer != NoopSzer) { "Cannot apply $change to uninitialized entity $this" }
    try {
      _szer.apply(this, change.propId, change.value)
    } catch (err :Throwable) {
      throw RuntimeException("Failed to apply $change to $this", err)
    }
  }

  private fun <T> emitChange (prop :Meta.Prop<T>, oldval :T, newval :T) {
    _host.onChange(this, prop.id, newval as Any)
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
      override val props :List<Meta.Prop<*>> = listOf()
      override fun create (id :Long) :DEntity = throw AssertionError()
    }
  }
}
