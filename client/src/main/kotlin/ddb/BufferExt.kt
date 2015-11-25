//
// DDB - for great syncing of data between server and clients

package ddb

import java.lang.reflect.Modifier
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap
import ddb.util.*

fun ByteBuffer.getBoolean () :Boolean = get() > 0
fun ByteBuffer.putBoolean (v :Boolean) { put(if (v) 1.toByte() else 0.toByte()) }

fun ByteBuffer.getString () :String = String(getByteArray(), StandardCharsets.UTF_8)
fun ByteBuffer.putString (str :String) { putByteArray(str.toByteArray(StandardCharsets.UTF_8)) }

private fun isFinal (clazz :Class<*>) =
  Modifier.isFinal(clazz.getModifiers()) || clazz.isEnum()

fun <T:Any> ByteBuffer.getValue (pcol :DProtocol, clazz :Class<T>) :T {
  return if (isFinal(clazz)) pcol.serializer(clazz).get(pcol, this)
  else getTagged<T>(pcol)
}
fun <T:Any> ByteBuffer.putValue (pcol :DProtocol, clazz :Class<T>, value :T) {
  if (isFinal(clazz)) pcol.serializer(clazz).put(pcol, this, value)
  else putTagged(pcol, value)
}

fun <T:Any> ByteBuffer.getTagged (pcol :DProtocol) :T {
  return pcol.serializer<T>(getShort()).get(pcol, this)
}
fun <T:Any> ByteBuffer.putTagged (pcol :DProtocol, value :T) {
  val szer = pcol.serializer(value.javaClass)
  putShort(szer.id)
  szer.put(pcol, this, value)
}

fun ByteBuffer.getAny (pcol :DProtocol) :Any = getTagged<Any>(pcol)
fun ByteBuffer.putAny (pcol :DProtocol, value :Any) = putTagged(pcol, value)

fun ByteBuffer.getBooleanArray () :BooleanArray {
  val array = BooleanArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getBoolean()
  return array
}
fun ByteBuffer.putBooleanArray (vals :BooleanArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putBoolean(vals[ii++])
}

fun ByteBuffer.getByteArray () :ByteArray {
  val array = ByteArray(getInt())
  get(array) // woo!
  return array
}
fun ByteBuffer.putByteArray (vals :ByteArray) {
  putInt(vals.size)
  put(vals) // woo!
}

fun ByteBuffer.getCharArray () :CharArray {
  val array = CharArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getChar()
  return array
}
fun ByteBuffer.putCharArray (vals :CharArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putChar(vals[ii++])
}

fun ByteBuffer.getShortArray () :ShortArray {
  val array = ShortArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getShort()
  return array
}
fun ByteBuffer.putShortArray (vals :ShortArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putShort(vals[ii++])
}

fun ByteBuffer.getIntArray () :IntArray {
  val array = IntArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getInt()
  return array
}
fun ByteBuffer.putIntArray (vals :IntArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putInt(vals[ii++])
}

fun ByteBuffer.getLongArray () :LongArray {
  val array = LongArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getLong()
  return array
}
fun ByteBuffer.putLongArray (vals :LongArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putLong(vals[ii++])
}

fun ByteBuffer.getFloatArray () :FloatArray {
  val array = FloatArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getFloat()
  return array
}
fun ByteBuffer.putFloatArray (vals :FloatArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putFloat(vals[ii++])
}

fun ByteBuffer.getDoubleArray () :DoubleArray {
  val array = DoubleArray(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getDouble()
  return array
}
fun ByteBuffer.putDoubleArray (vals :DoubleArray) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putDouble(vals[ii++])
}

fun ByteBuffer.getStringArray () :Array<String> {
  val array = arrayOfNulls<String>(getInt())
  var ii = 0 ; while (ii < array.size) array[ii++] = getString()
  return uncheckedCast<Array<String>>(array)
}
fun ByteBuffer.putStringArray (vals :Array<String>) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putString(vals[ii++])
}

fun <T:Any> ByteBuffer.getCollection (pcol :DProtocol, clazz :Class<T>) :Collection<T> =
  getList(pcol, clazz)
fun <T:Any> ByteBuffer.putCollection (pcol :DProtocol, clazz :Class<T>, elems :Collection<T>) {
  val length = elems.size ; putInt(length)
  if (isFinal(clazz)) pcol.serializer(clazz).put(pcol, this, length, elems)
  else pcol.put(this, length, elems)
}

fun <T:Any> ByteBuffer.getList (pcol :DProtocol, clazz :Class<T>) :List<T> {
  val length = getInt() ; val list = ArrayList<T>(length)
  if (isFinal(clazz)) pcol.serializer(clazz).get(pcol, this, length, list)
  else pcol.get(this, length, list)
  return list
}
fun <T:Any> ByteBuffer.putList (pcol :DProtocol, clazz :Class<T>, list :List<T>) {
  putCollection(pcol, clazz, list)
}

fun <K:Any,V:Any> ByteBuffer.getMap (
  pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>) :Map<K,V> {
  val size = getInt() ; val map = HashMap<K,V>(size)
  val kfinal = isFinal(kclazz) ; val vfinal = isFinal(vclazz)
  if (kfinal && vfinal) {
    val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz)
    var ii = 0 ; while (ii++ < size) map.put(kszer.get(pcol, this), vszer.get(pcol, this))
  } else if (kfinal) {
    val kszer = pcol.serializer(kclazz)
    var ii = 0 ; while (ii++ < size) map.put(kszer.get(pcol, this), getTagged(pcol))
  } else if (vfinal) {
    val vszer = pcol.serializer(vclazz)
    var ii = 0 ; while (ii++ < size) map.put(getTagged(pcol), vszer.get(pcol, this))
  } else {
    var ii = 0 ; while (ii++ < size) map.put(getTagged(pcol), getTagged(pcol))
  }
  return map
}
fun <K:Any,V:Any> ByteBuffer.putMap (pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>,
                                     map :Map<K,V>) {
  val size = map.size ; putInt(size)
  val kfinal = isFinal(kclazz) ; val vfinal = isFinal(vclazz)
  if (kfinal && vfinal) {
    val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz)
    for ((k, v) in map.entries) { kszer.put(pcol, this, k) ; vszer.put(pcol, this, v) }
  } else if (kfinal) {
    val kszer = pcol.serializer(kclazz)
    for ((k, v) in map.entries) { kszer.put(pcol, this, k) ; putTagged(pcol, v) }
  } else if (vfinal) {
    val vszer = pcol.serializer(vclazz)
    for ((k, v) in map.entries) { putTagged(pcol, k) ; vszer.put(pcol, this, v) }
  } else {
    for ((k, v) in map.entries) { putTagged(pcol, k) ; putTagged(pcol, v) }
  }
}
