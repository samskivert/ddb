//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.HashMap
import ddb.util.*

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

  fun <T:Any, C:MutableCollection<T>> get (buf :ByteBuffer, count :Int, into :C) :C {
    while (into.size < count) serializer<T>(
      buf.getShort()).get(this, buf, buf.getShort().toInt(), into)
    return into
  }
  fun <T:Any> put (buf :ByteBuffer, from :Collection<T>) {
    val count = from.size ; buf.putInt(count)
    put(buf, count, from)
  }
  fun <T:Any> put (buf :ByteBuffer, count :Int, from :Iterable<T>) {
    if (count > 0) putSegs(buf, from.iterator(), from.first().javaClass, 0, from.iterator())
  }

  /** Returns the id assigned to the component for `type`. */
  fun id (type :Class<*>) :Short =
    requireNotNull(_byType[type]) { "Not a protocol class: $type" }.id

  /** Returns the class associated with `id`th component. */
  fun type (id :Short) :Class<*> = try {
    _byId[id.toInt()]!!.type
  } catch (e :ArrayIndexOutOfBoundsException) {
    throw IllegalArgumentException("No protocol component for id $id")
  }

  /** Returns the serializer for value of `type`. */
  fun <T> serializer (type :Class<T>) :DSerializer<T> {
    var szer = _byType[type]
    if (szer == null) {
      // we lazily fill in serializers for anything assignable to List or Map because there could be
      // any number of such implementations and we need to cope with them
      if (List::class.java.isAssignableFrom(type)) {
        szer = _byType[List::class.java]
        _byType[type] = szer!!
      }
      else if (Map::class.java.isAssignableFrom(type)) {
        szer = _byType[Map::class.java]
        _byType[type] = szer!!
      }
    }
    return uncheckedCast<DSerializer<T>>(requireNotNull(szer) { "No serializer for $type" })
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
    uncheckedCast<DService.Factory<S>>(requireNotNull(_byType[type]) {
      "No DService factory registered for $type" })

  protected fun register (comp :Component) {
    val id = _nextId++
    _byId[id] = comp
    _byType[comp.type] = comp
    comp.init(id.toShort(), this)
  }

  // this writes <class code> <count> <elem ...> for consecutive runs of same-typed elements
  private tailrec fun <T> putSegs (buf :ByteBuffer, citer :Iterator<T>, clazz :Class<T>,
                                   count :Int, iter :Iterator<T>) {
    if (!citer.hasNext()) {
      if (count > 0) putSeg(buf, clazz, count, iter)
    } else {
      val nclazz = uncheckedCast<Class<T>>((citer.next() as Any).javaClass)
      if (nclazz == clazz) putSegs(buf, citer, clazz, count+1, iter)
      else {
        if (count > 0) putSeg(buf, clazz, count, iter)
        putSegs(buf, citer, nclazz, 1, iter)
      }
    }
  }
  private fun <T> putSeg (buf :ByteBuffer, clazz :Class<T>, count :Int, iter :Iterator<T>) {
    val szer = serializer(clazz)
    buf.putShort(szer.id)
    buf.putShort(count.toShort())
    szer.put(this, buf, count, iter)
  }
}
