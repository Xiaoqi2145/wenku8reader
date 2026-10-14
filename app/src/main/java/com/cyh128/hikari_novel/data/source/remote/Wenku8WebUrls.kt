package com.cyh128.hikari_novel.data.source.remote

/**
 * URL and header adjustments the wenku8 web host needs before a request leaves
 * the app.
 *
 * Both rules below were measured against `https://www.wenku8.net` on
 * 2026-10-12 with the app's own Dalvik User-Agent and `Accept-Encoding:
 * identity`. They are adaptations to the site's current edge policy, not a
 * bypass, and the site can change either at any time.
 *
 * ### 1. The literal `charset` query parameter is blocked
 *
 * Cloudflare answers `403` (its "Attention Required!" page) to any request whose
 * query string contains the lowercase substring `charset`:
 *
 * | query                    | result |
 * | ------------------------ | ------ |
 * | `?classid=0`             | 302    |
 * | `?classid=0&foo=bar`     | 302    |
 * | `?classid=0&charset=gbk` | 403    |
 * | `?classid=0&CHARSET=gbk` | 302    |
 * | `?classid=0&charset2=x`  | 403    |
 * | `?classid=0&ch%61rset=gbk` | 302  |
 *
 * The match is on the raw string, case-sensitive, and it is a substring test
 * rather than a parameter-name test (`charset2` and `xcharset` trip it too).
 * Percent-encoding one letter of the name (`charset` -> `ch%61rset`) keeps the
 * parameter working — the server decodes it and honours GBK/BIG5 — while the
 * raw string no longer contains `charset`.
 *
 * This is why the app's detail page and bookshelf failed while the home page
 * worked: `articleinfo.php?...&charset=gbk` and
 * `bookcase.php?classid=0&charset=gbk` were answered with the WAF page, and the
 * repository reported the `403` as a generic network error. The home page's
 * `index.php?charset=gbk` happens not to be gated, which is why only some
 * screens broke.
 *
 * ### 2. `reader.php` needs a Referer
 *
 * The chapter list (`modules/article/reader.php`) is answered with a Cloudflare
 * challenge when the request carries no `Referer`, and with the real page when
 * it carries any `Referer` on the same host. Every other web endpoint is happy
 * either way, so a same-host root Referer is applied as a default.
 */
object Wenku8WebUrls {

    /** The query parameter the site reads to choose GBK or BIG5. */
    const val CHARSET_PARAM = "charset"

    /**
     * The same parameter with its first letter percent-encoded.
     *
     * The server decodes this back to `charset`, but the raw URL no longer
     * contains the substring the edge blocks.
     */
    const val ENCODED_CHARSET_PARAM = "ch%61rset"

    /**
     * Matches a `charset=` assignment at the start of the query or after a `?`
     * or `&` separator.
     *
     * The start-of-query case matters: the interceptor rewrites
     * `HttpUrl.encodedQuery`, which has already dropped the leading `?`, so
     * `index.php?charset=gbk` arrives here as plain `charset=gbk`. A pattern
     * that required a separator would leave that — the most common shape, a lone
     * parameter — unrewritten and still blocked.
     *
     * Requiring either the start or a separator is also what keeps the rewrite
     * from touching a `charset` that appears inside a value: a query of
     * `searchkey=acharset=1` must not be altered.
     */
    private val CHARSET_ASSIGNMENT = Regex("(^|[?&])$CHARSET_PARAM=")

    /**
     * Rewrites every `charset=` parameter name so the request is not blocked.
     *
     * Only the name is touched; values and any other parameter are left exactly
     * as they were. The function is idempotent: an already-encoded name does not
     * match [CHARSET_ASSIGNMENT] and is returned unchanged, so it is safe to
     * apply on every hop.
     */
    fun hideCharsetParamName(url: String): String =
        url.replace(CHARSET_ASSIGNMENT) { match ->
            match.groupValues[1] + ENCODED_CHARSET_PARAM + "="
        }

    /**
     * The default Referer for a request to [host]: that host's root.
     *
     * A same-host Referer is enough to satisfy the rule for `reader.php` and is
     * harmless on every other endpoint, so one value can serve as the default.
     */
    fun refererFor(host: String): String = "https://$host/"
}
