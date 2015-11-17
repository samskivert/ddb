//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import react.SignalView
import react.Try

/** Encapsulates messages between client and server. */
abstract class DMessage : DData {

  /** A thing that accepts [DMessage]s. */
  interface Source {
    /** The id of the DDB that's on the other end of this source. */
    val id :Int

    /** Processes the [DService] call in `msg`, reporting success or failure to `onRsp`. */
    fun call (msg :ServiceReq, onRsp :SignalView.Listener<Try<Any>>) :Unit
  }

  /** Returns a hint to the flattened size of this message, in bytes. */
  open val sizeHint :Int
    get () = 256

  /** Flattens this message into a [ByteBuffer]. The buffer will be flipped prior to returning so
    * that it is ready to be sent to a client. */
  fun flatten (proto :DProtocol) :ByteBuffer {
    var size = sizeHint
    while (true) {
      try {
        // TODO: reuse buffers? track distribution of size by concrete message type and start with a
        // buffer that is at the 90th size percentile?
        val buf = ByteBuffer.allocate(size)
        proto.put(buf, this)
        buf.flip()
        return buf
      } catch (e :BufferOverflowException) {
        // double our buffer size and try again
        size *= 2
      }
    }
  }

  /** Requests to subscribe to the DDB identified by `dbKey`. Client to server. */
  class SubscribeReq (val dbKey :String) : DMessage() {
    override fun toString () = "SubscribeReq(db=$dbKey)"
  }
  /** Indicates DDB subscribe success. */
  class SubscribedRsp (val dbKey :String, val entities :Collection<Collection<DEntity>>,
                       val services :Map<Class<*>,Int>) : DMessage() {
    override val sizeHint :Int
      get () = 8092 // TODOL adjust based on entities size?
    override fun toString () =
      "SubscribedRsp(key=$dbKey, ents=${entities.size}, svcs=${services.size})"
  }

  /** Indicates DDB subscribe failure. */
  class SubFailedRsp (val dbKey :String, val cause :String) : DMessage() {
    override fun toString () = "SubFailedRsp(key=$dbKey, cause=$cause)"
  }

  /** Initiates a [DService] call. Client to server. */
  abstract class ServiceReq (val dbId :Int, val svcId :Int, val reqId :Int) : DMessage() {
    override fun toString () = "ServiceReq(db=$dbId, svc=$svcId, req=$reqId)"
  }
  // TODO: just stick 'args :Array<Any?> in ServiceReq?
  /** Indicates a successful [DService] call. */
  class CalledRsp (val reqId :Int, val result :Any) : DMessage() {
    override fun toString () = "CalledRsp(req=$reqId, res=$result)"
  }
  /** Indicates a failed [DService] call. */
  class FailedRsp (val reqId :Int, val cause :String) : DMessage() {
    override fun toString () = "FailedRsp(req=$reqId, cause=$cause)"
  }

  /** Communicates an entity property change. Server to client. */
  class PropChange (val dbId :Int, val entId :Int, val propId :Int, val value :Any) : DMessage()
}
