//
// DDB - for great syncing of data between server and clients

package ddb

import org.junit.Test
import org.junit.Assert.*

class DValueTest {

  @Test fun testGetSet () {
    val dv = DValue(3)
    assertEquals(3, dv())
    dv.update(4)
    assertEquals(4, dv())
  }
}
