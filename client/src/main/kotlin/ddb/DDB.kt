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
abstract class BaseDB (val key :String, val id :Int) {

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
}

/**
 * Extends [BaseDB] with bits only available on the client, which is a proxy of the source database
 * maintained on the server.
 */
abstract class DDB (key :String, id :Int) : BaseDB(key, id) {

  /** Resolves and returns the service with class `sclass`.
    * @throws IllegalArgumentException if no provider for `sclass` is registered with this ddb. */
  abstract fun <S : DService> service (sclass :Class<S>) :S
}
