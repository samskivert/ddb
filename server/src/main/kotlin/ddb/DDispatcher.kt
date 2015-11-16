//
// DDB - for great syncing of data between server and clients

package ddb

/**
 * Handles the dispatching of [DService] requests to their implementations, using the appropriate
 * [DContext] to execute the processing code.
 */
class DDispatcher<S :DService> (val id :Int, val ctx :DExecutor.Context, val impl :S) {

}
