//
// DDB - for great syncing of data between server and clients

package ddb.util

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
inline fun <T> uncheckedCast (value :Any) :T = value as T

/** Calls `f` with `this` iff `this` is non-null. */
inline fun <T : Any> T?.ifExists (f: (T) -> Unit) :Unit { if (this != null) f(this) }

/** Creates a [LoadingCache] configured to fill empty mappings via `filler`. */
fun <K,V> cacheMap (filler :(K) -> V) :LoadingCache<K, V> =
  CacheBuilder.newBuilder().build<K,V>(object : CacheLoader<K, V>() {
    override fun load(key: K) = filler(key)
  })
