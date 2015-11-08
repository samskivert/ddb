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
    override fun open (id :String) = RFuture.success(openDB(id))
    override fun close (ddb :DDB) {} // noop
  }

  override fun openDB (id :String) :DDB = _dbs.get(id)

  override fun destroyDB (ddb :DDB) { _dbs.invalidate(ddb.id) }

  class EphemeralDDB (id :String) : DDB(id) {

    override fun <E : DEntity> keys (ecomp :DCompanion<E>) :Collection<Long> =
      _etable(ecomp).entities.keys

    override fun <E : DEntity> entities (ecomp :DCompanion<E>) :Collection<E> =
      _etable(ecomp).entities.values

    @Suppress("UNCHECKED_CAST")
    override fun <E : DEntity> singleton (ecomp :DCompanion<E>) :E = _singles.get(ecomp) as E

    override fun <E : DEntity> get (ecomp :DCompanion<E>, id :Long) :E {
      val ent = _etable(ecomp).entities[id]
      return ent ?: throw IllegalArgumentException("No ${ecomp.entityName} entity with id: $id")
    }

    override fun <E : DEntity> create (ecomp :DCompanion<E>) :E {
      val table = _etable(ecomp)
      val id = table.nextId
      table.nextId += 1
      return table.create(ecomp, id)
    }

    override fun <E : DEntity> recreate (id :Long, ecomp :DCompanion<E>) :E {
      val table = _etable(ecomp)
      table.remove(id)
      return table.create(ecomp, id)
    }

    override fun destroy (entity :DEntity) {
      _etable(entity.companion).remove(entity.id)
    }

    private fun <E : DEntity> _etable (ecomp :DCompanion<E>) =
      _entities.get(ecomp.entityName) as ETable<E>

    private inner class ETable<E : DEntity> {
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

    private val _entities = Util.cacheMap<String,ETable<DEntity>> { comp -> ETable<DEntity>() }
    private val _singles = Util.cacheMap<DCompanion<DEntity>,DEntity> { it.create(1L) }
  }

  private val _dbs = Util.cacheMap<String,EphemeralDDB> { id -> EphemeralDDB(id) }
}
