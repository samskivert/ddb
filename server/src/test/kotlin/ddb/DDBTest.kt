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
    val ent = ddb.create(TestEntity, {})
    ent.onEmit(TestEntity.Age) { age ->
      println("Age changed $age")
    }
    ent.onChange(TestEntity.Age) { nage, oage ->
      println("Age changed $oage -> $nage")
    }
    ent.name = "pants"
    assertEquals("pants", ent.name)
    ent.age = 15
    assertEquals(15, ent.age)
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
        println("Got DDB $cddb")
        val cent = cddb.get<TestEntity>(sent.id)
        cent.onChange(TestEntity.Age) { age, oage ->
          println("Age changed $oage -> $age")
        }
        sent.age = 15
        client.closeDB(cddb)
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
        println("Got DDB $cddb")
        val svc = cddb.service(TestService::class.java)
        svc.longest(listOf("Foo", "Foozle", "Fooz")).onSuccess { str ->
          println("Longest $str")
        }
        svc.lookup(mapOf(5 to listOf("foo", "bar", "baz"),
                         3 to listOf("bim", "bam", "boom")), 5).onSuccess { res ->
          println("Lookup: 5 -> $res")
        }
        client.closeDB(cddb)
      }.
      onFailure { cause ->
        println("openDB failed: $cause")
      }
  }
}
