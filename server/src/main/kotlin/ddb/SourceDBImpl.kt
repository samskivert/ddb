//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.ArrayDeque
import java.util.HashMap
import react.SignalView
import react.Try

abstract class SourceDBImpl (key :String, id :Int, val server :DServer) : SourceDB(key, id) {

  companion object {
    /** Contains a reference to the currently executing SourceDB. */
    val current = ThreadLocal<SourceDBImpl>()
  }

  /** Subscribes `sess` to this db. */
  fun subscribe (sess :DSession) :Unit = postOp({ processSubscribe(sess) })
  /** Unsubscribes `sess` from this db. */
  fun unsubscribe (sess :DSession) :Unit = postOp({ processUnsubscribe(sess) })

  /** Queues the supplied operation for execution on this DDB's single-threaded execution context.
    * The op will be run as soon as all other operations queued on this DDB have completed. */
  fun postOp (op :() -> Unit) :Unit = synchronized(this) {
    _ops.offer(Runnable {
      try {
        current.set(this)
        op()
      } catch (t :Throwable) {
        server.onErr.report("Entity operation failed: $op", t)
      } finally {
        current.set(null)
        scheduleNext()
      }
    })
    if (_active == null) scheduleNext()
  }

  // TODO: bulk registry of entities loaded from persistent store
  // TODO: how to tell storage that an entity changed and should be saved, ditto deleted?

  // from BaseDB
  override fun <E : DEntity> keys (emeta :DEntity.Meta<E>) = etable(emeta).keys
  override fun <E : DEntity> entities (emeta :DEntity.Meta<E>) = etable(emeta).values
  override fun <E : DEntity> get (id :Long) :E = uncheckedCast<E>(_entities[id] ?:
    throw IllegalArgumentException("No entity with id: $id"))

  // from SourceDB
  override fun <S : DService> register (sclass :Class<S>, service :S) {
    assert (!_services.containsKey(sclass)) { "Duplicate service registered for $sclass: $service" }
    val disp = server.proto.factory(sclass).dispatcher(service)
    _services.put(sclass, disp.svcId)
    _dispatchers.put(disp.svcId, disp)
  }
  override fun <E : DEntity> create (emeta :DEntity.Meta<E>, init :(E) -> Unit) :E {
    val id = _nextId ; _nextId += 1
    return create(emeta, id, init)
  }
  override fun <E : DEntity> recreate (id :Long, emeta :DEntity.Meta<E>, init :(E) -> Unit) :E {
    _entities[id].ifExists { destroy(it) }
    return create(emeta, id, init)
  }
  override fun destroy (entity :DEntity) {
    if (_entities.remove(entity.id) != null) {
      etable(entity.meta).remove(entity.id)
      entityDestroyed.emit(entity)
    }
  }
  override fun close () { server.closeDB(this) }
  override fun destroy () {
    server.closeDB(this)
    server.storage.destroyDB(key)
  }

  // from DService.Host
  override fun call (msg :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<Any>>) {
    postOp({ processCall(msg, onRsp) })
  }

  // from DEntity.Host
  override fun onChange (entity :DEntity, propId :Short, value :Any) {
    // turn this message into a blob of bytes
    val buf = DMessage.PropChange(id, entity.id, propId, value).flatten(server.proto)
    // send those bytes to all of our subscribers
    for (sess in _subscribers) sess.send(buf)
  }

  /** Schedules the next operation in this context on our executor. */
  protected fun scheduleNext () :Unit = synchronized(this) {
    val active = _ops.poll()
    _active = active
    if (active != null) server.exec.execute(active)
  }

  private fun processCall (msg :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<Any>>) {
    val disp = _dispatchers[msg.svcId]
    if (disp != null) disp.dispatch(msg).onComplete(onRsp)
    else onRsp.onEmit(Try.failure(Exception("Unknown service: $msg")))
  }

  private fun processSubscribe (sess :DSession) {
    sess.send(DMessage.SubscribedRsp(key, id, _entities.values, _services.keys))
    _subscribers += sess
  }
  private fun processUnsubscribe (sess :DSession) {
    _subscribers -= sess
  }

  private fun <E : DEntity> create (emeta :DEntity.Meta<E>, id :Long, init :(E) -> Unit) :E {
    val entity = emeta.create(id)
    init(entity)
    entity._init(this, server.proto.entitySerializer(entity.javaClass))
    map(emeta, entity)
    entityCreated.emit(entity)
    return entity
  }

  private fun <E : DEntity> map (emeta :DEntity.Meta<E>, entity :E) {
    _entities[entity.id] = entity
    etable(emeta)[entity.id] = entity
  }

  private fun <E : DEntity> etable (emeta :DEntity.Meta<E>) :MutableMap<Long,E> {
    val table = _byType.getOrPut(emeta.entityName) { hashMapOf<Long,DEntity>() }
    return uncheckedCast<MutableMap<Long,E>>(table)
  }

  private val _entities = hashMapOf<Long,DEntity>()
  private val _byType = hashMapOf<String,HashMap<Long,DEntity>>()
  private var _nextId = 1L

  private val _services = hashMapOf<Class<*>,Int>()
  private val _dispatchers = hashMapOf<Int,DService.Dispatcher>()
  private val _subscribers = arrayListOf<DSession>()

  private val _ops = ArrayDeque<Runnable>()
  private var _active :Runnable? = null
}
