//
// DDB - for great syncing of data between server and clients

package ddb

import react.RFuture

/**
 * Marker trait for services which are provided by a [DDB]. Service methods must only accept
 * "marshallable" parameter types (primitives, collections of primitives, and [DData] classes), and
 * must return a [RFuture] result.
 */
interface DService {

  /** Wires [DService] into the [DProtocol] system. */
  abstract class Factory<S : DService> (type :Class<S>) : DProtocol.Component(type) {
    /** Creates a marshaller for our service with the supplied configuration. */
    abstract fun marshaller (source :DMessage.Source) :Marshaller<S>
    /** Creates a dispatcher for our service that uses `impl` to do its actual work. */
    abstract fun dispatcher (impl :S) :Dispatcher
  }

  /** Handles the marshalling of service calls into [DMessage.ServiceReq] messages and sending them
    * along for delivery to the server. A concrete marshaller class is generated for all [DService]
    * subtypes. */
  abstract class Marshaller<T : DService> (val svcId :Int, val source :DMessage.Source) : DService

  /** Handles unmarshalling [DMessage.ServiceReq] messages, calling the appropriate [DService]
    * method, and wiring up a response listener. */
  abstract class Dispatcher (val svcId :Int, val impl :DService) {
    /** Dispatches `req` to `impl`, returning its future result. */
    abstract fun dispatch (req :DMessage.ServiceReq) :RFuture<Any>
  }
}
