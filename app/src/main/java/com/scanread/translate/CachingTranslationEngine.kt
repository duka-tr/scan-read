package com.scanread.translate

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remembers recent translations so text seen again (e.g. after scrolling back) is instant. */
class CachingTranslationEngine(
    private val delegate: TranslationEngine,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) : TranslationEngine {

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<String, String>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > maxEntries
    }

    override suspend fun translate(texts: List<String>): List<String> {
        val cached = mutex.withLock { texts.map { cache[it] } }
        val missing = texts.filterIndexed { index, _ -> cached[index] == null }.distinct()
        if (missing.isEmpty()) return cached.map { it!! }

        val translated = missing.zip(delegate.translate(missing)).toMap()
        mutex.withLock { cache.putAll(translated) }
        return texts.mapIndexed { index, text -> cached[index] ?: translated.getValue(text) }
    }

    override fun close() = delegate.close()

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 500
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
