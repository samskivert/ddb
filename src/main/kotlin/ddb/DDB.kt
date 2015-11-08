//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.Signal

/**
 * Maintains a database of reactive entities.
 */
abstract class DDB (val id :String) {

  /** A signal emitted when an entity is created. */
  val entityCreated = Signal.create<DEntity>()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed = Signal.create<DEntity>()

  /** Returns the keys for all entities of type [E]. */
  abstract fun <E : DEntity> keys (ecomp :DCompanion<E>) :Collection<Long>

  /** Returns all entities of type [E]. */
  abstract fun <E : DEntity> entities (ecomp :DCompanion<E>) :Collection<E>

  /** Returns the singleton entity of the specified type, creating it if needed. */
  abstract fun <E : DEntity> singleton (ecomp :DCompanion<E>) :E

  /** Returns the entity of the specified type with id `id`.
    * @throws IllegalArgumentException if no entity exists with that id. */
  abstract fun <E : DEntity> get (ecomp :DCompanion<E>, id :Long) :E

  /** Creates a new entity via `ecomp` assigning it a new unique id. */
  abstract fun <E : DEntity> create (ecomp :DCompanion<E>) :E

  /** Destroys the entity `id` and creates a new entity via `ecomp` with the same id. */
  abstract fun <E : DEntity> recreate (id :Long, ecomp :DCompanion<E>) :E

  /** Destroys `entity`, removing it from the database. */
  abstract fun destroy (entity :DEntity)
}
