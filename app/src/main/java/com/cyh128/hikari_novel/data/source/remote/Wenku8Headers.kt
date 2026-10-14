package com.cyh128.hikari_novel.data.source.remote

/**
 * Headers for the wenku8 *web* endpoints (`www.wenku8.net` / `www.wenku8.cc`).
 *
 * The site sits behind Cloudflare, which does not treat every client the same
 * way. Two things have to match, both measured on a device against
 * `https://www.wenku8.net/checkcode.php` (2026-10-11) using the app's own
 * Dalvik User-Agent:
 *
 * 1. User-Agent. The site serves the official app's Dalvik identity and answers
 *    Cloudflare's challenge to everything else:
 *
 *    | User-Agent                      | result |
 *    | ------------------------------- | ------ |
 *    | `Dalvik/...` (the official app) | 200    |
 *    | Android WebView UA              | 403    |
 *    | Chrome mobile / desktop UA      | 403    |
 *    | `curl/8.0`                      | 403    |
 *
 * 2. `Accept-Encoding`. Cloudflare answers `403` to any request carrying
 *    `Accept-Encoding: gzip`, and OkHttp adds exactly that header on its own for
 *    every request:
 *
 *    | Accept-Encoding        | result |
 *    | ---------------------- | ------ |
 *    | (absent)               | 200    |
 *    | `gzip`                 | 403    |
 *    | `identity`             | 200    |
 *
 *    This is why `curl` (which sends no such header by default) reached the site
 *    while the app did not, on the same device, host and User-Agent. The browser
 *    UA the app used to send was a second, independent cause of the same `403`.
 *
 * Both are adaptations to the site's current policy, not a bypass: the site can
 * change the rule at any time. They live here, in one place, so a future change
 * is a one-line edit rather than a hunt through several methods.
 */
object Wenku8Headers {

    /**
     * The identity the site serves. Same value the app already sends to the
     * `app.wenku8.com` API, so the two halves stay consistent.
     */
    const val USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 15; 23114RD76B Build/AQ3A.240912.001)"

    /**
     * The only `Accept-Encoding` the web host accepts.
     *
     * `gzip` is answered with a `403`, so the client has to ask for an
     * uncompressed response instead of letting OkHttp negotiate gzip by default.
     */
    const val ACCEPT_ENCODING = "identity"

    /**
     * True for the two web hosts that Cloudflare gates.
     *
     * Shared so the header, retry and timeout rules all agree on which hosts
     * they apply to instead of each re-declaring the same string pair.
     */
    fun isWebHost(host: String): Boolean =
        host == "www.wenku8.net" || host == "www.wenku8.cc"
}
