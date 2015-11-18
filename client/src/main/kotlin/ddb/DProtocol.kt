//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.HashMap

abstract class DProtocol (compCount :Int) {

  /** A base for classes that compal either data or service requests over the wire. There is a one
    * to one correspondence between a compaller and a concrete type (either data, [DEntity]
    * subtype, or [DService] subtype. */
  abstract class Component (val type :Class<*>) {
    /** Returns the unique id assigned to this compaller. */
    val id :Short
      get () = _id

    internal open fun init (id :Short, pcol :DProtocol) { _id = id }
    private var _id = 0.toShort()
  }

  private var _nextId = 0
  private val _byId = arrayOfNulls<Component>(DSerializers.Defaults.size+compCount)
  private val _byType = HashMap<Class<*>,Component>(_byId.size)
  init { for (szer in DSerializers.Defaults) register(szer) }

  /** Writes an arbitrary value to `buf`, preceded by its type id. */
  fun put (buf :ByteBuffer, value :Any) {
    val szer = serializer(value.javaClass)
    buf.putShort(szer.id)
    szer.put(this, buf, value)
  }

  /** Reads a type id from `buf` followed by a value of that type. */
  fun get (buf :ByteBuffer) :Any = serializer<Any>(buf.getShort()).get(this, buf)

  /** Returns the serializer for value of `type`. */
  fun <T> serializer (type :Class<T>) :DSerializer<T> {
    val szer = _byType[type]
    if (szer == null) throw IllegalArgumentException("No serializer for $type")
    return uncheckedCast<DSerializer<T>>(szer)
  }

  /** Returns the serializer for entity of `type`. */
  fun <T : DEntity> entitySerializer (type :Class<T>) :DEntitySerializer<T> =
    uncheckedCast<DEntitySerializer<T>>(serializer(type))

  fun <T> serializer (id :Short) :DSerializer<T> = try {
    uncheckedCast<DSerializer<T>>(_byId[id.toInt()]!!)
  } catch (e :ArrayIndexOutOfBoundsException) {
    throw IllegalArgumentException("No serializer for id $id")
  }

  /** Returns the [DService.Factory] for `type`. */
  fun <S : DService> factory (type :Class<S>) :DService.Factory<S> =
    uncheckedCast<DService.Factory<S>>(_byType[type]!!)

  protected fun register (comp :Component) {
    val id = _nextId++
    _byId[id] = comp
    _byType[comp.type] = comp
    comp.init(id.toShort(), this)
  }
}
