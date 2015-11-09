//
// DDB - for great syncing of data between server and clients

package ddb

import java.util.*
import react.Signal

/**
 * Maintains a database of reactive entities.
 */
abstract class DDB (val id :String) {

  /** Extends [DDB] with operations that are only applicable on the source database. The source
    * database is maintained on the server, whereas clients have proxies to that source. */
  abstract class Source (id :String) : DDB(id) {

    /** Creates a new keyed entity via `emeta` assigning it a new unique id.
      * @param init a function that will be called to initialize the entity before it is announced
      * to the world via [entityCreated].
      * @return the newly created entity. */
    abstract fun <E : DEntity.Keyed> create (emeta :DEntity.Keyed.Meta<E>, init :(E) -> Unit) :E

    /** Destroys the entity `id` and creates a new entity via `emeta` with the same id.
      * @param init a function that will be called to initialize the entity before it is announced
      * to the world via [entityCreated].
      * @return the newly recreated entity. */
    abstract fun <E : DEntity.Keyed> recreate (id :Long, emeta :DEntity.Keyed.Meta<E>,
                                               init :(E) -> Unit) :E

    /** Destroys `entity`, removing it from the database. */
    abstract fun destroy (entity :DEntity.Keyed)

    /** Registers `service` as the provider of `S` in this ddb. */
    abstract fun <S : DService> register (sclass :Class<S>, service :S) :Unit
  }

  /** A signal emitted when an entity is created. */
  val entityCreated = Signal.create<DEntity>()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed = Signal.create<DEntity>()

  /** Returns the keys for all entities of type [E]. */
  abstract fun <E : DEntity.Keyed> keys (emeta :DEntity.Keyed.Meta<E>) :Collection<Long>

  /** Returns all entities of type [E]. */
  abstract fun <E : DEntity.Keyed> entities (emeta :DEntity.Keyed.Meta<E>) :Collection<E>

  /** Returns the singleton entity of the specified type, creating it if necessary. */
  abstract fun <E : DEntity.Singleton> get (emeta :DEntity.Singleton.Meta<E>) :E

  /** Returns the entity of the specified type with id `id`.
    * @throws IllegalArgumentException if no entity exists with that id. */
  abstract fun <E : DEntity.Keyed> get (emeta :DEntity.Keyed.Meta<E>, id :Long) :E

  /** Resolves and returns the service with class `sclass`.
    * @throws IllegalArgumentException if no provider for `sclass` is registered with this ddb. */
  abstract fun <S : DService> service (sclass :Class<S>) :S
}
