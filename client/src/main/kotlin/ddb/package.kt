//
// DDB - for great syncing of data between server and clients

package ddb

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T> uncheckedCast (value :Any) :T = value as T

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T : Any?> uncheckedNullCast (value :Any?) :T = value as T
