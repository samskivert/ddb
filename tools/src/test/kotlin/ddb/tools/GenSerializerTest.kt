//
// DDB - for great syncing of data between server and clients

package ddb.tools

import java.io.StringWriter
import java.nio.ByteBuffer
import java.nio.file.Paths

import org.junit.Assert.*
import org.junit.Test

import ddb.*

class GenSerializerTest {

  enum class TestEnum : DData { FOO, BAR, BAZ }

  data class TestData (
    val intVal :Int,
    val intArrayVal :IntArray,
    val intListVal :List<Int>,
    val strVal :String,
    val strArrayVal :Array<String>,
    val strListVal :List<String>,
    val strIntMapVal :Map<String,Int>,
    val strColVal :Collection<String>,
    val enumVal :TestEnum
  ) : DData

  open class BaseData (val intVal :Int, val strVal :String) : DData
  class DerivedData (intVal :Int, strVal :String, val boolVal :Boolean, val anyVal :Any) :
    BaseData(intVal, strVal)

  open class TestEntity (id :Long) : DEntity.Keyed(id) {
    companion object : Meta<TestEntity> {
      val BoolProp    = TestEntity::boolProp
      val IntProp     = TestEntity::intProp
      val StringProp  = TestEntity::stringProp
      val DataProp    = TestEntity::dataProp
      val EnumProp    = TestEntity::enumProp

      override val entityName = "test"
      override fun create (id :Long) = TestEntity(id)
    }

    var boolProp   :Boolean  by dvalue(false)
    var intProp    :Int      by dvalue(0)
    var stringProp :String   by dvalue("")
    var dataProp   :TestData by dvalue(TestData(1, intArrayOf(1, 2), listOf(1, 2, 3),
                                                "foo", arrayOf("bar", "baz"), listOf("quuxx"),
                                                hashMapOf("foo" to 1, "bar" to 2),
                                                listOf("bippie"), TestEnum.BAR))
    var enumProp   :TestEnum by dvalue(TestEnum.FOO)

    override fun toString () = "$id $boolProp $intProp $stringProp $dataProp $enumProp"
    override val meta :Meta<TestEntity>
      get () = Companion
  }

  class DerivedEntity (id :Long) : TestEntity(id) {
    companion object : Meta<DerivedEntity> {
      val ListProp    = DerivedEntity::listProp

      override val entityName = "derived"
      override fun create (id :Long) = DerivedEntity(id)
    }

    var listProp :List<String> by dvalue(listOf<String>())

    override fun toString () = "${super.toString()} $listProp"
    override val meta :Meta<DerivedEntity>
      get () = Companion
  }

