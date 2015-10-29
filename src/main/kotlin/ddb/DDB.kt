//
// DDB - for great syncing of data between server and clients

package ddb

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import java.util.*

open class DDB {

  /** A signal emitted when an entity is created. */
  val entityCreated = DSignal<DEntity>()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed = DSignal<DEntity>()

  /** Returns the keys for all entities of type [E]. */
  fun <E : DEntity> keys (ecomp :DCompanion<E>) :Iterable<Long> = _etable(ecomp).entities.keys

  /** Returns all entities of type [E]. */
  fun <E : DEntity> entities (ecomp :DCompanion<E>) :Iterable<E> = _etable(ecomp).entities.values

  @Suppress("UNCHECKED_CAST")
  fun <E : DEntity> singleton (ecomp :DCompanion<E>) :E = _singles.get(ecomp) as E

  fun <E : DEntity> get (ecomp :DCompanion<E>, id :Long) :E {
    val ent = _etable(ecomp).entities[id]
    return ent ?: throw IllegalArgumentException("No $ecomp entity with id: $id")
  }

  /** Creates a new entity via `ecomp` assigning it a new unique id. */
  fun <E : DEntity> create (ecomp :DCompanion<E>) :E {
    val table = _etable(ecomp)
    val id = table.nextId
    table.nextId += 1
    return table.create(ecomp, id)
  }

  /** Destroys the entity `id` and creates a new entity via `ecomp` with the same id. */
  fun <E : DEntity> recreate (id :Long, ecomp :DCompanion<E>) :E {
    val table = _etable(ecomp)
    table.remove(id)
    return table.create(ecomp, id)
  }

  fun destroy (entity :DEntity) {
    _etable(entity.companion()).remove(entity.id)
  }

  private fun <E : DEntity> _etable (ecomp :DCompanion<E>) =
      _entities.get(ecomp.entityName()) as ETable<E>

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

  private val _entities = cacheMap<String,ETable<DEntity>> { comp -> ETable<DEntity>() }
  private val _singles = cacheMap<DCompanion<DEntity>,DEntity> { it.create(1L) }

  /** Creates a [LoadingCache] configured to fill empty mappings via `filler`. */
  private fun <K,V> cacheMap (filler :(K) -> V) :LoadingCache<K, V> =
    CacheBuilder.newBuilder().build<K,V>(object : CacheLoader<K, V>() {
      override fun load(key: K) = filler(key)
    })
}
