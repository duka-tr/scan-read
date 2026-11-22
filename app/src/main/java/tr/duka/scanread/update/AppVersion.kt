package tr.duka.scanread.update

/**
 * A release version such as `0.2.1` (an optional leading `v` and a suffix like `-debug` are
 * ignored). Missing components count as zero, so `1.2` equals `1.2.0`.
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        for (i in 0 until maxOf(parts.size, other.parts.size)) {
            val difference = parts.getOrElse(i) { 0 } - other.parts.getOrElse(i) { 0 }
            if (difference != 0) return difference
        }
        return 0
    }

    override fun toString(): String = parts.joinToString(".")

    companion object {
        private val PATTERN = Regex("""^v?(\d+(?:\.\d+)*)""")

        /** Parses `v1.2.3`, `1.2.3-debug` etc.; `null` for anything else (e.g. model release tags). */
        fun parse(text: String): AppVersion? =
            PATTERN.find(text.trim())?.groupValues?.get(1)?.split('.')?.map(String::toInt)?.let(::AppVersion)
    }
}
