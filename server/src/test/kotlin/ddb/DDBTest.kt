//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.junit.Test
import org.junit.Assert.*

class DDBTest {

  class TestEntity (id :Long) : DEntity(id) {
    companion object : Meta<TestEntity>() {
      val Name = prop(TestEntity::name)
      val Age  = prop(TestEntity::age)
      override val entityName = "test"
      override fun create (id :Long) = TestEntity(id)
    }

    var name :String by Name.delegate("")
    var age :Int     by Age.delegate(0)

    override val meta = Companion
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
}
