//
// DDB - for great syncing of data between server and clients

package ddb

import java.nio.ByteBuffer

class TestProtocol : DProtocol(8) {
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
    register(object : DSerializer<ddb.DMessage.PropChange>(ddb.DMessage.PropChange::class.java) {
      override fun get (pcol :DProtocol, buf :ByteBuffer) = ddb.DMessage.PropChange(
        buf.getInt(),
        buf.getInt(),
        buf.getInt(),
        buf.getAny(pcol)
      )
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DMessage.PropChange) {
        buf.putInt(obj.dbId)
        buf.putInt(obj.entId)
        buf.putInt(obj.propId)
        buf.putAny(pcol, obj.value)
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
    register(object : DEntitySerializer<ddb.DDBTest.TestEntity>(ddb.DDBTest.TestEntity::class.java) {
      override fun create (buf :ByteBuffer) =
        ddb.DDBTest.TestEntity(buf.getLong())
      override fun read (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DDBTest.TestEntity) {
        obj.name = buf.getString()
        obj.age = buf.getInt()
      }
      override fun put (pcol :DProtocol, buf :ByteBuffer, obj :ddb.DDBTest.TestEntity) {
        buf.putLong(obj.id)
        buf.putString(obj.name)
        buf.putInt(obj.age)
      }
    })
    register(ddb.DMessage.SubscribedRsp.serializer)
  }
}