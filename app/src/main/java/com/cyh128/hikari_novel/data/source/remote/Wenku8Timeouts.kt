package com.cyh128.hikari_novel.data.source.remote

/**
 * Timeouts for the shared OkHttp client.
 *
 * The wenku8 web host sits behind Cloudflare and is slow to first byte on
 * mobile networks. Measured from the device on 2026-10-11 against
 * `https://www.wenku8.net/checkcode.php`:
 *
 * | run | TTFB    |
 * | --- | ------- |
 * | 1   | 9.35 s  |
 * | 2   | 18.36 s |
 *
 * Later on the same device the body of `index.php` still had not finished
 * arriving when a 30 s read timeout fired, so the ceiling is set to 60 s.
 *
 * OkHttp's default read timeout is 10 s, so a request the site answers
 * successfully still fails inside the app whenever TTFB crosses that line.
 * That is the difference between "the site is down" and "the site is slow":
 * the site returns `200`, the client gives up first.
 *
 * The values are deliberately generous but bounded, so a genuinely hung
 * connection still fails instead of waiting forever.
 */
object Wenku8Timeouts {
    const val CONNECT_SECONDS = 15L
    const val READ_SECONDS = 60L
    const val WRITE_SECONDS = 30L
}
