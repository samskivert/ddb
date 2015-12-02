//
// DDB - for great syncing of data between server and clients

package ddb

/** Extends [DDB] with operations that are only applicable on the source database. The source
  * database is maintained on the server, whereas clients have proxies to that source.
  */
abstract class SourceDB (key :String, id :Int) : DDB(key, id), DService.Host, DEntity.Host {

  /** Registers `service` as the provider of `S` in this ddb. */
  abstract fun <S : DService> register (sclass :Class<S>, service :S) :Unit

  /** Creates a new entity via `emeta` assigning it a new unique id.
    * @param init a function that will be called to initialize the entity before it is announced
    * to the world via [entityCreated].
    * @return the newly created entity. */
  abstract fun <E : DEntity> create (emeta :DEntity.Meta<E>, init :(E) -> Unit) :E

  /** Destroys the entity `id` and creates a new entity via `emeta` with the same id.
    * @param init a function that will be called to initialize the entity before it is announced
    * to the world via [entityCreated].
    * @return the newly recreated entity. */
  abstract fun <E : DEntity> recreate (id :Long, emeta :DEntity.Meta<E>,
                                             init :(E) -> Unit) :E

  /** Destroys `entity`, removing it from the database. */
  abstract fun destroy (entity :DEntity)

  /** Closes this database. */
  abstract fun close () :Unit

  /** Destroys this database. */
  abstract fun destroy () :Unit

  /** Queues the supplied operation for execution on this DDB's single-threaded execution context.
    * The op will be run as soon as all other operations queued on this DDB have completed. */
  abstract fun postOp (op :() -> Unit) :Unit

  override fun <S : DService> service (sclass :Class<S>) :S =
    throw UnsupportedOperationException("Use service(sclass, recv) on the server.")
}
