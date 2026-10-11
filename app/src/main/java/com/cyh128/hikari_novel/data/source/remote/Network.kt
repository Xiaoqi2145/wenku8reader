package com.cyh128.hikari_novel.data.source.remote

import com.cyh128.hikari_novel.util.Base64Helper
import okhttp3.ResponseBody
import rxhttp.toAwait
import rxhttp.wrapper.coroutines.CallAwait
import rxhttp.wrapper.entity.KeyValuePair
import rxhttp.wrapper.entity.OkResponse
import rxhttp.wrapper.param.RxHttp
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@Singleton
class Network @Inject constructor() {
    /**
     * Shared client for image downloads.
     *
     * A dedicated client keeps download I/O off the RxHttp client's pool, but
     * it must be built once and reused so connections are pooled and idle
     * threads are reclaimed instead of leaking one client per download.
     */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(okhttp3.ConnectionPool(4, 30, java.util.concurrent.TimeUnit.SECONDS))
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        // Illustration URLs live on the image CDN, which does not gate on UA, but
        // sending the app's own identity keeps every outbound request consistent
        // and avoids standing out from the rest of the client.
        val request = Request.Builder().url(url)
            .header("User-Agent", Wenku8Headers.USER_AGENT)
            .build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            response.body?.bytes() ?: error("Empty response body")
        }
    }
    fun login(
        url: String,
        username: String,
        password: String,
        checkcode: String,
        usecookie: String
    ): CallAwait<OkResponse<ResponseBody?>> =
        RxHttp.postForm(url)
            .add("username", username)
            .add("password", password)
            .add("checkcode", checkcode)
            .add("usecookie", usecookie)
            .add("action", "login")
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Referer", url)
            .toAwait<ResponseBody>()
            .toAwaitOkResponse()

    fun post(url: String, cookie: String, pairs: List<KeyValuePair>): CallAwait<OkResponse<ResponseBody?>> {
        val rxHttpFormParam = RxHttp.postForm(url)
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
        if (cookie.isNotBlank()) rxHttpFormParam.addHeader("Cookie", cookie)
        pairs.forEach { rxHttpFormParam.add(it.key, it.value) }
        return rxHttpFormParam.toAwait<ResponseBody>().toAwaitOkResponse()
    }

    fun get(url: String, cookie: String): CallAwait<OkResponse<ResponseBody?>> {
        val rxHttp = RxHttp.get(url)
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
        if (cookie.isNotBlank()) rxHttp.addHeader("Cookie", cookie)
        return rxHttp.toAwait<ResponseBody>().toAwaitOkResponse()
    }

    fun getWithoutCookie(url: String): CallAwait<OkResponse<ResponseBody?>> =
        RxHttp.get(url)
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
            .toAwait<ResponseBody>()
            .toAwaitOkResponse()

    fun getReaderPage(url: String, referer: String): CallAwait<OkResponse<ResponseBody?>> =
        RxHttp.get(url)
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("Accept-Language", "zh-CN,zh;q=0.9")
            .addHeader("Referer", referer)
            .toAwait<ResponseBody>()
            .toAwaitOkResponse()

    fun getFromAppWenku8Com(request: String): CallAwait<String> {
        //val request = "action=book&do=text&aid=2906&cid=117212&t=0"
        return RxHttp
            .postForm("http://app.wenku8.com/api.php")
            .addHeader("User-Agent", Wenku8Headers.USER_AGENT)
            .add("appver", "1.21")
            .add("request", Base64Helper.encodeBase64(request))
            .add("timestamp", "${System.currentTimeMillis() / 1000}")
            .toAwait<String>()
    }
}
