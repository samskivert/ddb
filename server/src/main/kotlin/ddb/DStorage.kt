//
// DDB - for great syncing of data between server and clients

package ddb

/**
 * Abstracts over a storage mechanism for [DDB] instances. For testing we have [EphemeralStorage]
 * which maintains dbs in memory and provides no persistence. Eventually we'll also have a Google
 * App Engine Datastore backed implementation.
 */
abstract class DStorage {

  /**
   * Opens the database with the specified `key`, assigning it the ephemeral id `id`. This should
   * create the database in the backing store if necessary. The storage implementation need not
   * cache database instances, the server will keep the database around until closed. Storage need
   * only concern itself with instantiating a concrete [SourceDB] implementation backed by the
   * appropriate storage mechanism.
   */
  abstract fun openDB (key :String, id :Int) :SourceDBImpl

  /**
   * Destroys the database identified by `key`, removing all traces of it from the persistent store.
   */
  abstract fun destroyDB (key :String) :Unit
}
