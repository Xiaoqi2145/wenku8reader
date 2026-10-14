package com.cyh128.hikari_novel

import android.app.Application
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.cyh128.hikari_novel.data.repository.AppRepository
import com.cyh128.hikari_novel.data.repository.Wenku8Repository
import com.cyh128.hikari_novel.data.repository.ReaderRepository
import com.cyh128.hikari_novel.data.source.remote.Wenku8CookieJar
import com.cyh128.hikari_novel.data.source.remote.Wenku8CallLogger
import com.cyh128.hikari_novel.data.source.remote.Wenku8Headers
import com.cyh128.hikari_novel.data.source.remote.Wenku8RetryInterceptor
import com.cyh128.hikari_novel.data.source.remote.Wenku8Timeouts
import com.cyh128.hikari_novel.data.source.remote.Wenku8WebUrls
import com.cyh128.hikari_novel.ui.other.CrashActivity
import com.cyh128.hikari_novel.util.LanguageHelper
import com.cyh128.hikari_novel.util.ThemeHelper
import com.developer.crashx.config.CrashConfig
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import okhttp3.Cookie
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import rxhttp.RxHttpPlugins
import rxhttp.wrapper.cookie.CookieStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.net.ssl.SSLSession


@HiltAndroidApp
class HikariApp : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var wenku8Repository: Wenku8Repository

    @Inject
    lateinit var readerRepository: ReaderRepository

    @Inject
    lateinit var wenku8CookieJar: Wenku8CookieJar

    companion object {
        lateinit var application: Application
    }

    override fun onCreate() {
        MMKV.initialize(this)

        super.onCreate()

        ThemeHelper.setCurrentTheme(appRepository.getAppTheme())
        ThemeHelper.setDarkMode(appRepository.getDarkMode())
        LanguageHelper.setCurrentLanguage(appRepository.getLanguage())

        // One-shot: installs whose node host was forced onto the Cloudflare-blocked
        // host are moved onto the one that answers. Must run before any request,
        // because every request builds its URL from this value.
        wenku8Repository.migrateForcedNodeHost()

        application = this
        val cookieStore = CookieStore()
        wenku8CookieJar.attach(cookieStore)
        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(Wenku8Timeouts.CONNECT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(Wenku8Timeouts.READ_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(Wenku8Timeouts.WRITE_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(Wenku8WebHeadersInterceptor())
            .addInterceptor(Wenku8RetryInterceptor())
            .addInterceptor(CookieInterceptor())
            .eventListener(Wenku8CallLogger())
            .cookieJar(cookieStore) //保存cookie
            .hostnameVerifier { _: String?, _: SSLSession? -> true }
//            .protocols(Collections.singletonList(Protocol.HTTP_1_1))
            .build()

        Glide.get(this).registry
            .replace(
                GlideUrl::class.java,
                InputStream::class.java, OkHttpUrlLoader.Factory(client)
            )

        RxHttpPlugins.init(client)
        // Best-effort maintenance: an exception here must not kill the process at startup.
        applicationScope.launch {
            try {
                readerRepository.resumeQueuedDownloads()
            } catch (t: Throwable) {
                Log.e("HikariApp", "resumeQueuedDownloads failed", t)
            }
        }
        CrashConfig.Builder.create()
            .errorActivity(CrashActivity::class.java)
            .apply()
    }

    /**
     * Makes requests to the wenku8 web host look like the official app's, and
     * applies the URL fixes that host needs.
     *
     * Four things have to match, all measured against `https://www.wenku8.net`
     * with the app's own Dalvik User-Agent:
     *
     *  - `User-Agent`: the site serves the official app's Dalvik identity and
     *    answers Cloudflare's challenge to everything else (Chrome, WebView,
     *    `curl`).
     *  - `Accept-Encoding`: Cloudflare answers `403` to any request carrying
     *    `Accept-Encoding: gzip`, and OkHttp adds exactly that header on its own
     *    for every request. Dropping it to `identity` returns `200`; putting
     *    `gzip` back returns `403`, repeatably. This is why `curl` (which sends
     *    no such header) reached the site while the app did not, on the same
     *    device, host and User-Agent.
     *  - The literal `charset` query parameter is itself blocked: the detail page
     *    and bookshelf requests carried `&charset=gbk` and came back as the WAF
     *    page, which is why those screens reported a network error while the home
     *    page worked. See [Wenku8WebUrls.hideCharsetParamName].
     *  - `reader.php` needs a same-host `Referer` or it is challenged; one is
     *    filled in as a default. See [Wenku8WebUrls.refererFor].
     *
     * The encoding, charset and Referer rules are scoped to the two wenku8 web
     * hosts. The image CDN and the app API are not gated this way, so they keep
     * whatever encoding they negotiate. The User-Agent fallback applies to every
     * request, because the login checkcode is fetched by Glide through this same
     * client and would otherwise go out with OkHttp's default identity.
     */
    private class Wenku8WebHeadersInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val builder = request.newBuilder()

            if (request.header("User-Agent") == null) {
                builder.header("User-Agent", Wenku8Headers.USER_AGENT)
            }
            if (Wenku8Headers.isWebHost(request.url.host)) {
                builder.header("Accept-Encoding", Wenku8Headers.ACCEPT_ENCODING)
                if (request.header("Referer") == null) {
                    builder.header("Referer", Wenku8WebUrls.refererFor(request.url.host))
                }
                // Only touch the URL when there is a query to rewrite: setting an
                // empty encoded query would append a bare trailing `?`.
                val query = request.url.encodedQuery
                if (query != null) {
                    val fixed = request.url.newBuilder()
                        .encodedQuery(Wenku8WebUrls.hideCharsetParamName(query))
                        .build()
                    builder.url(fixed)
                }
            }
            return chain.proceed(builder.build())
        }
    }

    inner class CookieInterceptor: Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val response = chain.proceed(request)

            val setCookies = response.headers("Set-Cookie")

            if (setCookies.isNotEmpty() && Wenku8Headers.isWebHost(request.url.host)) {
                val httpUrl = request.url
                setCookies.forEach { setCookie ->
                    val cookie = Cookie.parse(httpUrl, setCookie)
                    if (cookie != null && cookie.name == "jieqiUserInfo") {
                        // The session value is deliberately not logged. It used to
                        // be written to logcat verbatim, which hands any reader of
                        // the log — including another app on pre-Android-11
                        // devices — a working session.
                        wenku8Repository.cookie = cookie.name + "=" + cookie.value + ";"
                        wenku8Repository.expDate = cookie.expiresAt
                        if (cookie.expiresAt == 253402300799999) {
                            wenku8Repository.expDate = 0
                        }
                    }
                }
            }
            return response
        }
    }
}
