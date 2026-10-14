package com.cyh128.hikari_novel.data.source.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two URL rules the wenku8 web host needs, pinned without a network.
 *
 * The edge answers `403` to any query string containing the literal lowercase
 * `charset`, so the parameter name is percent-encoded before the request leaves
 * the app. Every case below is one that was measured against
 * `https://www.wenku8.net` on 2026-10-12.
 */
class Wenku8WebUrlsTest {

    @Test
    fun theParameterNameIsEncoded() {
        assertEquals(
            "id=2906&ch%61rset=gbk",
            Wenku8WebUrls.hideCharsetParamName("id=2906&charset=gbk")
        )
    }

    @Test
    fun aLeadingParameterIsEncoded() {
        // The interceptor rewrites `HttpUrl.encodedQuery`, which has no leading
        // `?`, so the first parameter is the common case and must be handled.
        assertEquals(
            "ch%61rset=gbk",
            Wenku8WebUrls.hideCharsetParamName("charset=gbk")
        )
        // `index.php?&charset=gbk` reaches the interceptor as `&charset=gbk`.
        assertEquals(
            "&ch%61rset=gbk",
            Wenku8WebUrls.hideCharsetParamName("&charset=gbk")
        )
    }

    @Test
    fun theValueIsLeftAlone() {
        assertEquals(
            "classid=0&ch%61rset=big5",
            Wenku8WebUrls.hideCharsetParamName("classid=0&charset=big5")
        )
    }

    @Test
    fun charsetInsideAValueIsNotTouched() {
        // A search for a title containing the text must not be rewritten.
        assertEquals(
            "searchkey=acharset=1&page=1",
            Wenku8WebUrls.hideCharsetParamName("searchkey=acharset=1&page=1")
        )
        assertEquals(
            "searchkey=xcharset&page=1",
            Wenku8WebUrls.hideCharsetParamName("searchkey=xcharset&page=1")
        )
    }

    @Test
    fun otherParametersAreNotTouched() {
        assertEquals(
            "sort=allvisit&page=1&ch%61rset=gbk",
            Wenku8WebUrls.hideCharsetParamName("sort=allvisit&page=1&charset=gbk")
        )
    }

    @Test
    fun theResultNoLongerContainsTheBlockedSubstring() {
        val rewritten = Wenku8WebUrls.hideCharsetParamName("id=2906&charset=gbk")
        assertFalse(rewritten.contains(Wenku8WebUrls.CHARSET_PARAM))
    }

    @Test
    fun anAlreadyEncodedNameIsIdempotent() {
        // Applying the rule twice must not double-encode.
        val once = Wenku8WebUrls.hideCharsetParamName("id=2906&charset=gbk")
        assertEquals(once, Wenku8WebUrls.hideCharsetParamName(once))
    }

    @Test
    fun anEmptyQueryIsUnchanged() {
        assertEquals("", Wenku8WebUrls.hideCharsetParamName(""))
    }

    @Test
    fun aQueryWithoutCharsetIsUnchanged() {
        // A request like `reader.php?aid=2906` carries a query but nothing to
        // rewrite; the interceptor must leave it byte-for-byte alone rather than
        // emit a trailing `?`.
        assertEquals("aid=2906", Wenku8WebUrls.hideCharsetParamName("aid=2906"))
    }

    @Test
    fun theRefererIsTheSameHostRoot() {
        // `reader.php` is challenged without a Referer and served with one; any
        // same-host value works, so the root is used as a single default.
        assertEquals("https://www.wenku8.net/", Wenku8WebUrls.refererFor("www.wenku8.net"))
        assertEquals("https://www.wenku8.cc/", Wenku8WebUrls.refererFor("www.wenku8.cc"))
        assertTrue(Wenku8WebUrls.refererFor("www.wenku8.net").startsWith("https://"))
    }
}
