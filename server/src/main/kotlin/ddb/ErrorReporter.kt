//
// DDB - for great syncing of data between server and clients

package ddb

/** Used by various internals to report errors. */
interface ErrorReporter {

  /** Reports an internal error. The caller will have recovered from this error already, we expect
    * this method to log or report it in some way. */
  fun report (msg :String, err :Throwable?) :Unit
}
