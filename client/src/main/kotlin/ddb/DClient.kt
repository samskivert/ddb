//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import react.*

abstract class DClient {

  /** A signal emitted when this client's connection has failed. This will be followed by an
    * emission of [onClose] in the event of connection failure. */
  val onError = Signal.create<Throwable>()

  /** A signal emitted when this client has closed its connection. */
  val onClose = Signal.create<DClient>()

  /** The protocol used by this client (must match the one used on the server). */
  abstract val proto :DProtocol

  /** Opens the database keyed `key`, subscribing to changes thereto until it is closed. */
  fun openDB (key :String) :RFuture<DDB> {
    // if we already have the db open, then we're done
    val ddb = _dbsByKey[key]
    if (ddb != null) return RFuture.success<DDB>(ddb)

    // if we already have an open in progress for this db, piggy back on that
    val rsp = _pendingOpens[key]
    if (rsp != null) return rsp

    // create a new "open db" message + promise pair and deliver the message
    val nrsp = safePromise<DDB>()
    _pendingOpens[key] = nrsp
    val msg = DMessage.SubscribeReq(key)

    // if we have a session, just send the message
    val sess = _session
    if (sess != null) sess.send(msg)
    else {
      // if we have a session open in progress, piggy back on that
      val sessF = _sessionF
      if (sessF != null) sessF.onSuccess { it.send(msg) }
      // otherwise we need to start a new session
      else _sessionF = openSession().
        onSuccess { _session = it }.
        onSuccess { it.send(msg) }.
        onFailure { err -> failPenders(err) }.
        onComplete { _sessionF = null }
    }

    return nrsp
  }

  /** Closes `ddb`, ceasing all change notifications. The client should not reference `ddb` or any
    * entity contained therein after `ddb` has been closed. */
  fun closeDB (ddb :DDB) {
    val sess = _session
    if (sess == null) reportError("Can't close $ddb; have no active session", null)
    else if (_dbsByKey.remove(ddb.key) == null) {
      _dbsById.remove(ddb.id)
      sess.send(DMessage.UnsubscribeReq(ddb.key))
    }
  }

  /** Called when something bad happens in the client. Implement as desired. */
  abstract fun reportError (msg :String, err :Throwable?) :Unit

  internal fun nextReqId () :Int = ++_lastReqId

  internal fun sendCall (msg :DMessage.ServiceReq, onRsp :RPromise<out Any>) {
    val sess = _session
    if (sess == null) onRsp.fail(Exception("No active session"))
    else {
      _pendingCalls.put(msg.reqId, onRsp)
      sess.send(msg)
    }
  }

  internal fun <T> safePromise () :RPromise<T> = object : RPromise<T>() {
    override fun complete (result :Try<T>) {
      try {
        super.complete(result)
      } catch (err :Throwable) {
        reportError("DClient promise choked on complete ($result)", err)
      }
    }
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

  abstract inner class Session {
    /** Sends a message to the server. */
    abstract fun send (msg :DMessage) :Unit
    /** Closes the current session gracefully. */
    abstract fun close () :Unit
  }

  protected abstract fun openSession () :RFuture<Session>

  protected fun sessionDidFail (cause :Throwable) {
    failPenders(cause)
    // TODO: will sessionDidClose be called by the subclass?
    onError.emit(cause)
  }

  protected fun sessionDidClose () {
    _session = null
    _dbsByKey.clear()
    _dbsById.clear()
    _lastReqId = 0
    failPenders(Exception("Session closed."))
    onClose.emit(this)
  }

  private fun failPenders (cause :Throwable) {
    if (!_pendingOpens.isEmpty() || !_pendingCalls.isEmpty()) {
      for (po in _pendingOpens.values) po.fail(cause)
      _pendingOpens.clear()
      for (pc in _pendingCalls.values) pc.fail(cause)
      _pendingCalls.clear()
    }
  }

  private inline fun onDb (dbId :Int, msg :DMessage, op :(DDBImpl) -> Unit) {
    val ddb = _dbsById[dbId]
    if (ddb != null) op(ddb)
    else reportError("Got message for unknown db: $msg", null)
  }

  private var _sessionF :RFuture<Session>? = null
  private var _session :Session? = null
  private val _pendingMsgs = arrayListOf<DMessage>()

  private val _dbsByKey = hashMapOf<String,DDBImpl>()
  private val _dbsById = hashMapOf<Int,DDBImpl>()
  private val _pendingOpens = hashMapOf<String,RPromise<DDB>>()
  private val _pendingCalls = hashMapOf<Int,RPromise<out Any>>()
  private var _lastReqId = 0
}
