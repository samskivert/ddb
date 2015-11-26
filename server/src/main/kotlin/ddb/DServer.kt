//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import ddb.util.*

/**
 * Brings together all of the moving parts needed to coordinate a [DDB] server.
 *
 * @property exec the executor used to process all multithreaded actions. This should offer
 * approximately as many threads as the CPU has cores.
 */
abstract class DServer (val exec :Executor) {

  /** Used to report errors. */
  val onErr = object : ErrorReporter {
    override fun report (msg :String, err :Throwable?) {
      System.err.println(msg)
      if (err != null) err.printStackTrace(System.err)
    }
  }

  /** Translates everything to/from bytes. */
  abstract val proto :DProtocol

  /** Provides the backing store for [DDB]s. */
  abstract val storage :DStorage

  /** Returns true if the database identified by `key` is currently open, false otherwise. */
  fun isDBOpen (key :String) :Boolean = _dbsByKey.asMap().containsKey(key)

  /** Opens the database identified by `key`, resolving it from persistent storage if necessary. */
  fun openDB (key :String) :SourceDB = _dbsByKey.get(key)

  /** Returns the database with `id` or null. */
  fun getDB (id :Int) :SourceDB? = _dbsById[id]

  internal fun closeDB (ddb :SourceDB) {
    _dbsByKey.invalidate(ddb.key)
    _dbsById.remove(ddb.id)
  }

  // this should be internal but then I can't use it in test code, blah
  /*internal*/ fun dispatch (dmsg :DMessage, session :DSession) { when (dmsg) {
    is DMessage.SubscribeReq -> _dbsByKey[dmsg.dbKey]?.subscribe(session)
    is DMessage.ServiceReq   -> _dbsById[dmsg.dbId]?.call(dmsg, session)
  }}

  private val _nextServiceId = AtomicInteger(1)
  private val _dbsByKey = cacheMap<String,SourceDBImpl> { key ->
    storage.openDB(key, _nextServiceId.getAndIncrement()).apply {
      _dbsById.put(id, this)
    }
  }
  private val _dbsById = ConcurrentHashMap<Int,SourceDBImpl>()
}
