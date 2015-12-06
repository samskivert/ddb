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

  /** Used to log info & errors. */
  val log = object : Logger {
    override fun info (msg :String) {
      System.err.println(msg)
    }
    override fun error (msg :String, err :Throwable?) {
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

  /** Returns the database identified by `key` or null. */
  fun getDB (key :String) :SourceDB? = _dbsByKey.getIfPresent(key)

  /** Returns the database with id `id` or null. */
  fun getDB (id :Int) :SourceDB? = _dbsById[id]

  internal fun closeDB (ddb :SourceDB) {
    _dbsByKey.invalidate(ddb.key)
    _dbsById.remove(ddb.id)
  }

  // this should be internal but then I can't use it in test code, blah
  /*internal*/ fun dispatch (dmsg :DMessage, session :DSession) { when (dmsg) {
    is DMessage.SubscribeReq -> {
      val db = _dbsByKey.getIfPresent(dmsg.dbKey)
      if (db != null) db.subscribe(session)
      else session.send(DMessage.SubFailedRsp(dmsg.dbKey, "e.no_such_ddb"))
    }
    is DMessage.ServiceReq   -> {
      val db = _dbsById[dmsg.dbId]
      if (db != null) db.call(dmsg, session)
      else session.send(DMessage.FailedRsp(dmsg.reqId, "e.no_such_ddb"))
    }
    is DMessage.PropChange   -> {
      val db = _dbsById[dmsg.dbId]
      if (db != null) db.change(dmsg, session)
      else log.error("Dropping prop change on unknown db [msg=$dmsg, from=$session]", null)
    }
  }}

  private val _nextServiceId = AtomicInteger(1)
  private val _dbsByKey = cacheMap<String,SourceDBImpl> { key ->
    storage.openDB(key, _nextServiceId.getAndIncrement()).apply {
      _dbsById.put(id, this)
    }
  }
  private val _dbsById = ConcurrentHashMap<Int,SourceDBImpl>()
}
