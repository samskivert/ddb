//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.ArrayDeque
import java.util.HashMap
import react.*
import ddb.util.*

class SourceDBImpl (key :String, id :Int, val server :DServer) : SourceDB(key, id) {

  companion object {
    /** Contains a reference to the currently executing SourceDB. */
    val current = ThreadLocal<SourceDBImpl>()
  }

  /** Subscribes `sess` to this db. */
  fun subscribe (sess :DSession) :Unit = postOp({ processSubscribe(sess) })
  /** Unsubscribes `sess` from this db. */
  fun unsubscribe (sess :DSession) :Unit = postOp({ processUnsubscribe(sess) })

  /** Processes a service call from a client session. */
  fun call (msg :DMessage.ServiceReq, session :DSession) {
    postOp({ session.runBound {
      processCall(msg, session.svcRsp(msg))
    }})
  }

  /** Processes a property change request from a client session. */
  fun change (msg :DMessage.PropChange, session :DSession) {
    postOp({ session.runBound {
      processChange(msg, session)
    }})
  }

  // TODO: bulk registry of entities loaded from persistent store
  // TODO: how to tell storage that an entity changed and should be saved, ditto deleted?

  // from BaseDB
  override fun <E : DEntity> keys (emeta :DEntity.Meta<E>) = etable(emeta).keys
  override fun <E : DEntity> entities (emeta :DEntity.Meta<E>) = etable(emeta).values
  override fun <E : DEntity> get (id :Long) :E =
    uncheckedCast<E>(requireNotNull(_entities[id]) { "No entity with id: $id" })

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
  override fun <E : DEntity> setAccessControl (emeta :DEntity.Meta<E>, ac :AccessControl<E>) {
    val old = _acls.put(emeta, ac)
    if (old != null) server.log.error("Overwrote ACL [emeta=$emeta, new=$ac, old=$old]", null)
  }
  override fun destroy (entity :DEntity) {
    if (_entities.remove(entity.id) != null) {
      etable(entity.meta).remove(entity.id)
      notifySubs(DMessage.EntityDestroyed(id, entity.id))
      entityDestroyed.emit(entity)
    }
  }
  override fun close () { server.closeDB(this) }
  override fun destroy () {
    server.closeDB(this)
    server.storage.destroyDB(key)
  }
  override fun postOp (op :() -> Unit) :Unit = synchronized(this) {
    _ops.offer(Runnable {
      try {
        current.set(this)
        op()
      } catch (t :Throwable) {
        server.log.error("Entity operation failed: $op", t)
      } finally {
        current.set(null)
        scheduleNext()
      }
    })
    if (_active == null) scheduleNext()
  }

  // from DService.Host
  override fun nextReqId () = TODO()
  override fun <T> promise () = RPromise.create<T>() // TODO: safe promise here too?
  override fun call (msg :DMessage.ServiceReq, onRsp :RPromise<out Any>) {
    postOp({ processCall(msg, onRsp.completer()) })
  }

  // from DEntity.Host
  override fun onChange (entity :DEntity, propId :Short, value :Any) {
    notifySubs(DMessage.PropChange(id, entity.id, propId, value))
  }

  /** Schedules the next operation in this context on our executor. */
  protected fun scheduleNext () :Unit = synchronized(this) {
    val active = _ops.poll()
    _active = active
    if (active != null) server.exec.execute(active)
  }

  private fun notifySubs (msg :DMessage) {
    // turn this message into a blob of bytes
    val buf = msg.flatten(server.proto)
    // send those bytes to all of our subscribers (the call to asReadOnlyBuffer creates a shallow
    // copy of the buffer for use by each subscriber, otherwise their positions/limits/etc would
    // conflict)
    for (sess in _subscribers.keys) sess.send(buf.asReadOnlyBuffer())
  }

  private fun processCall (msg :DMessage.ServiceReq, onRsp :SignalView.Listener<Try<out Any>>) {
    val disp = _dispatchers[msg.svcId]
    try {
      if (disp != null) disp.dispatch(msg).onComplete(onRsp)
      else onRsp.onEmit(Try.failure(Exception("Unknown service: $msg")))
    } catch (err :DService.ServiceException) {
      onRsp.onEmit(Try.failure(err))
    } catch (err :Throwable) {
      onRsp.onEmit(Try.failure(Exception("Error: server lost marbles")))
      throw err
    }
  }

  private fun processChange (msg :DMessage.PropChange, sess :DSession) {
    val ent = _entities[msg.entId]
    if (ent == null) server.log.error(
      "$this missing entity for prop change [msg=$msg, from=$sess]", null)
    else {
      val ac = uncheckedCast<AccessControl<DEntity>>(_acls[ent.meta] ?: DefaultAccessControl)
      if (!ac.canChange(ent, sess)) server.log.error(
        "$this rejecting illegal prop change [msg=$msg, from=$sess]", null)
      else ent._apply(msg)
    }
  }

  private fun processSubscribe (sess :DSession) {
    sess.send(DMessage.SubscribedRsp(key, id, _entities.values, _services.keys))
    // if this session closes without unsubscribing, clean it up
    val closer = sess.onClose.connect { unsubscribe(it) }
    _subscribers.put(sess, closer)
  }
  private fun processUnsubscribe (sess :DSession) {
    // close the connection we made earlier to auto-unsubscribe this session on premature closure
    _subscribers.remove(sess)?.let { it.close() }
  }

  private fun <E : DEntity> create (emeta :DEntity.Meta<E>, eid :Long, einit :(E) -> Unit) :E {
    val entity = emeta.create(eid)
    einit(entity)
    entity._init(this, server.proto.entitySerializer(entity.javaClass))
    map(emeta, entity)
    notifySubs(DMessage.EntityCreated(id, entity))
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
  private val _acls = hashMapOf<DEntity.Meta<*>,AccessControl<*>>()
  private var _nextId = 1L

  private val _services = hashMapOf<Class<*>,Short>()
  private val _dispatchers = hashMapOf<Short,DService.Dispatcher>()
  private val _subscribers = hashMapOf<DSession,Closeable>()

  private val _ops = ArrayDeque<Runnable>()
  private var _active :Runnable? = null
}
