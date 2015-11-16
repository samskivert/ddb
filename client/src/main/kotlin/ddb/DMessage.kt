//
// DDB - for great syncing of data between server and clients

package ddb

/** Encapsulates a message to a particular [DDB].
  * This is the base for both upstream and downstream messages.
  * @property dbId the id of the [DDB] to which this message is directed.
  */
abstract class DMessage (val dbId :Int) : DData {

  /** Communicates an entity property change, usually from server to client. */
  class PropChange (dbId :Int, val entId :Int, val propId :Int, val value :Any) : DMessage(dbId)

  /** Communiates a service request, usually from client to server. */
  abstract class ServiceReq (dbId :Int, val svcId :Int) : DMessage(dbId)
  // TODO: just stick 'args :Array<Any?> in ServiceReq?
}
