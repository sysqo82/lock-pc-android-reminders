package com.locpc.reminders

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import timber.log.Timber

class SessionCookieJar(private val context: Context) : CookieJar {

    private val prefs by lazy {
        context.getSharedPreferences("session_cookies", Context.MODE_PRIVATE)
    }
    private val gson = Gson()

    // In-memory cache
    private val cookies = mutableListOf<Cookie>()
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        try {
            val json = prefs.getString("cookies", null) ?: return
            val type = object : TypeToken<List<SerializableCookie>>() {}.type
            val saved: List<SerializableCookie> = gson.fromJson(json, type)
            val now = System.currentTimeMillis()
            val valid = saved.filter { it.expiresAt > now || it.persistent }
                .mapNotNull { it.toCookie() }
            cookies.addAll(valid)
            Timber.d("SessionCookieJar: restored ${cookies.size} cookies from prefs")
        } catch (e: Exception) {
            Timber.e(e, "SessionCookieJar: failed to restore cookies")
        }
    }

    private fun persist() {
        try {
            val serializable = cookies.map { SerializableCookie.from(it) }
            prefs.edit().putString("cookies", gson.toJson(serializable)).apply()
        } catch (e: Exception) {
            Timber.e(e, "SessionCookieJar: failed to persist cookies")
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        ensureLoaded()
        // Replace existing cookies with the same name+domain+path
        cookies.forEach { new ->
            this.cookies.removeAll { it.name == new.name && it.domain == new.domain && it.path == new.path }
            this.cookies.add(new)
        }
        persist()
        Timber.d("SessionCookieJar: saved ${cookies.size} cookies, total=${this.cookies.size}")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        ensureLoaded()
        return cookies.filter { it.matches(url) }
    }

    fun getCookieHeaderForHost(host: String): String? {
        ensureLoaded()
        return cookies
            .filter { it.domain == host || host.endsWith(".${it.domain}") || it.domain == host.removePrefix("www.") }
            .joinToString("; ") { "${it.name}=${it.value}" }
            .ifEmpty { null }
    }

    fun clear() {
        cookies.clear()
        prefs.edit().remove("cookies").apply()
        Timber.d("SessionCookieJar: cleared")
    }

    // Minimal serializable representation of a Cookie
    private data class SerializableCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val persistent: Boolean,
        val hostOnly: Boolean
    ) {
        fun toCookie(): Cookie? = try {
            Cookie.Builder()
                .name(name).value(value)
                .apply { if (hostOnly) hostOnlyDomain(domain) else domain(domain) }
                .path(path)
                .apply { if (expiresAt > 0) expiresAt(expiresAt) }
                .apply { if (secure) secure() }
                .apply { if (httpOnly) httpOnly() }
                .build()
        } catch (e: Exception) { null }

        companion object {
            fun from(c: Cookie) = SerializableCookie(
                name = c.name, value = c.value, domain = c.domain,
                path = c.path, expiresAt = c.expiresAt, secure = c.secure,
                httpOnly = c.httpOnly, persistent = c.persistent, hostOnly = c.hostOnly
            )
        }
    }
}

