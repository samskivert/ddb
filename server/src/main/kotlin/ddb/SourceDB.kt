//
// DDB - for great syncing of data between server and clients

package ddb

/** Extends [DDB] with operations that are only applicable on the source database. The source
  * database is maintained on the server, whereas clients have proxies to that source.
  */
abstract class SourceDB (key :String, id :Int) : BaseDB(key, id), DMessage.Source {

  /** Registers `service` as the provider of `S` in this ddb. */
  abstract fun <S : DService> register (sclass :Class<S>, service :S) :Unit

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

  /** Returns all entities in this DDB in a format suitable for blasting to a client. */
  abstract fun allEntities () :Collection<Collection<DEntity>>

  /** Closes this database. */
  abstract fun close () :Unit

  /** Destroys this database. */
  abstract fun destroy () :Unit
}
