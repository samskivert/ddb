//
// DDB - for great syncing of data between server and clients

package ddb

/** Represents a single component of an entity, which can be read, written, and reacted. */
class DValue[T] (initVal :T) {

  def get :T = _current

  def apply () :T = get

  def update (value :T) = {
    // TODO: reactive stuffs
    _current = value
  }

  def += [E] (elem :E)(implicit ev :T <:< Seq[E]) = {
    val res = (get :+ elem).asInstanceOf[T] // TODO: can we avoid this cast?
    update(res)
  }

  // TODO: reaction methods

  private[this] var _current = initVal
}
