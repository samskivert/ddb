//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.ArrayDeque
import java.util.HashMap
import react.SignalView
import react.Try

abstract class DDBSourceImpl (key :String, id :Int, val server :DServer) : DDBSource(key, id) {

  companion object {
    /** Contains a reference to the currently executing DDBSource. */
    val current = ThreadLocal<DDBSourceImpl>()
  }

  /** Dispatches `msg` to this `DDB`. This method can be called from any thread and will process the
    * message on this DDB's single-threaded execution context.
    * @param onRsp if the processing of this message generates a response, it will be delivered
    * thereto. */
  fun dispatch (msg :DMessage, onRsp :SignalView.Listener<Try<Any>>) {
    postOp({ process(msg, onRsp) })
  }

  /** Queues the supplied operation for execution on this DDB's single-threaded execution context.
    * The op will be run as soon as all other operations queued on this DDB have completed. */
  fun postOp (op :() -> Unit) :Unit = synchronized(this) {
    _ops.offer(Runnable {
      try {
        current.set(this)
        op()
      } catch (t :Throwable) {
        server.onErr.report("Entity operation failed: $op", t)
      } finally {
        current.set(null)
        scheduleNext()
      }
    })
    if (_active == null) scheduleNext()
  }

  override fun <S : DService> register (sclass :Class<S>, service :S) {
    // TODO: we want to put a service proxy in here which encodes the request as a message and
    // delivers it to our op queue; that way if another DDB in this process asks us for a service,
    // it gets one that it can safely use from its execution context
    val prev = _services.put(sclass, service)
    assert(prev == null) { "Duplicate service registered for $sclass: had $prev got $service" }
    val id = (_nextServiceId++)
    _dispatchers.put(id, server.proto.dispatcher(sclass, service))
  }

  override fun <S : DService> service (sclass :Class<S>) :S {
    val svc = uncheckedNullCast<S?>(_services[sclass])
    return svc ?: throw IllegalArgumentException("No provider registered for $sclass")
  }

  override fun close () {
    server.closeDB(this)
  }

  override fun destroy () {
    server.closeDB(this)
    server.storage.destroyDB(key)
  }

  /** Schedules the next operation in this context on our executor. */
  protected fun scheduleNext () :Unit = synchronized(this) {
    val active = _ops.poll()
    _active = active
    if (active != null) server.exec.execute(active)
  }

  private fun process (msg :DMessage, onRsp :SignalView.Listener<Try<Any>>) :Unit = when(msg) {
    is DMessage.ServiceReq -> {
      val disp = _dispatchers[msg.svcId]
      if (disp != null) disp.dispatch(msg, onRsp)
      else server.onErr.report("Unknown service [ddb=$this, msg=$msg]", null)
    }
    else -> server.onErr.report("Unknown message! [ddb=$this, msg=$msg]", null)
  }

  /** The queue of operations pending on this context. */
  private val _ops = ArrayDeque<Runnable>()
  /** The currently active operation, or null. */
  private var _active :Runnable? = null

  /** The registry of service implementations by class token. */
  private val _services = HashMap<Class<*>,DService>()
  /** The registry of service dispatchers by id. */
  private val _dispatchers = HashMap<Int,DService.Dispatcher>()
  /** Used to assign unique ids to [DService] impls. */
  private var _nextServiceId = 1
}
