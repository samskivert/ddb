//
// DDB - for great syncing of data between server and clients

package ddb

/** An entity distributed between client and server.
  *
  * TODO: more details.
  */
abstract class DEntity (val id :Long) {

  /** Defines a reactive component of this entity. */
  def dvalue[T] (schemaVers :Int, initVal :T) :DValue[T] = {
    // TODO: registering with schema (using version), etc.
    new DValue(initVal)
  }
}
