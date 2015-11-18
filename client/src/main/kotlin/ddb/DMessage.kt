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
  class SubscribedRsp (val dbKey :String, val dbId :Int,
                       val keyeds :Collection<Collection<DEntity.Keyed>>,
                       val singles :Collection<DEntity.Singleton>,
                       val services :Collection<Class<*>>) : DMessage() {
    override val sizeHint :Int
      get () = 16*1024 // TODOL adjust based on count of entities?
    override fun toString () = "SubscribedRsp(key=$dbKey, id=$dbId, kents=${keyeds.size}, " +
      "sents=${singles.size}, svcs=${services.size})"

    companion object {
      val serializer = object : DSerializer<SubscribedRsp>(SubscribedRsp::class.java) {
        override fun get (pcol :DProtocol, buf :ByteBuffer) = SubscribedRsp(
          buf.getString(),
          buf.getInt(),
          getEntityLists(pcol, buf),
          buf.getCollection(pcol, ddb.DEntity.Singleton::class.java),
          buf.getCollection(pcol, java.lang.Class::class.java)
        )
        override fun put (pcol :DProtocol, buf :ByteBuffer, obj :SubscribedRsp) {
          buf.putString(obj.dbKey)
          buf.putInt(obj.dbId)
          putEntityLists(pcol, buf, obj.keyeds)
          buf.putCollection(pcol, ddb.DEntity.Singleton::class.java, obj.singles)
          buf.putCollection(pcol, java.lang.Class::class.java, obj.services)
        }
      }

      fun getEntityLists (pcol :DProtocol, buf :ByteBuffer) :Collection<Collection<DEntity.Keyed>> {
        val count = buf.getInt()
        val entityLists = arrayListOf<Collection<DEntity.Keyed>>()
        for (ii in 1..count) entityLists.add(buf.getCollection(pcol, DEntity.Keyed::class.java))
        return entityLists
      }
      fun putEntityLists (pcol :DProtocol, buf :ByteBuffer, lists :Collection<Collection<DEntity.Keyed>>) {
        buf.putInt(lists.size)
        for (list in lists) buf.putCollection(pcol, DEntity.Keyed::class.java, list)
      }
    }
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
  abstract class ServiceReq (val dbId :Int, val svcId :Int, val reqId :Int) : DMessage() {
    override fun toString () = "ServiceReq(db=$dbId, svc=$svcId, req=$reqId)"
  }
  // TODO: just stick 'args :Array<Any?> in ServiceReq?
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

  /** Communicates an entity property change. Server to client. */
  class PropChange (val dbId :Int, val entId :Int, val propId :Int, val value :Any) : DMessage()
}
