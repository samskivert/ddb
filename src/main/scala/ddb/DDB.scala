//
// DDB - for great syncing of data between server and clients

package ddb

import com.google.common.cache._
import java.util.HashMap
import react.Signal
import scala.collection.mutable.{Map => MMap}

class DDB {
  import scala.collection.JavaConversions._

  /** A signal emitted when an entity is created. */
  val entityCreated :Signal[DEntity] = Signal.create()

  /** A signal emitted when an entity is destroyed. */
  val entityDestroyed :Signal[DEntity] = Signal.create()

  def keys[E <: DEntity] (ecomp :DCompanion[E]) :Iterable[Long] =
    _etable(ecomp).entities.keySet

  def entities[E <: DEntity] (ecomp :DCompanion[E]) :Iterable[E] =
    _etable(ecomp).entities.values.asInstanceOf[Iterable[E]]

  def singleton[E <: DEntity] (ecomp :DCompanion[E]) :E =
    _singles.get(ecomp).asInstanceOf[E]

  def get[E <: DEntity] (ecomp :DCompanion[E])(id :Long) :E =
    _etable(ecomp).entities.get(id) match {
      case null => throw new IllegalArgumentException(s"No $ecomp entity with id: $id")
      case ent  => ent.asInstanceOf[E]
    }

  /** Creates a new entity via `ecomp` assigning it a new unique id. */
  def create[E <: DEntity] (ecomp :DCompanion[E]) :E = {
    val table = _etable(ecomp)
    val id = table.nextId
    table.nextId += 1
    table.create(ecomp, id)
  }

  /** Destroys the entity `id` and creates a new entity via `ecomp` with the same id. */
  def recreate[E <: DEntity] (id :Long, ecomp :DCompanion[E]) :E = {
    val table = _etable(ecomp)
    table.remove(id)
    table.create(ecomp, id)
  }

  def destroy (entity :DEntity) :Unit = {
    _etable(entity.companion).remove(entity.id)
  }

  private def _etable[E <: DEntity] (ecomp :DCompanion[E]) = _entities.get(ecomp.entityName)

  private class ETable {
    var nextId = 1L
    val entities = new HashMap[Long,DEntity]()

    def create[E <: DEntity] (ecomp :DCompanion[E], id :Long) :E = {
      val entity = ecomp.create(id)
      entities.put(id, entity)
      entityCreated.emit(entity)
      entity
    }

    def remove (id :Long) {
      val removed = entities.remove(id)
      if (removed != null) entityDestroyed.emit(removed)
    }
  }

  private val _entities = cacheMap[String,ETable](comp => new ETable)
  private val _singles = cacheMap[DCompanion[_],DEntity](
    comp => comp.asInstanceOf[DCompanion[DEntity]].create(1L))

  /** Creates a [[Cache]] configured to fill empty mappings via `filler`. */
  private def cacheMap[K,V] (filler :K => V) :LoadingCache[K,V] =
    CacheBuilder.newBuilder().asInstanceOf[CacheBuilder[Any,Any]].build(new CacheLoader[K,V]() {
      override def load (key :K) = filler(key)
    })
}
