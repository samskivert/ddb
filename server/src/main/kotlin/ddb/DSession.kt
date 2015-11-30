//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import react.Signal
import react.SignalView
import react.Try
import ddb.util.*

/**
 * Tracks information for a single client session. Sending messages to the client is done via this
 * instance. Signals are provided to report session failure and termination, for interested parties.
 * A mechanism is also provided for configuring session-local state.
 */
abstract class DSession (val server :DServer) {

  // TODO: server-initiated session closure
  // TODO: what else?

  companion object {

    /** Returns the currently bound session.
      * @throws IllegalStateException if no session is currently bound. */
    val current :DSession
      get () = threadSession.get() ?: throw IllegalStateException("No current session.")

    /** Returns the currently bound session or null. */
    val currentOrNull :DSession?
      get () = threadSession.get()

    /** Contains a reference to currently active session, if any. */
    val threadSession = ThreadLocal<DSession>()
  }

  /** A signal emitted when this session has terminated (cleanly or in error). */
  val onClose = Signal.create<DSession>()

  /** A signal emitted when this session terminates due to connection failure. This will be followed
    * by an emitting of the [onClose] signal. */
  val onError = Signal.create<Throwable>()

  /** Runs `op` with this session bound as the current session. */
  inline fun runBound (crossinline op :() -> Unit) {
    threadSession.set(this)
    try { op() }
    finally { threadSession.set(null) }
  }

  /** Returns the state instance identified by `clazz`, or excepts. */
  fun <T:Any> state (clazz :KClass<T>) :T = uncheckedCast<T>(
    _state[clazz] ?: throw IllegalStateException("No instance registered for $clazz"))
  /** Returns the state instance identified by `clazz`, or null. */
  fun <T:Any> checkState (clazz :KClass<T>) :T? = uncheckedNullCast<T>(_state[clazz])
  /** Sets the state instance identified by `clazz` to `value`. */
  fun <T:Any> setState (clazz :KClass<T>, value :T) { _state[clazz] = value }

  /** Returns a string representation of the address (usually IP) associated with this session. */
  abstract fun address () :String

  /** Queues the binary `msg` for delivery to this client. */
  abstract fun send (msg :ByteBuffer) :Unit

  /** Flattens `msg` to bytes and queues it for delivery to this client. */
  fun send (msg :DMessage) :Unit = send(msg.flatten(server.proto))

  /** Handles results from [DService] calls initiated by this client. */
  fun svcRsp (dmsg :DMessage.ServiceReq) = object : SignalView.Listener<Try<out Any>> {
    override fun onEmit (result :Try<out Any>) {
      if (result.isSuccess()) {
        send(DMessage.CalledRsp(dmsg.reqId, result.get()))
      } else {
        val err = result.failure
        send(DMessage.FailedRsp(dmsg.reqId, err.message ?: err.javaClass.name))
      }
    }
  }

  /** Decodes the message in `buf` and dispatches it appropriately. */
  protected fun process (buf :ByteBuffer) {
    try {
      val msg = buf.getTagged<Any>(server.proto)
      if (msg is DMessage) server.dispatch(msg, this)
      else server.onErr.report("Got unknown message from client [$this, msg=$msg]", null)
    } catch (t :Throwable) {
      server.onErr.report("WebSocket decode failure [$this, buf=$buf]", t);
    }
  }

  private val _state = ConcurrentHashMap<KClass<*>,Any>()
}
