//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.RFuture

/**
 * Provides in-memory databases which are not backed by any persistent store. Useful for testing,
 * prototyping, etc.
 */
class EphemeralStorage (val server :DServer) : DStorage() {

  override fun openDB (key :String, id :Int) = EphemeralDDB(key, id, server)
  override fun destroyDB (key :String) {} // nothing needed

  class EphemeralDDB (key :String, id :Int, server :DServer) : SourceDBImpl(key, id, server) {
  }
}
