package com.cyh128.hikari_novel.util

/**
 * Version-tag handling for the update check.
 *
 * The release feed mixes real versions with non-version tags (the upstream
 * repository's `bye` notice, for example) and the installed version name can
 * carry a `DEBUG` suffix. Parsing both sides into numbers is what keeps a tag
 * like `bye` from being read as "newer than everything".
 */
object VersionTag {

    private val VERSION = Regex("""v?(\d+(?:\.\d+)*)""")

    /**
     * The numeric components of [raw], or null when it is not a version tag.
     *
     * `3.9.10`, `v3.9.10` and `3.9.10 DEBUG` all parse; `bye` and `latest` do
     * not, which is what filters them out of the update check.
     */
    fun parse(raw: String?): List<Int>? {
        val match = VERSION.find(raw?.trim().orEmpty()) ?: return null
        return match.groupValues[1].split('.').map { it.toIntOrNull() ?: return null }
    }

    /**
     * Compares two parsed versions component by component.
     *
     * Numeric order matters: `3.9.10` is newer than `3.9.9`, which string
     * comparison gets backwards. Missing trailing components count as zero, so
     * `3.10` and `3.10.0` are equal.
     */
    fun compare(a: List<Int>, b: List<Int>): Int {
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = (a.getOrNull(i) ?: 0) - (b.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }

    /** True when any of [candidates] is a strictly newer version than [current]. */
    fun anyNewerThan(current: String?, candidates: List<String?>): Boolean {
        val installed = parse(current) ?: return false
        return candidates.any { candidate ->
            parse(candidate)?.let { compare(it, installed) > 0 } == true
        }
    }
}
