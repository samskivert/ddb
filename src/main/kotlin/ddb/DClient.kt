//
// DDB - for great syncing of data between server and clients

package ddb

import react.RFuture

abstract class DClient {

  /** Opens the database named `id`, subscribing to changes thereto until it is closed. */
  abstract fun open (id :String) :RFuture<DDB>

  /** Opens the databases named `ids`, subscribing to each individually in single request. */
  abstract fun open (ids :List<String>) :RFuture<List<DDB>>

  /** Closes `ddb`, ceasing all change notifications. The client should not reference `ddb` or any
    * entity contained therein after `ddb` has been closed. */
  abstract fun close (ddb :DDB) :Unit
}
