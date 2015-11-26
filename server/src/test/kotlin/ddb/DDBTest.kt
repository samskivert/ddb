//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.junit.Test
import org.junit.Assert.*
import react.RFuture

class DDBTest {

  class TestEntity (id :Long) : DEntity(id) {
    companion object : Meta<TestEntity>() {
      val Name = prop(TestEntity::name)
      val Age  = prop(TestEntity::age)
      override val entityName = "test"
      override fun create (id :Long) = TestEntity(id)
    }

    var name :String by Name("")
    var age :Int     by Age(0)

    override val meta = Companion
  }

  interface TestService : DService {
    fun longest (names :List<String>) :RFuture<String>
    fun lookup (map :Map<Int,List<String>>, key :Int) :RFuture<List<String>>
  }

  class TestImpl : TestService {
    override fun longest (names :List<String>) =
      if (names.isEmpty()) RFuture.failure(Exception("Empty list!"))
      else RFuture.success(names.sortedBy { it.length }.last())
    override fun lookup (map :Map<Int,List<String>>, key :Int) =
      map[key]?.let { RFuture.success(it) } ?: RFuture.failure(Exception("No mapping for $key"))
  }

  fun testServer () :DServer {
    val directExec = object : Executor {
      override fun execute (cmd :Runnable) = cmd.run()
    }
    return object : DServer(directExec) {
      override val proto = TestProtocol()
      override val storage = EphemeralStorage(this)
    }
  }

  fun testClient (server :DServer) = object : DClient() {
    val session = object : DSession(server) {
      override fun address () = "test"
      override fun send (msg :ByteBuffer) = recv(msg)
    }
    override val proto = TestProtocol()
    override fun reportError (msg :String, err :Throwable?) {
      println(msg)
      if (err != null) err.printStackTrace(System.out)
    }
    override fun send (msg :DMessage) {
      server.dispatch(msg, session)
    }
  }

  @Test fun testServerCRUD () {
    val server = testServer()
    val ddb = server.openDB("test")
    var changes = 0
    val ent = ddb.create(TestEntity, {})
    ent.onEmit(TestEntity.Age) { age ->
      // println("Age changed $age")
      assertEquals(15, age)
      changes += 1
    }
    ent.onChange(TestEntity.Age) { nage, oage ->
      // println("Age changed $oage -> $nage")
      assertEquals(0, oage)
      assertEquals(15, nage)
      changes += 1
    }
    ent.name = "pants"
    assertEquals("pants", ent.name)
    ent.age = 15
    assertEquals(15, ent.age)
    assertEquals(2, changes)
  }

  @Test fun testClientSubscribe () {
    val server = testServer()
    val sddb = server.openDB("test")
    val sent = sddb.create(TestEntity) { ent ->
      ent.name = "Arthur Dent"
      ent.age = 42
    }

    val client = testClient(server)
    client.openDB("test").
      onSuccess { cddb ->
        // println("Got DDB $cddb")
        var changes = 0
        val cent = cddb.get<TestEntity>(sent.id)
        cent.onChange(TestEntity.Age) { age, oage ->
          // println("Age changed $oage -> $age")
          assertEquals(42, oage)
          assertEquals(15, age)
          changes += 1
        }
        sent.age = 15
        client.closeDB(cddb)
        assertEquals(1, changes)
      }.
      onFailure { cause ->
        println("openDB failed: $cause")
      }
  }

  @Test fun testRPC () {
    val server = testServer()
    val sddb = server.openDB("test")
    sddb.register(TestService::class.java, TestImpl())

    val client = testClient(server)
    client.openDB("test").
      onSuccess { cddb ->
        // println("Got DDB $cddb")
        var responses = 0
        val svc = cddb.service(TestService::class.java)
        svc.longest(listOf("Foo", "Foozle", "Fooz")).onSuccess { str ->
          assertEquals("Foozle", str)
          responses += 1
          // println("Longest $str")
        }
        svc.lookup(mapOf(5 to listOf("foo", "bar", "baz"),
                         3 to listOf("bim", "bam", "boom")), 5).onSuccess { res ->
          // println("Lookup: 5 -> $res")
          assertEquals(listOf("foo", "bar", "baz"), res)
          responses += 1
        }
        client.closeDB(cddb)
        assertEquals(2, responses)
      }.
      onFailure { cause ->
        fail("openDB failed: $cause")
      }
  }
}
