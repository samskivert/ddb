//
// DDB - for great syncing of data between server and clients

package ddb

import org.junit.Test
import org.junit.Assert.*

class DDBTest {
    class TestEntity (id :Long) : DEntity(id) {
        val name = dvalue(1, "")

        override fun companion () = Companion

        companion object :DCompanion<TestEntity> {
            override fun entityName () = "test"
            override fun create (id :Long) = TestEntity(id)
        }
    }

    @Test fun testCRUD () {
        val ddb = DDB()

        val ent = ddb.create(TestEntity.Companion)
        ent.name.update("pants")
        assertEquals("pants", ent.name())
    }
}
