//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.RFuture

/**
 * A server that creates in-memory databases which are not backed by any persistent store. Useful
 * for testing, prototyping, etc.
 */
class EphemeralServer : DServer() {

  /** Returns a "local" client that returns dbs directly from this ephemeral server. */
  fun localClient () :DClient = object : DClient() {
    override fun open (id :String) = RFuture.success<DDB>(openDB(id))
    override fun open (ids :List<String>) = RFuture.success<List<DDB>>(ids.map { openDB(it) })
    override fun close (ddb :DDB) {} // noop
  }

  override fun openDB (id :String) :DDB.Source = _dbs.get(id)

  override fun destroyDB (ddb :DDB.Source) { _dbs.invalidate(ddb.id) }

  class EphemeralDDB (id :String) : DDB.Source(id) {

    override fun <E : DEntity.Keyed> keys (emeta :DEntity.Keyed.Meta<E>) :Collection<Long> =
      _etable(emeta).entities.keys

    override fun <E : DEntity.Keyed> entities (emeta :DEntity.Keyed.Meta<E>) :Collection<E> =
      _etable(emeta).entities.values

    @Suppress("UNCHECKED_CAST")
    override fun <E : DEntity.Singleton> get (emeta :DEntity.Singleton.Meta<E>) :E =
      _singles.get(emeta) as E

    override fun <E : DEntity.Keyed> get (emeta :DEntity.Keyed.Meta<E>, id :Long) :E {
      val ent = _etable(emeta).entities[id]
      return ent ?: throw IllegalArgumentException("No ${emeta.entityName} entity with id: $id")
    }

    override fun <E : DEntity.Keyed> create (emeta :DEntity.Keyed.Meta<E>, init :(E) -> Unit) :E {
      val table = _etable(emeta)
      val id = table.nextId
      table.nextId += 1
      return table.create(emeta, id, init)
    }

    override fun <E : DEntity.Keyed> recreate (id :Long, emeta :DEntity.Keyed.Meta<E>,
                                               init :(E) -> Unit) :E {
      val table = _etable(emeta)
      table.remove(id)
      return table.create(emeta, id, init)
    }

    override fun destroy (entity :DEntity.Keyed) {
      _etable(entity.meta).remove(entity.id)
    }

    override fun <S : DService> register (sclass :Class<S>, service :S) {
      val prev = _services.put(sclass, service)
      assert(prev == null) { "Duplicate service registered for $sclass: had $prev got $service" }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <S : DService> service (sclass :Class<S>) :S = (_services.get(sclass) as S?) ?:
      throw IllegalArgumentException("No provider registered for $sclass")

    private fun <E : DEntity.Keyed> _etable (emeta :DEntity.Keyed.Meta<E>) =
      _entities.get(emeta.entityName) as ETable<E>

    private inner class ETable<E : DEntity.Keyed> {
      var nextId = 1L
      val entities = HashMap<Long, E>()

      fun create (emeta :DEntity.Keyed.Meta<E>, id :Long, init :(E) -> Unit) :E {
        val entity = emeta.create(id)
        entities.put(id, entity)
        init(entity)
        entityCreated.emit(entity)
        return entity
      }

      fun remove (id :Long) {
        val removed = entities.remove(id)
        if (removed != null) entityDestroyed.emit(removed)
      }
    }

    private val _entities = Util.cacheMap<String,ETable<DEntity.Keyed>> {
      meta -> ETable<DEntity.Keyed>() }
    private val _singles = Util.cacheMap<DEntity.Singleton.Meta<*>,DEntity.Singleton> {
      it.create() }
    private val _services = HashMap<Class<*>,DService>()
  }

  private val _dbs = Util.cacheMap<String,EphemeralDDB> { id -> EphemeralDDB(id) }
}
