//
// DDB - for great syncing of data between server and clients

package ddb

/** An entity distributed between client and server.
  *
  * TODO: more details.
  */
abstract class DEntity (val id :Long) {

  /** Returns a reference to this entity's companion singleton. */
  abstract fun companion () :DCompanion<DEntity>

  /** Defines a reactive component of this entity. */
  fun <T> dvalue (schemaVers :Int, initVal :T) :DValue<T> {
    // TODO: registering with schema (using version), etc.
    return DValue(initVal)
  }
}
