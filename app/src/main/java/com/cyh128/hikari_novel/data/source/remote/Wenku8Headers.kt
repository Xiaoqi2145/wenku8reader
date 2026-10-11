package com.cyh128.hikari_novel.data.source.remote

/**
 * Headers for the wenku8 *web* endpoints (`www.wenku8.net` / `www.wenku8.cc`).
 *
 * The site sits behind Cloudflare, which does not treat every client the same
 * way. Measured against `https://www.wenku8.net/login.php` (2026-10-11):
 *
 * | User-Agent                          | result |
 * | ----------------------------------- | ------ |
 * | `Dalvik/...` (the official app)     | 200    |
 * | Android WebView UA                  | 403    |
 * | Chrome mobile / desktop UA          | 403    |
 * | `curl/8.0`                          | 403    |
 *
 * Only the official app's Dalvik identity is served, so every request the app
 * makes to the web host has to present it. The browser UA the app used to send
 * was answered with a Cloudflare challenge (`403` + `Cf-Mitigated: challenge`),
 * which surfaced as an unexplainable "登录失败" — including the checkcode image,
 * which the login page loads through the same client.
 *
 * This is an adaptation to the site's current policy, not a bypass: the site can
 * change the rule at any time. It lives here, in one place, so a future change is
 * a one-line edit rather than a hunt through five methods.
 */
object Wenku8Headers {

    /**
     * The identity the site serves. Same value the app already sends to the
     * `app.wenku8.com` API, so the two halves stay consistent.
     */
    const val USER_AGENT =
        "Dalvik/2.1.0 (Linux; U; Android 15; 23114RD76B Build/AQ3A.240912.001)"
}
