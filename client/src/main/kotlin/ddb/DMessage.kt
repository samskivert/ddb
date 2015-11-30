//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.BufferOverflowException
import java.nio.ByteBuffer
import react.SignalView
import react.Try

/** Encapsulates messages between client and server. */
abstract class DMessage : DData {

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
        buf.putTagged(proto, this)
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
  class SubscribedRsp (val dbKey :String, val dbId :Int, val entities :Collection<DEntity>,
                       val services :Collection<Class<*>>) : DMessage() {
    override val sizeHint :Int
      get () = 16*1024 // TODOL adjust based on count of entities?
    override fun toString () = "SubscribedRsp(key=$dbKey, id=$dbId, ents=${entities.size}, " +
      "svcs=${services.size})"
  }

  /** Indicates DDB subscribe failure. */
  class SubFailedRsp (val dbKey :String, val cause :String) : DMessage() {
    override fun toString () = "SubFailedRsp(key=$dbKey, cause=$cause)"
  }

  /** Requests to unsubscribe from the DDB identified by `dbKey`. Client to server. */
  class UnsubscribeReq (val dbKey :String) : DMessage() {
    override fun toString () = "UnsubscribeReq(db=$dbKey)"
  }

  /** Initiates a [DService] call. Client to server. */
  class ServiceReq (val dbId :Int, val svcId :Short, val methId :Short, val reqId :Int,
                    val args :List<Any>) : DMessage() {
    override fun toString () =
      "ServiceReq(db=$dbId, svc=$svcId, meth=$methId, req=$reqId, argc=${args.size})"
  }
  /** Simplifies life when dispatching [DService] responses. */
  abstract class ServiceRsp (val reqId :Int) : DMessage () {
    abstract val result :Try<Any>
    override fun toString () = "ServiceRsp(req=$reqId, res=$result)"
  }
  /** Indicates a successful [DService] call. */
  class CalledRsp (reqId :Int, val value :Any) : ServiceRsp(reqId) {
    override val result :Try<Any>
      get () = Try.success(value)
  }
  /** Indicates a failed [DService] call. */
  class FailedRsp (reqId :Int, val cause :String) : ServiceRsp(reqId) {
    override val result :Try<Any>
      get () = Try.failure(Exception(cause))
  }

  /** Communicates entity creation. Server to client. */
  class EntityCreated (val dbId :Int, val entity :DEntity) : DMessage()
  /** Communicates entity destruction. Server to client. */
  class EntityDestroyed (val dbId :Int, val entId :Long) : DMessage()
  /** Communicates an entity property change. Server to client. */
  class PropChange (val dbId :Int, val entId :Long, val propId :Short, val value :Any) : DMessage()
}
