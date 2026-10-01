package com.muazdev.hijricalendar.widget.glance

/**
 * A minimal least-recently-used map.
 *
 * **Not `android.util.LruCache`**, deliberately. That class is a stub in the `android.jar` that
 * local unit tests compile against — every method returns a default and does nothing — so a cache
 * built on it cannot be tested off-device at all, and the property that matters here (that the map
 * stays *bounded*) is exactly the one you cannot assert against a stub. This version is ordinary
 * JVM code: real behaviour under `testDebugUnitTest`, and one fewer Android dependency in a module
 * that also has to work on Kotlin/Native-shaped test hosts.
 *
 * Access-ordered, so a frequently-read entry is not evicted by a burst of one-off renders — which
 * is the case that matters, since one widget's month is re-read on every recompose while a preview's
 * is written once.
 *
 * **Null values are not cached.** `V` is non-null and callers skip the put for a null projection,
 * which is the right call: a null means the Hijri math could not resolve the month for these
 * options — an out-of-range date, not a transient — so recomputing it is cheap and never changes
 * the answer, and a map that could not tell "absent" from "null" would cache the absence anyway.
 *
 * Thread-safety: every operation is synchronized. Callers must still build a value *outside* the
 * cache and put it afterwards — see `HijriWidgetRenderCache`, where the alternative is holding this
 * monitor across the Hijri math and serialising every widget render in the process.
 */
internal class LruCache<K : Any, V : Any>(private val maxSize: Int) {

    init {
        require(maxSize > 0) { "maxSize must be positive, was $maxSize" }
    }

    // Access order = true is what makes it LRU rather than insertion-ordered.
    private val entries = object : LinkedHashMap<K, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
            size > maxSize
    }

    /** The value for [key], marking it most-recently-used. Null when absent. */
    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        entries[key] = value
    }

    /** Current entry count. Exposed so the bound is assertable rather than asserted in a comment. */
    @Synchronized
    fun size(): Int = entries.size
}
