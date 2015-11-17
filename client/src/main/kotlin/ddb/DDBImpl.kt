//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.HashMap
import react.SignalView
import react.Try

class DDBImpl (val client :DClient, rsp :DMessage.SubscribedRsp) : DDB(rsp.dbKey, rsp.dbId),
  DMessage.Source {

  private val _keyedEnts = hashMapOf<String,HashMap<Long,DEntity.Keyed>>()
  private val _singleEnts = hashMapOf<String,DEntity.Singleton>()
  private val _services = hashMapOf<Class<*>,DService>()

  init {
    // map all of the keyed and singleton entities
    for (kents in rsp.keyeds) {
      val table = hashMapOf<Long,DEntity.Keyed>()
      for (kent in kents) table[kent.id] = kent
      val ename = kents.iterator().next().meta.entityName
      _keyedEnts[ename] = table
    }
    for (sent in rsp.singles) _singleEnts[sent.meta.entityName] = sent
    // create marshallers for our services
    for (sclass in rsp.services) _services[sclass] = client.proto.factory(
      uncheckedCast<Class<DService>>(sclass)).marshaller(this)
  }

  // from DDB
  override fun <E : DEntity.Keyed> keys (emeta :DEntity.Keyed.Meta<E>) = etable(emeta).keys
  override fun <E : DEntity.Keyed> entities (emeta :DEntity.Keyed.Meta<E>) = etable(emeta).values
  override fun <E : DEntity.Singleton> get (emeta :DEntity.Singleton.Meta<E>) =
    uncheckedCast<E>(_singleEnts[emeta.entityName] ?:
      throw IllegalArgumentException("No singleton registered for $emeta"))
  override fun <E : DEntity.Keyed> get (emeta :DEntity.Keyed.Meta<E>, id :Long) =
    uncheckedCast<E>(etable(emeta)[id] ?:
      throw IllegalArgumentException("No entity with key $id for $emeta"))
  override fun <S : DService> service (sclass :Class<S>) = uncheckedCast<S>(_services[sclass] ?:
    throw IllegalArgumentException("No service registered for $sclass"))

  // from DMessage.Source
  override fun call (msg :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<Any>>) =
    client.sendCall(msg, onRsp)

  private fun <E : DEntity.Keyed> etable (emeta :DEntity.Keyed.Meta<E>) :Map<Long,E> {
    val table = _keyedEnts.getOrPut(emeta.entityName) { hashMapOf<Long,DEntity.Keyed>() }
    return uncheckedCast<Map<Long,E>>(table)
  }
}
