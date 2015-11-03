//
// DDB - for great syncing of data between server and clients

package ddb

import org.junit.Test
import org.junit.Assert.*

class DDBTest {
    class TestEntity (id :Long) : DEntity(id) {
        var name :String by dvalue(1, "")
        var age :Int by dvalue(1, 0)

        override val companion = Companion

        companion object :DCompanion<TestEntity> {
            val NAME = TestEntity::name
            val AGE  = TestEntity::age
            override val entityName = "test"
            override fun create (id :Long) = TestEntity(id)
        }
    }

    @Test fun testCRUD () {
        val ddb = DDB()
        val ent = ddb.create(TestEntity.Companion)
        ent.onEmit(TestEntity.AGE) { age ->
            println("Age changed $age")
        }
        ent.onChange(TestEntity.AGE) { nage, oage ->
            println("Age changed $oage -> $nage")
        }
        ent.name = "pants"
        assertEquals("pants", ent.name)
        ent.age = 15
        assertEquals(15, ent.age)
    }
}
