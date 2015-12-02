//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.HashMap
import react.RPromise
import ddb.util.*

class DDBImpl (val client :DClient, rsp :DMessage.SubscribedRsp) : DDB(rsp.dbKey, rsp.dbId),
  DService.Host, DEntity.Host {

  // from DDB
  override fun <E : DEntity> keys (emeta :DEntity.Meta<E>) = etable(emeta).keys
  override fun <E : DEntity> entities (emeta :DEntity.Meta<E>) = etable(emeta).values
  override fun <E : DEntity> get (id :Long) =
    uncheckedCast<E>(requireNotNull(_entities[id]) { "No entity with key $id" })
  override fun <S : DService> service (sclass :Class<S>) =
    uncheckedCast<S>(requireNotNull(_services[sclass]) { "No service registered for $sclass" })

  // from DService.Host
  override fun nextReqId () = client.nextReqId()
  override fun <T> promise () = client.safePromise<T>()
  override fun call (msg :DMessage.ServiceReq, onRsp :RPromise<out Any>) =
    client.sendCall(msg, onRsp)

  // from DEntity.Host
  override fun onChange (entity :DEntity, propId :Short, value :Any) {
    // TODO: we can't freak out here because this is also triggered when applying PropChange from
    // server; maybe we want to set a flag? or come up with some sneaky way to change the value
    // locally that circumvents the normal onChange mechanism so that we can still freak out if the
    // user accidentally tries to change a property on the client?

    // throw UnsupportedOperationException("Cannot change entities directly on client " +
    //   "[ent=$entity, pid=$propId, val=$value]")
  }

  internal fun apply (msg :DMessage.EntityCreated) {
    register(msg.entity)
    entityCreated.emit(msg.entity)
  }
  internal fun apply (msg :DMessage.EntityDestroyed) {
    val entity = _entities.remove(msg.entId)
    if (entity == null) client.reportError("$this got destroyed for unknown entity: $msg", null)
    else entityDestroyed.emit(entity)
  }
  internal fun apply (msg :DMessage.PropChange) {
    val ent = _entities[msg.entId]
    if (ent != null) ent.apply(msg)
    else client.reportError("$this missing entity for $msg", null)
  }

  private fun <E : DEntity> etable (emeta :DEntity.Meta<E>) :MutableMap<Long,E> {
    val table = _byType.getOrPut(emeta.entityName) { hashMapOf<Long,DEntity>() }
    return uncheckedCast<MutableMap<Long,E>>(table)
  }

  private fun register (ent :DEntity) {
    _entities[ent.id] = ent
    etable(ent.meta)[ent.id] = ent
    ent._init(this, client.proto.entitySerializer(ent.javaClass))
  }

  private val _entities = hashMapOf<Long,DEntity>()
  private val _byType = hashMapOf<String,HashMap<Long,DEntity>>()
  private val _services = hashMapOf<Class<*>,DService>()

  init {
    for (ent in rsp.entities) register(ent)
    for (sclass in rsp.services) _services[sclass] = client.proto.factory(
      uncheckedCast<Class<DService>>(sclass)).marshaller(this)
  }
}
