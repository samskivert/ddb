//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.HashMap

abstract class DProtocol (szerCount :Int) {

  private var _nextId = 0
  private val _byId = arrayOfNulls<DSerializer<*>>(DSerializers.Defaults.size+szerCount)
  private val _byType = HashMap<Class<*>,DSerializer<*>>(_byId.size)
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

  /** Creates a dispatcher for [DService] `type`. */
  abstract fun <S : DService> dispatcher (type :Class<S>, impl :S) :DService.Dispatcher

  protected fun register (szer :DSerializer<*>) {
    szer.init(_nextId.toShort())
    _byId[_nextId++] = szer
    _byType[szer.type] = szer
  }
}
