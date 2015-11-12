//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer

abstract class DSerializer<T> (val type :Class<T>) {

  abstract fun get (pcol :DProtocol, buf :ByteBuffer) :T
  abstract fun put (pcol :DProtocol, buf :ByteBuffer, obj :T) :Unit

  val id :Short
    get () = _id

  internal fun init (id :Short) { _id = id }
  private var _id = 0.toShort()
}

abstract class DEntitySerializer<T> (type :Class<T>) : DSerializer<T>(type) {

  override fun get (pcol :DProtocol, buf :ByteBuffer) :T {
    val obj = create(buf) ; read(pcol, buf, obj) ; return obj
  }

  abstract fun create (buf :ByteBuffer) :T
  abstract fun read (pcol :DProtocol, buf :ByteBuffer, obj :T) :Unit
}

object DSerializers {

  val Void = object : DSerializer<Any>(Any::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Any = throw AssertionError()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Any) {}
  }

  val Boolean = object : DSerializer<Boolean>(Boolean::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Boolean = buf.getBoolean()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Boolean) { buf.putBoolean(obj) }
  }

  val Byte = object : DSerializer<Byte>(Byte::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Byte = buf.get()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Byte) { buf.put(obj) }
  }

  val Char = object : DSerializer<Char>(Char::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Char = buf.getChar()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Char) { buf.putChar(obj) }
  }

  val Short = object : DSerializer<Short>(Short::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Short = buf.getShort()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Short) { buf.putShort(obj) }
  }

  val Int = object : DSerializer<Int>(Int::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Int = buf.getInt()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Int) { buf.putInt(obj) }
  }

  val Long = object : DSerializer<Long>(Long::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Long = buf.getLong()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Long) { buf.putLong(obj) }
  }

  val Float = object : DSerializer<Float>(Float::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Float = buf.getFloat()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Float) { buf.putFloat(obj) }
  }

  val Double = object : DSerializer<Double>(Double::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Double = buf.getDouble()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Double) { buf.putDouble(obj) }
  }

  val String = object : DSerializer<String>(String::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :String = buf.getString()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :String) { buf.putString(obj) }
  }

  val Defaults = arrayOf(Void, Boolean, Byte, Char, Short, Int, Long, Float, Double, String)
}
