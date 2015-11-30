//
// DDB - for great syncing of data between server and clients

package ddb

import react.RFuture
import react.RPromise

/**
 * Marker trait for services which are provided by a [DDB]. Service methods must only accept
 * "marshallable" parameter types (primitives, collections of primitives, and [DData] classes), and
 * must return a [RFuture] result.
 */
interface DService {

  /** An exception thrown to indicate service failure. Such exceptions are propagated back to the
    * client without recording them internally as unexpected failures. */
  class ServiceException (msg :String) : Exception(msg)

  /** Wires [DService] into the [DProtocol] system. */
  abstract class Factory<S : DService> (type :Class<S>) : DProtocol.Component(type) {
    /** Creates a marshaller for our service with the supplied configuration. */
    abstract fun marshaller (host :Host) :Marshaller<S>
    /** Creates a dispatcher for our service that uses `impl` to do its actual work. */
    abstract fun dispatcher (impl :S) :Dispatcher
  }

  /** Handles the marshalling of service calls into [DMessage.ServiceReq] messages and sending them
    * along for delivery to the server. A concrete marshaller class is generated for all [DService]
    * subtypes. */
  abstract class Marshaller<S : DService> (val svcId :Short) : DService

  /** Handles unmarshalling [DMessage.ServiceReq] messages, calling the appropriate [DService]
    * method, and wiring up a response listener. */
  abstract class Dispatcher (val svcId :Short) {
    /** Dispatches `req`, returning its future result. */
    abstract fun dispatch (req :DMessage.ServiceReq) :RFuture<out Any>
    /** Used by dispatchers to report invalid requests. */
    protected fun unknown (methId :Short, impl :Any) = RFuture.failure<Any>(
      IllegalArgumentException("Unknown service method [methId=$methId, impl=$impl]"))
  }

  /** Allows a [DService] to communicate to the [DDB] in which it is hosted. */
  interface Host {
    /** The id of the DDB that's hosting this service. */
    val id :Int

    /** Returns an id to use for a service request to this host. */
    fun nextReqId () :Int

    /** Processes the [DService] call in `msg`, reporting success or failure to `onRsp`. */
    fun call (msg :DMessage.ServiceReq, onRsp :RPromise<out Any>) :Unit
  }
}

/** Throws `DService.ServiceException` with `msg` unless `cond` is true. */
inline fun svcRequire (cond :Boolean, msg :() -> String) {
  if (!cond) throw DService.ServiceException(msg())
}

/** Throws `DService.ServiceException` with `msg` unless `cond` is true. */
inline fun <T:Any> svcRequireNotNull (value :T?, msg :() -> String) :T =
  if (value == null) throw DService.ServiceException(msg()) else value
