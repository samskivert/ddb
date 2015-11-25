//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.HashMap
import ddb.util.*

abstract class DSerializer<T> (type :Class<T>) : DProtocol.Component(type) {

  abstract fun get (pcol :DProtocol, buf :ByteBuffer) :T
  abstract fun put (pcol :DProtocol, buf :ByteBuffer, obj :T) :Unit

  fun get (pcol :DProtocol, buf :ByteBuffer, count :Int, into :MutableCollection<T>) {
    var ii = 0 ; while (ii++ < count) into.add(get(pcol, buf))
  }
  fun put (pcol :DProtocol, buf :ByteBuffer, count :Int, from :Iterable<T>) {
    put(pcol, buf, count, from.iterator())
  }
  fun put (pcol :DProtocol, buf :ByteBuffer, count :Int, iter :Iterator<T>) {
    var ii = 0 ; while (ii++ < count) put(pcol, buf, iter.next())
  }
}

abstract class DEntitySerializer<T : DEntity> (type :Class<T>) : DSerializer<T>(type) {

  open fun create (id :Long) :T = throw UnsupportedOperationException("Can't create abstract $type")
  open val parent :Class<out DEntity>?
    get () = null
  abstract val props :List<DEntity.Meta.Prop<*>>

  fun apply (ent :T, propId :Short, value :Any) {
    uncheckedCast<DEntity.Meta.Prop<Any>>(_allProps[propId.toInt()]).kprop.set(ent, value)
  }

  override fun get (pcol :DProtocol, buf :ByteBuffer) :T {
    val obj = create(buf.getLong())
    for (prop in _allProps) prop.read(pcol, buf, obj)
    return obj
  }
  override fun put (pcol :DProtocol, buf :ByteBuffer, obj :T) {
    buf.putLong(obj.id)
    for (prop in _allProps) prop.write(pcol, buf, obj)
  }

  override internal fun init (id :Short, pcol :DProtocol) {
    super.init(id, pcol)
    parent.ifExists { ptype -> _allProps.addAll(pcol.entitySerializer(ptype)._allProps) }
    _allProps.addAll(props)
    var pid = 0 ; for (prop in _allProps) prop.id = (pid++).toShort()
  }

  private val _allProps = ArrayList<DEntity.Meta.Prop<*>>()
}

object DSerializers {

  val VoidS = object : DSerializer<Any>(Any::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Any = throw AssertionError()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Any) {}
  }

  val BooleanS = object : DSerializer<Boolean>(Boolean::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Boolean = buf.getBoolean()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Boolean) { buf.putBoolean(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JBooleanS = object : DSerializer<java.lang.Boolean>(java.lang.Boolean::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Boolean =
      buf.getBoolean() as java.lang.Boolean
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Boolean) {
      buf.putBoolean(obj.booleanValue()) }
  }

  val ByteS = object : DSerializer<Byte>(Byte::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Byte = buf.get()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Byte) { buf.put(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JByteS = object : DSerializer<java.lang.Byte>(java.lang.Byte::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Byte =
      buf.get() as java.lang.Byte
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Byte) {
      buf.put(obj.toByte()) }
  }

  val CharS = object : DSerializer<Char>(Char::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Char = buf.getChar()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Char) { buf.putChar(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JCharS = object : DSerializer<java.lang.Character>(java.lang.Character::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Character =
      buf.getChar() as java.lang.Character
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Character) {
      buf.putChar(obj.charValue()) }
  }

  val ShortS = object : DSerializer<Short>(Short::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Short = buf.getShort()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Short) { buf.putShort(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JShortS = object : DSerializer<java.lang.Short>(java.lang.Short::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Short =
      buf.getShort() as java.lang.Short
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Short) {
      buf.putShort(obj.toShort()) }
  }

  val IntS = object : DSerializer<Int>(Int::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Int = buf.getInt()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Int) { buf.putInt(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JIntS = object : DSerializer<java.lang.Integer>(java.lang.Integer::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Integer =
      buf.getInt() as java.lang.Integer
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Integer) {
      buf.putInt(obj.toInt()) }
  }

  val LongS = object : DSerializer<Long>(Long::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Long = buf.getLong()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Long) { buf.putLong(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JLongS = object : DSerializer<java.lang.Long>(java.lang.Long::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Long =
      buf.getLong() as java.lang.Long
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Long) {
      buf.putLong(obj.toLong()) }
  }

  val FloatS = object : DSerializer<Float>(Float::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Float = buf.getFloat()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Float) { buf.putFloat(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JFloatS = object : DSerializer<java.lang.Float>(java.lang.Float::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Float =
      buf.getFloat() as java.lang.Float
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Float) {
      buf.putFloat(obj.toFloat()) }
  }

  val DoubleS = object : DSerializer<Double>(Double::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Double = buf.getDouble()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Double) { buf.putDouble(obj) }
  }
  @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
  val JDoubleS = object : DSerializer<java.lang.Double>(java.lang.Double::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :java.lang.Double =
      buf.getDouble() as java.lang.Double
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :java.lang.Double) {
      buf.putDouble(obj.toDouble()) }
  }

  val StringS = object : DSerializer<String>(String::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :String = buf.getString()
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :String) { buf.putString(obj) }
  }

  val ClassS = object : DSerializer<Class<*>>(Class::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :Class<*> = pcol.type(buf.getShort())
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :Class<*>) {
      buf.putShort(pcol.id(obj)) }
  }

  val ArrayListS = object : DSerializer<ArrayList<*>>(ArrayList::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :ArrayList<*> {
      val length = buf.getInt()
      return pcol.get(buf, length, ArrayList<Any>(length))
    }
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ArrayList<*>) { pcol.put(buf, obj) }
  }

  val HashMapS = object : DSerializer<HashMap<*,*>>(HashMap::class.java) {
    override fun get (pcol :DProtocol, buf :ByteBuffer) :HashMap<*,*> {
      val size = buf.getInt()
      val keys = pcol.get(buf, size, ArrayList<Any>(size))
      val values = pcol.get(buf, size, ArrayList<Any>(size))
      val map = HashMap<Any,Any>(size)
      var ii = 0 ; while (ii++ < size) map.put(keys[ii], values[ii])
      return map
    }
    override fun put (pcol :DProtocol, buf :ByteBuffer, obj :HashMap<*,*>) {
      val size = obj.size
      val keys = ArrayList<Any>(size)
      val values = ArrayList<Any>(size)
      for ((k, v) in obj.entries) { keys += k ; values += v }
      buf.putInt(size)
      pcol.put(buf, size, keys)
      pcol.put(buf, size, values)
    }
  }

  // TODO: do we want other concrete list/map types?

  val Defaults = arrayOf(VoidS, BooleanS, JBooleanS, ByteS, JByteS, CharS, JCharS, ShortS, JShortS,
                         IntS, JIntS, LongS, JLongS, FloatS, JFloatS, DoubleS, JDoubleS, StringS,
                         ClassS, ArrayListS, HashMapS)
}