  // some hand coded serializers to give me an idea of what I want to generate
  val TestProtocol = object : DProtocol(4) {
    init {
      register(object : DSerializer<TestEnum>(TestEnum::class.java) {
        override fun get (pcol :DProtocol, buf :ByteBuffer) =
          TestEnum.valueOf(buf.getString())
        override fun put (pcol :DProtocol, buf :ByteBuffer, obj :TestEnum) :Unit =
          buf.putString(obj.name)
      })

      register(object : DSerializer<TestData>(TestData::class.java) {
        override fun get (pcol :DProtocol, buf :ByteBuffer) = TestData(
          buf.getInt(),
          buf.getIntArray(),
          buf.getList(pcol, Int::class.java),
          buf.getString(),
          buf.getStringArray(),
          buf.getList(pcol, String::class.java),
          buf.getMap(pcol, String::class.java, Int::class.java),
          buf.getCollection(pcol, String::class.java),
          buf.getValue(pcol, TestEnum::class.java)
        )
        override fun put (pcol :DProtocol, buf :ByteBuffer, obj :TestData) {
          buf.putInt(obj.intVal)
          buf.putIntArray(obj.intArrayVal)
          buf.putList(pcol, Int::class.java, obj.intListVal)
          buf.putString(obj.strVal)
          buf.putStringArray(obj.strArrayVal)
          buf.putList(pcol, String::class.java, obj.strListVal)
          buf.putMap(pcol, String::class.java, Int::class.java, obj.strIntMapVal)
          buf.putCollection(pcol, String::class.java, obj.strColVal)
          buf.putValue(pcol, TestEnum::class.java, obj.enumVal)
        }
      })

      register(object : DEntitySerializer<TestEntity>(TestEntity::class.java) {
        override fun create (buf :ByteBuffer) = TestEntity(buf.getLong())
        override fun read (pcol :DProtocol, buf :ByteBuffer, obj :TestEntity) {
          obj.boolProp = buf.getBoolean()
          obj.intProp = buf.getInt()
          obj.stringProp = buf.getString()
          obj.dataProp = buf.getValue(pcol, TestData::class.java)
          obj.enumProp = buf.getValue(pcol, TestEnum::class.java)
        }
        override fun put (pcol :DProtocol, buf :ByteBuffer, obj :TestEntity) {
          buf.putLong(obj.id)
          buf.putBoolean(obj.boolProp)
          buf.putInt(obj.intProp)
          buf.putString(obj.stringProp)
          buf.putValue(pcol, TestData::class.java, obj.dataProp)
          buf.putValue(pcol, TestEnum::class.java, obj.enumProp)
        }
      })

      register(object : DEntitySerializer<DerivedEntity>(DerivedEntity::class.java) {
        override fun create (buf :ByteBuffer) = DerivedEntity(buf.getLong())
        override fun read (pcol :DProtocol, buf :ByteBuffer, obj :DerivedEntity) {
          pcol.entitySerializer(TestEntity::class.java).read(pcol, buf, obj)
          obj.listProp = buf.getList(pcol, String::class.java)
        }
        override fun put (pcol :DProtocol, buf :ByteBuffer, obj :DerivedEntity) {
          pcol.entitySerializer(TestEntity::class.java).put(pcol, buf, obj)
          buf.putList(pcol, String::class.java, obj.listProp)
        }
      })
    }
  }

  @Test fun testTestSerializer () {
    val buf = ByteBuffer.allocate(1024)
    val data = TestData(42, intArrayOf(1, 3, 5, 7, 9), listOf(2, 4, 6, 8),
                        "peanut", arrayOf("who", "was", "that", "man"), listOf("kumar", "dude"),
                        hashMapOf("foo" to 1, "bar" to 2, "answer" to 42), listOf("pickle"),
                        TestEnum.BAZ)
    val entity = TestEntity(2)
    entity.boolProp = true
    entity.intProp = 42
    entity.stringProp = "Bob"

    val dentity = DerivedEntity(3)
    dentity.boolProp = false
    dentity.intProp = 13
    dentity.stringProp = "Jim"
    dentity.listProp = listOf("foo", "bar", "baz")

    val pcol = TestProtocol
    pcol.put(buf, data)
    pcol.put(buf, entity)
    pcol.put(buf, dentity)
    // println("Bytes: ${buf.position()}")
    buf.flip()
    // can't use default equals because Kotlin annoyingly punts on deep equals for arrays; sigh
    assertEquals(data.toString(), pcol.get(buf).toString())
    assertEquals(entity.toString(), pcol.get(buf).toString())
    assertEquals(dentity.toString(), pcol.get(buf).toString())
  }

  @Test fun testSelfSerializers () {
    val root = Paths.get(System.getProperty("user.dir"))
    val classes = root.resolve("..").resolve("client").resolve("target").resolve("classes")
    // println(classes)
    val out = StringWriter()
    process(listOf(classes), out)
    // println(out.toString())
    // TODO: test something?
  }

  @Test fun testSimpleSerializer () {
    val root = Paths.get(System.getProperty("user.dir"))
    val classes = root.resolve("target").resolve("test-classes")
    // println(classes)
    val out = StringWriter()
    process(listOf(classes), out)
    // println(out.toString())
    // TODO: test something?
  }

  // @Test fun debugGoldenAgeExtractor () {
  //   val root = Paths.get("/Users/mdb/projects/goldenage/core")
  //   val classes = root.resolve("target").resolve("classes")
  //   extractMetas(listOf(classes))
  // }
}
