//
// DDB - for great syncing of data between server and clients

package ddb

import org.junit.Test
import org.junit.Assert.*

class DDBTest {

  class TestEntity (id :Long) : DEntity(id) {
    companion object :DCompanion<TestEntity> {
      val Name = TestEntity::name
      val Age  = TestEntity::age
      override val entityName = "test"
      override fun create (id :Long) = TestEntity(id)
    }

    var name :String by dvalue(1, "")
    var age :Int by dvalue(1, 0)

    override val companion = Companion
  }

  @Test fun testCRUD () {
    val server = EphemeralServer()
    val ddb = server.openDB("test")
    val ent = ddb.create(TestEntity.Companion)
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
}
