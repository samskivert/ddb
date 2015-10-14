//
// DDB - for great syncing of data between server and clients

package ddb

import org.junit.Test
import org.junit.Assert._

class DValueTest {

  @Test def testGetSet () {
    val dv = new DValue[Int](3)
    assertEquals(3, dv())
    dv() = 4
    assertEquals(4, dv())
  }

  @Test def testAppend () {
    val dv = new DValue[Seq[Int]](Seq(3))
    assertEquals(Seq(3), dv())
    dv += 4
    assertEquals(Seq(3, 4), dv())
  }
}
