//
// DDB - for great syncing of data between server and clients

package ddb

import react.Value

/** Represents a single component of an entity, which can be read, written, and reacted. */
class DValue[T] (initVal :T) extends Value(initVal) {

  /** Allows Scala `apply` syntax as synonym for [[get]]. */
  def apply () :T = get

  def += [E] (elem :E)(implicit ev :T <:< Seq[E]) = {
    val res = (get :+ elem).asInstanceOf[T] // TODO: can we avoid this cast?
    update(res)
  }

  // TODO: reaction methods
}
