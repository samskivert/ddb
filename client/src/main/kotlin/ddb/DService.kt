//
// DDB - for great syncing of data between server and clients

package ddb

import react.RFuture
import react.SignalView
import react.Try

/**
 * Marker trait for services which are provided by a [DDB]. Service methods must only accept
 * "marshallable" parameter types (primitives, collections of primitives, and [DData] classes), and
 * must return a [RFuture] result.
 */
interface DService {

  /** Handles unmarshalling [DMessage.ServiceReq] messages, calling the appropriate [DService]
    * method, and wiring up a response listener. */
  abstract class Dispatcher (val impl :DService) {

    /** Dispatches `req` to `impl`, routing the result to `onRsp`. */
    abstract fun dispatch (req :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<Any>>) :Unit
  }
}
