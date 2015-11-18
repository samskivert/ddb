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
    val ddb = _ddbs.get(key)
    return if (ddb != null) RFuture.success(ddb)
    else _pendingOpens.getOrPut(key) {
      send(DMessage.SubscribeReq(key))
      RPromise.create<DDB>()
    }
  }

  /** Closes `ddb`, ceasing all change notifications. The client should not reference `ddb` or any
    * entity contained therein after `ddb` has been closed. */
  fun closeDB (ddb :DDB) {
    if (_ddbs.remove(ddb.key) != null) send(DMessage.UnsubscribeReq(ddb.key))
    else reportError("Closed unopen DB? [key=${ddb.key}]", Exception())
  }

  /** Called when something bad happens in the client. Implement as desired. */
  abstract fun reportError (msg :String, err :Throwable?) :Unit

  protected abstract fun send (msg :DMessage) :Unit

  internal fun sendCall (msg :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<Any>>) {
    _pendingCalls.put(msg.reqId, onRsp)
    send(msg)
  }

  protected fun recv (buf :ByteBuffer) {
    val msg = proto.get(buf)
    if (msg is DMessage) recv(msg)
    else reportError("Got non-DMessage message: $msg", null)
  }

  protected fun recv (msg :DMessage) :Unit = when (msg) {
    is DMessage.SubscribedRsp -> {
      val ddb = DDBImpl(this, msg)
      _ddbs[msg.dbKey] = ddb
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
      if (onRsp != null) onRsp.onEmit(msg.result)
      else reportError("Missing listener for $msg", null)
    }
    is DMessage.PropChange -> {}
    else -> reportError("Unknown message: $msg", null)
  }

  private val _ddbs = hashMapOf<String,DDB>()
  private val _pendingOpens = hashMapOf<String,RPromise<DDB>>()
  private val _pendingCalls = hashMapOf<Int,SignalView.Listener<Try<Any>>>()
}
