//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.Signal
import kotlin.reflect.KClass

/**
 * Maintains a database of reactive entities.
 */
abstract class DDB (val id :String) {

  /** Extends [DDB] with operations that are only applicable on the source database. The source
    * database is maintained on the server, whereas clients have proxies to that source. */
  abstract class Source (id :String) : DDB(id) {

    /** Creates a new keyed entity via `ecomp` assigning it a new unique id.
      * @return the newly created entity. */
    abstract fun <E : DEntity.Keyed> create (ecomp :DCompanion<E>) :E

    /** Destroys the entity `id` and creates a new entity via `ecomp` with the same id. */
    abstract fun <E : DEntity.Keyed> recreate (id :Long, ecomp :DCompanion<E>) :E

    /** Creates a the singleton entity for `ecomp`.
      * @return the newly created entity. */
    abstract fun <E : DEntity.Singleton> createSingleton (ecomp :DCompanion<E>) :E

    /** Destroys `entity`, removing it from the database. */
    abstract fun destroy (entity :DEntity.Keyed)

    /** Registers `service` as the provider of `S` in this ddb. */
    abstract fun <S : DService> register (sclass :KClass<S>, service :S) :Unit
  }

  /** A signal emitted when an entity is created. */
  val entityCreated = Signal.create<DEntity>()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed = Signal.create<DEntity>()

  /** Returns the keys for all entities of type [E]. */
  abstract fun <E : DEntity.Keyed> keys (ecomp :DCompanion<E>) :Collection<Long>

  /** Returns all entities of type [E]. */
  abstract fun <E : DEntity.Keyed> entities (ecomp :DCompanion<E>) :Collection<E>

  /** Returns the singleton entity of the specified type.
    * @throws IllegalArgumentException if no singleton exists for `ecomp`. */
  abstract fun <E : DEntity.Singleton> singleton (ecomp :DCompanion<E>) :E

  /** Returns the entity of the specified type with id `id`.
    * @throws IllegalArgumentException if no entity exists with that id. */
  abstract fun <E : DEntity.Keyed> get (ecomp :DCompanion<E>, id :Long) :E

  /** Resolves and returns the service with class `sclass`.
    * @throws IllegalArgumentException if no provider for `sclass` is registered with this ddb. */
  abstract fun <S : DService> service (sclass :KClass<S>) :S
}
