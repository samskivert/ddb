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

fun ByteBuffer.getAny (pcol :DProtocol) :Any = pcol.get(this)
fun ByteBuffer.putAny (pcol :DProtocol, value :Any) :Unit = pcol.put(this, value)

private fun isFinal (clazz :Class<*>) = Modifier.isFinal(clazz.getModifiers())

fun <T : DData> ByteBuffer.getValue (pcol :DProtocol, clazz :Class<T>) :T {
  return if (isFinal(clazz)) pcol.serializer(clazz).get(pcol, this)
  else pcol.serializer<T>(getShort()).get(pcol, this)
}
fun <T : DData> ByteBuffer.putValue (pcol :DProtocol, clazz :Class<T>, value :T) {
  val szer = pcol.serializer(clazz)
  if (!isFinal(clazz)) putShort(szer.id)
  szer.put(pcol, this, value)
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
  return uncheckedCast<Array<String>>(array)
}
fun ByteBuffer.putStringArray (vals :Array<String>) {
  putInt(vals.size)
  var ii = 0 ; while (ii < vals.size) putString(vals[ii++])
}

fun <T> ByteBuffer.getList (pcol :DProtocol, clazz :Class<T>) :List<T> {
  val length = getInt() ; val list = ArrayList<T>(length)
  if (isFinal(clazz)) pcol.serializer(clazz).get(pcol, this, length, list)
  else while (list.size < length) pcol.serializer<T>(
    getShort()).get(pcol, this, getShort().toInt(), list)
  return list
}

fun <T> ByteBuffer.putList (pcol :DProtocol, clazz :Class<T>, list :List<T>) {
  val length = list.size ; putInt(length)
  if (!isFinal(clazz)) putSegs(pcol, list.iterator(), clazz, 0, list.iterator())
  else {
    // pointless micro-optimization to avoid calling list.iterator(); why do I do these things?
    val szer = pcol.serializer(clazz)
    var ii = 0 ; while (ii < length) szer.put(pcol, this, list[ii++])
  }
}

fun <T> ByteBuffer.getCollection (pcol :DProtocol, clazz :Class<T>) :Collection<T> =
  getList(pcol, clazz)
fun <T> ByteBuffer.putCollection (pcol :DProtocol, clazz :Class<T>, elems :Collection<T>) {
  val length = elems.size ; putInt(length)
  if (isFinal(clazz)) pcol.serializer(clazz).put(pcol, this, length, elems.iterator())
  else putSegs(pcol, elems.iterator(), clazz, 0, elems.iterator())
}

// this writes <class code> <count> <elem ...> for consecutive runs of same-typed elements
private tailrec fun <T> ByteBuffer.putSegs (pcol :DProtocol, citer :Iterator<T>, clazz :Class<T>,
                                            count :Int, iter :Iterator<T>) {
  if (!citer.hasNext()) {
    if (count > 0) putSeg(pcol, clazz, count, iter)
  } else {
    val nclazz = uncheckedCast<Class<T>>((citer.next() as Any).javaClass)
    if (nclazz == clazz) putSegs(pcol, citer, clazz, count+1, iter)
    else {
      if (count > 0) putSeg(pcol, clazz, count, iter)
      putSegs(pcol, citer, nclazz, 1, iter)
    }
  }
}
private fun <T> ByteBuffer.putSeg (pcol :DProtocol, clazz :Class<T>,
                                   count :Int, iter :Iterator<T>) {
  val szer = pcol.serializer(clazz)
  putShort(szer.id)
  putShort(count.toShort())
  szer.put(pcol, this, count, iter)
}

fun <K,V> ByteBuffer.getMap (pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>) :Map<K,V> {
  val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz)
  val size = getInt() ; val map = HashMap<K,V>(size)
  var ii = 0 ; while (ii++ < size) map.put(kszer.get(pcol, this), vszer.get(pcol, this))
  return map
}
fun <K,V> ByteBuffer.putMap (pcol :DProtocol, kclazz :Class<K>, vclazz :Class<V>, map :Map<K,V>) {
  val size = map.size ; putInt(size)
  val kfinal = isFinal(kclazz) ; val vfinal = isFinal(vclazz)
  if (kfinal && vfinal) {
    val kszer = pcol.serializer(kclazz) ; val vszer = pcol.serializer(vclazz)
    for ((k, v) in map.entries) { kszer.put(pcol, this, k) ; vszer.put(pcol, this, v) }
  } else if (kfinal) {
    val kszer = pcol.serializer(kclazz)
    for ((k, v) in map.entries) { kszer.put(pcol, this, k) ; pcol.put(this, v as Any) }
  } else if (vfinal) {
    val vszer = pcol.serializer(vclazz)
    for ((k, v) in map.entries) { pcol.put(this, k as Any) ; vszer.put(pcol, this, v) }
  }
}
