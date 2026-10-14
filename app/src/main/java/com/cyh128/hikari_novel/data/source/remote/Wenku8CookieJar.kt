package com.cyh128.hikari_novel.data.source.remote

import rxhttp.wrapper.cookie.CookieStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the one persistent [CookieStore] used by the shared OkHttp client.
 *
 * rxhttp's `CookieStore` is backed by a `DiskLruCache`, so `jieqiUserInfo`
 * survives process death and upgrades. Logging out used to clear only the MMKV
 * copy of the session and left the cookie jar intact; the next login then
 * carried the previous session and could be rejected. Routing logout through
 * this holder makes "退出登录" actually drop the stored session.
 */
@Singleton
class Wenku8CookieJar @Inject constructor() {
    private var store: CookieStore? = null

    fun attach(store: CookieStore) {
        this.store = store
    }

    fun clear() {
        store?.removeAllCookie()
    }
}
