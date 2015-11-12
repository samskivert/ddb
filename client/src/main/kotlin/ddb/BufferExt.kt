//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap

fun ByteBuffer.getBoolean () :Boolean = get() > 0
fun ByteBuffer.putBoolean (v :Boolean) { put(if (v) 1.toByte() else 0.toByte()) }

fun ByteBuffer.getString () :String = String(getByteArray(), StandardCharsets.UTF_8)
fun ByteBuffer.putString (str :String) { putByteArray(str.toByteArray(StandardCharsets.UTF_8)) }

fun <T : DData> ByteBuffer.getValue (pcol :DProtocol, clazz :Class<T>) :T {
  return pcol.serializer(clazz).get(pcol, this)
}
fun <T : DData> ByteBuffer.putValue (pcol :DProtocol, clazz :Class<T>, value :T) {
  pcol.serializer(clazz).put(pcol, this, value)
}

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
  return array as Array<String>
}
fun ByteBuffer.putStringArray (vals :Array<String>) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putString(vals[ii++])
}

fun <T> ByteBuffer.getList (pcol :DProtocol, clazz :Class<T>) :List<T> {
  val szer = pcol.serializer(clazz)
  val length = getInt() ; val list = ArrayList<T>(length)
  var ii = 0 ; while (ii++ < length) list.add(szer.get(pcol, this))
  return list
}
fun <T> ByteBuffer.putList (pcol :DProtocol, clazz :Class<T>, list :List<T>) {
  val szer = pcol.serializer(clazz) ; val length = list.size
  putInt(length)
  var ii = 0 ; while (ii < length) szer.put(pcol, this, list[ii++])
}

fun <K,V> ByteBuffer.getMap (pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>) :Map<K,V> {
  val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz)
  val size = getInt() ; val map = HashMap<K,V>(size)
  var ii = 0 ; while (ii++ < size) map.put(kszer.get(pcol, this), vszer.get(pcol, this))
  return map
}
fun <K,V> ByteBuffer.putMap (pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>, map :Map<K,V>) {
  val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz) ; val size = map.size
  putInt(size)
  for ((k, v) in map.entries) { kszer.put(pcol, this, k) ; vszer.put(pcol, this, v) }
}
