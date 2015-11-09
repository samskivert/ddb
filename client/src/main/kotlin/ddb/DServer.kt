//
// DDB - for great syncing of data between server and clients

package ddb

abstract class DServer {

  /**
   * Opens the database with the specified `id`, creating its backing store if necessary.
   */
  abstract fun openDB (id :String) :DDB.Source

  /**
   * Destroys `ddb`, deleting all entities contained therein.
   */
  abstract fun destroyDB (ddb :DDB.Source) :Unit
}
