//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer
import react.RFuture
import react.RPromise

class TestProtocol : DProtocol(12) {
  init {
    register(object : DSerializer<ddb.DMessage.FailedRsp>(ddb.DMessage.FailedRsp::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.FailedRsp(
        buf.getInt(),
        buf.getString()
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.FailedRsp) {
        buf.putInt(obj.reqId)
        buf.putString(obj.cause)
      }
    })
    register(object : DSerializer<ddb.DMessage.EntityDestroyed>(ddb.DMessage.EntityDestroyed::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.EntityDestroyed(
        buf.getInt(),
        buf.getLong()
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.EntityDestroyed) {
        buf.putInt(obj.dbId)
        buf.putLong(obj.entId)
      }
    })
    register(object : DSerializer<ddb.DMessage.ServiceReq>(ddb.DMessage.ServiceReq::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.ServiceReq(
        buf.getInt(),
        buf.getShort(),
        buf.getShort(),
        buf.getInt(),
        buf.getList(pcol, Any::class.java)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.ServiceReq) {
        buf.putInt(obj.dbId)
        buf.putShort(obj.svcId)
        buf.putShort(obj.methId)
        buf.putInt(obj.reqId)
        buf.putList(pcol, Any::class.java, obj.args)
      }
    })
    register(object : DSerializer<ddb.DMessage.UnsubscribeReq>(ddb.DMessage.UnsubscribeReq::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.UnsubscribeReq(
        buf.getString()
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.UnsubscribeReq) {
        buf.putString(obj.dbKey)
      }
    })
    register(object : DSerializer<ddb.DMessage.SubscribeReq>(ddb.DMessage.SubscribeReq::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.SubscribeReq(
        buf.getString()
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.SubscribeReq) {
        buf.putString(obj.dbKey)
      }
    })
    register(object : DSerializer<ddb.DMessage.SubFailedRsp>(ddb.DMessage.SubFailedRsp::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.SubFailedRsp(
        buf.getString(),
        buf.getString()
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.SubFailedRsp) {
        buf.putString(obj.dbKey)
        buf.putString(obj.cause)
      }
    })
    register(object : DService.Factory<ddb.DDBTest.TestService>(ddb.DDBTest.TestService::class.java) {
      override fun marshaller (host :DService.Host) = object : DService.Marshaller<ddb.DDBTest.TestService>(id), ddb.DDBTest.TestService {
        override fun longest (names :List<String>) :RFuture<String> {
          val result = host.promise<String>()
          host.call(DMessage.ServiceReq(host.id, svcId, 1, host.nextReqId(), listOf(names)), result)
          return result
        }
        override fun lookup (map :Map<Int,List<String>>, key :Int) :RFuture<List<String>> {
          val result = host.promise<List<String>>()
          host.call(DMessage.ServiceReq(host.id, svcId, 2, host.nextReqId(), listOf(map, key)), result)
          return result
        }
      }
      override fun dispatcher (impl :ddb.DDBTest.TestService) = object : DService.Dispatcher(id) {
        override fun dispatch (req :DMessage.ServiceReq) :RFuture<out Any> {
          return when (req.methId.toInt()) {
            1 -> impl.longest(req.args[1-1] as List<String>)
            2 -> impl.lookup(req.args[1-1] as Map<Int,List<String>>, req.args[2-1] as Int)
            else -> unknown(req.methId, impl)
          }
        }
      }
    })
    register(object : DSerializer<ddb.DMessage.PropChange>(ddb.DMessage.PropChange::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.PropChange(
        buf.getInt(),
        buf.getLong(),
        buf.getShort(),
        buf.getAny(pcol)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.PropChange) {
        buf.putInt(obj.dbId)
        buf.putLong(obj.entId)
        buf.putShort(obj.propId)
        buf.putAny(pcol, obj.value)
      }
    })
    register(object : DSerializer<ddb.DMessage.CalledRsp>(ddb.DMessage.CalledRsp::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.CalledRsp(
        buf.getInt(),
        buf.getAny(pcol)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.CalledRsp) {
        buf.putInt(obj.reqId)
        buf.putAny(pcol, obj.value)
      }
    })
    register(object : DSerializer<ddb.DMessage.EntityCreated>(ddb.DMessage.EntityCreated::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.EntityCreated(
        buf.getInt(),
        buf.getValue(pcol, ddb.DEntity::class.java)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.EntityCreated) {
        buf.putInt(obj.dbId)
        buf.putValue(pcol, ddb.DEntity::class.java, obj.entity)
      }
    })
    register(object : DEntitySerializer<ddb.DDBTest.TestEntity>(ddb.DDBTest.TestEntity::class.java) {
      override fun create (id :Long) = ddb.DDBTest.TestEntity(id)
      override val props = listOf<DEntity.Meta.Prop<*>>(
        ddb.DDBTest.TestEntity.Name,
        ddb.DDBTest.TestEntity.Age
      )
    })
    register(object : DSerializer<ddb.DMessage.SubscribedRsp>(ddb.DMessage.SubscribedRsp::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.SubscribedRsp(
        buf.getString(),
        buf.getInt(),
        buf.getCollection(pcol, ddb.DEntity::class.java),
        buf.getCollection(pcol, Class::class.java)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.SubscribedRsp) {
        buf.putString(obj.dbKey)
        buf.putInt(obj.dbId)
        buf.putCollection(pcol, ddb.DEntity::class.java, obj.entities)
        buf.putCollection(pcol, Class::class.java, obj.services)
      }
    })
  }
}
