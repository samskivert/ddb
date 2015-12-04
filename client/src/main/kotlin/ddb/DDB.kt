//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.Signal

/**
 * Maintains a database of reactive entities.
 *
 * @property key a string key that uniquely identifies this database across time.
 * @property id an ephemeral but unique id assigned to this database. Used internally for routing
 * messages between client and server.
 */
abstract class DDB (val key :String, val id :Int) {

  /** A signal emitted when an entity is created. */
  val entityCreated = Signal.create<DEntity>()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed = Signal.create<DEntity>()

  /** Returns the keys for all entities of type [E]. */
  abstract fun <E : DEntity> keys (emeta :DEntity.Meta<E>) :Collection<Long>

  /** Returns all entities of type [E]. */
  abstract fun <E : DEntity> entities (emeta :DEntity.Meta<E>) :Collection<E>

  /** Returns the entity of the specified type with id `id`.
    * @throws IllegalArgumentException if no entity exists with that id. */
  abstract fun <E : DEntity> get (id :Long) :E

  /** Returns the singleton entity of the specified type.
    * @throws IllegalArgumentException if no singleton entity exists for `emeta`. */
  fun <E : DEntity> get (emeta :DEntity.Meta<E>) :E {
    val iter = entities(emeta).iterator()
    require(iter.hasNext()) { "No singleton registered for $emeta" }
    return iter.next()
  }

  /** Resolves and returns the service with class `sclass`.
    * @throws IllegalArgumentException if no provider for `sclass` is registered with this ddb. */
  abstract fun <S : DService> service (sclass :Class<S>) :S

  override fun toString () = "DDB[key=$key, id=$id]"
}
