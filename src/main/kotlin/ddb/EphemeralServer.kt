//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.RFuture
import kotlin.reflect.KClass

/**
 * A server that creates in-memory databases which are not backed by any persistent store. Useful
 * for testing, prototyping, etc.
 */
class EphemeralServer : DServer() {

  /** Returns a "local" client that returns dbs directly from this ephemeral server. */
  fun localClient () :DClient = object : DClient() {
    override fun open (id :String) = RFuture.success(openDB(id))
    override fun close (ddb :DDB) {} // noop
  }

  override fun openDB (id :String) :DDB = _dbs.get(id)

  override fun destroyDB (ddb :DDB) { _dbs.invalidate(ddb.id) }

  class EphemeralDDB (id :String) : DDB.Source(id) {

    override fun <E : DEntity.Keyed> keys (ecomp :DCompanion<E>) :Collection<Long> =
      _etable(ecomp).entities.keys

    override fun <E : DEntity.Keyed> entities (ecomp :DCompanion<E>) :Collection<E> =
      _etable(ecomp).entities.values

    @Suppress("UNCHECKED_CAST")
    override fun <E : DEntity.Singleton> singleton (ecomp :DCompanion<E>) :E =
      _singles.get(ecomp) as E

    override fun <E : DEntity.Keyed> get (ecomp :DCompanion<E>, id :Long) :E {
      val ent = _etable(ecomp).entities[id]
      return ent ?: throw IllegalArgumentException("No ${ecomp.entityName} entity with id: $id")
    }

    override fun <E : DEntity.Keyed> create (ecomp :DCompanion<E>) :E {
      val table = _etable(ecomp)
      val id = table.nextId
      table.nextId += 1
      return table.create(ecomp, id)
    }

    override fun <E : DEntity.Singleton> createSingleton (ecomp :DCompanion<E>) :E {
      throw UnsupportedOperationException("TODO")
    }

    override fun <E : DEntity.Keyed> recreate (id :Long, ecomp :DCompanion<E>) :E {
      val table = _etable(ecomp)
      table.remove(id)
      return table.create(ecomp, id)
    }

    override fun destroy (entity :DEntity.Keyed) {
      _etable(entity.companion).remove(entity.id)
    }

    override fun <S : DService> register (sclass :KClass<S>, service :S) {
      val prev = _services.put(sclass, service)
      assert(prev == null) { "Duplicate service registered for $sclass: had $prev got $service" }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <S : DService> service (sclass :KClass<S>) :S =
      (_services.get(sclass) as S?) ?:
        throw IllegalArgumentException("No provider registered for $sclass")

    private fun <E : DEntity.Keyed> _etable (ecomp :DCompanion<E>) =
      _entities.get(ecomp.entityName) as ETable<E>

    private inner class ETable<E : DEntity.Keyed> {
      var nextId = 1L
      val entities = HashMap<Long, E>()

      fun create (ecomp :DCompanion<E>, id :Long) :E {
        val entity = ecomp.create(id)
        entities.put(id, entity)
        entityCreated.emit(entity)
        return entity
      }

      fun remove (id :Long) {
        val removed = entities.remove(id)
        if (removed != null) entityDestroyed.emit(removed)
      }
    }

    private val _entities = Util.cacheMap<String,ETable<DEntity.Keyed>> {
      comp -> ETable<DEntity.Keyed>() }
    private val _singles = Util.cacheMap<DCompanion<DEntity>,DEntity> { it.create(1L) }
    private val _services = HashMap<KClass<*>,DService>()
  }

  private val _dbs = Util.cacheMap<String,EphemeralDDB> { id -> EphemeralDDB(id) }
}
