//
// DDB - for great syncing of data between server and clients

package ddb

/**
 * Marker trait for services which are provided by a [DDB]. Service methods must only accept
 * "marshallable" parameter types (primitives, collections of primitives, and [DData] classes), and
 * must return a [RFuture] result.
 */
interface DService {

}
