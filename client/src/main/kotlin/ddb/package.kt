//
// DDB - for great syncing of data between server and clients

package ddb

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
internal inline fun <T> uncheckedCast (value :Any) :T = value as T

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
internal inline fun <T : Any?> uncheckedNullCast (value :Any?) :T = value as T

/** Calls `f` with `this` iff `this` is non-null. */
internal inline fun <T : Any> T?.ifExists (f: (T) -> Unit) :Unit { if (this != null) f(this) }
