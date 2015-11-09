
//
// DDB - for great syncing of data between server and clients

package ddb

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache

object Util {

  /** Creates a [LoadingCache] configured to fill empty mappings via `filler`. */
  fun <K,V> cacheMap (filler :(K) -> V) :LoadingCache<K, V> =
    CacheBuilder.newBuilder().build<K,V>(object : CacheLoader<K, V>() {
      override fun load(key: K) = filler(key)
    })
}
