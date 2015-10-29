//
// DDB - for great syncing of data between server and clients

package ddb

import react.*

/** Represents a single component of an entity, which can be read, written, and reacted. */
class DValue<T> (initVal :T) : Value<T>(initVal) {

  /** Allows Kotlin `apply` syntax as synonym for [get()]. */
  operator fun invoke () :T = get()
}
