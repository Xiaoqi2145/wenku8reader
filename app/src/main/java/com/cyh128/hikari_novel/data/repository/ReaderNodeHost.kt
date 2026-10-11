package com.cyh128.hikari_novel.data.repository

/**
 * Which wenku8 host the app talks to, and how a stored value is repaired.
 *
 * The two hosts are not interchangeable, which is what makes this worth its own
 * type: `.cc` answers with a Cloudflare challenge (HTTP 403, `Cf-Mitigated:
 * challenge`) that an OkHttp client cannot pass, while `.net` still serves the
 * flow — provided the request carries the official app's User-Agent (see
 * `com.cyh128.hikari_novel.data.source.remote.Wenku8Headers`). A stored value
 * therefore decides whether the app works at all.
 *
 * The repair is a pure function so the rule is testable without MMKV; the
 * repository applies it once at startup.
 */
object ReaderNodeHost {

    /** The host that serves requests. */
    const val WORKING = "www.wenku8.net"

    /**
     * The host the removed rewrite forced every install onto.
     *
     * Kept as a named constant because the migration has to recognise exactly
     * this value: any other stored host is the user's own choice and is left
     * alone.
     */
    const val BLOCKED = "www.wenku8.cc"

    /**
     * The host to store, given whatever is stored now.
     *
     * Only [BLOCKED] is rewritten. A user who deliberately picked the other host
     * keeps it, and an empty or unknown value is left for the default to handle
     * rather than being silently overwritten here.
     */
    fun migrate(stored: String?): String =
        if (stored == BLOCKED) WORKING else stored.orEmpty()

    /** True when [host] is the one that serves requests. */
    fun isWorking(host: String?): Boolean = host == WORKING
}
