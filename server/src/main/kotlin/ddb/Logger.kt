//
// DDB - for great syncing of data between server and clients

package ddb

/** Used by various internals to report info & errors. */
interface Logger {

  /** Reports an informational message. */
  fun info (msg :String) :Unit

  /** Reports an internal error. The caller will have recovered from this error already, we expect
    * this method to log or report it in some way. */
  fun error (msg :String, err :Throwable?) :Unit
}
