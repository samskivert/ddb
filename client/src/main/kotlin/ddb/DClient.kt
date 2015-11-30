//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import react.*

abstract class DClient {

  /** The protocol used by this client (must match the one used on the server). */
  abstract val proto :DProtocol

  /** Opens the database keyed `key`, subscribing to changes thereto until it is closed. */
  fun openDB (key :String) :RFuture<DDB> {
    val ddb = _dbsByKey[key]
    return if (ddb != null) RFuture.success<DDB>(ddb)
    else {
      val rsp = _pendingOpens[key]
      if (rsp != null) rsp
      else {
        val nrsp = RPromise.create<DDB>()
        _pendingOpens[key] = nrsp
        send(DMessage.SubscribeReq(key))
        nrsp
      }
    }
  }

  /** Closes `ddb`, ceasing all change notifications. The client should not reference `ddb` or any
    * entity contained therein after `ddb` has been closed. */
  fun closeDB (ddb :DDB) {
    if (_dbsByKey.remove(ddb.key) != null) {
      _dbsById.remove(ddb.id)
      send(DMessage.UnsubscribeReq(ddb.key))
    } else reportError("Closed unopen DB? [key=${ddb.key}]", Exception())
  }

  /** Called when something bad happens in the client. Implement as desired. */
  abstract fun reportError (msg :String, err :Throwable?) :Unit

  /** Sends a message to the server. */
  abstract fun send (msg :DMessage) :Unit

  internal fun nextReqId () :Int = ++_lastReqId

  internal fun sendCall (msg :DMessage.ServiceReq, onRsp :RPromise<out Any>) {
    _pendingCalls.put(msg.reqId, onRsp)
    send(msg)
  }

  protected fun recv (buf :ByteBuffer) {
    val msg = buf.getTagged<Any>(proto)
    if (msg is DMessage) recv(msg)
    else reportError("Got non-DMessage message: $msg", null)
  }

  protected fun recv (msg :DMessage) {
    try {
      when (msg) {
        is DMessage.SubscribedRsp -> {
          val ddb = DDBImpl(this, msg)
          _dbsByKey[msg.dbKey] = ddb
          _dbsById[msg.dbId] = ddb
          val onOpen = _pendingOpens.remove(msg.dbKey)
          if (onOpen != null) onOpen.succeed(ddb)
          else reportError("Missing listener for $msg", null)
        }
        is DMessage.SubFailedRsp -> {
          val onOpen = _pendingOpens.remove(msg.dbKey)
          if (onOpen != null) onOpen.fail(Exception(msg.cause))
          else reportError("Missing listener for $msg", null)
        }
        is DMessage.ServiceRsp -> {
          val onRsp = _pendingCalls.remove(msg.reqId)
          if (onRsp != null) onRsp.complete(msg.result)
          else reportError("Missing listener for $msg", null)
        }
        is DMessage.EntityCreated -> onDb(msg.dbId, msg) { it.apply(msg) }
        is DMessage.EntityDestroyed -> onDb(msg.dbId, msg) { it.apply(msg) }
        is DMessage.PropChange -> onDb(msg.dbId, msg) { it.apply(msg) }
        else -> reportError("Unknown message: $msg", null)
      }
    } catch (err :Throwable) {
      reportError("Failure processing: $msg", err)
    }
  }

  private inline fun onDb (dbId :Int, msg :DMessage, op :(DDBImpl) -> Unit) {
    val ddb = _dbsById[dbId]
    if (ddb != null) op(ddb)
    else reportError("Got message for unknown db: $msg", null)
  }

  private val _dbsByKey = hashMapOf<String,DDBImpl>()
  private val _dbsById = hashMapOf<Int,DDBImpl>()
  private val _pendingOpens = hashMapOf<String,RPromise<DDB>>()
  private val _pendingCalls = hashMapOf<Int,RPromise<out Any>>()
  private var _lastReqId = 0
}
