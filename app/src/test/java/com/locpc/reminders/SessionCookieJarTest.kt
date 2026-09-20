package com.locpc.reminders

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.anyOrNull

class SessionCookieJarTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var cookieJar: SessionCookieJar

    private val prefsStorage = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)

        `when`(editor.putString(anyString(), anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<String>(1)
            prefsStorage[key] = value
            editor
        }

        `when`(editor.remove(anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            prefsStorage.remove(key)
            editor
        }

        `when`(prefs.getString(anyString(), anyOrNull())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val default = invocation.getArgument<String?>(1)
            prefsStorage[key] ?: default
        }

        cookieJar = SessionCookieJar(context)
    }

    @Test
    fun saveFromResponse_and_loadForRequest_storesAndRetrievesMatchingCookies() {
        val url = "https://example.com/api".toHttpUrl()
        val cookie1 = Cookie.Builder()
            .domain("example.com")
            .path("/")
            .name("session")
            .value("abc123secret")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie1))

        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("session", loaded[0].name)
        assertEquals("abc123secret", loaded[0].value)
    }

    @Test
    fun saveFromResponse_replacesCookieWithSameNameDomainPath() {
        val url = "https://example.com/api".toHttpUrl()
        val oldCookie = Cookie.Builder()
            .domain("example.com")
            .path("/")
            .name("token")
            .value("old_value")
            .build()

        val newCookie = Cookie.Builder()
            .domain("example.com")
            .path("/")
            .name("token")
            .value("new_value")
            .build()

        cookieJar.saveFromResponse(url, listOf(oldCookie))
        cookieJar.saveFromResponse(url, listOf(newCookie))

        val loaded = cookieJar.loadForRequest(url)
        assertEquals(1, loaded.size)
        assertEquals("new_value", loaded[0].value)
    }

    @Test
    fun getCookieHeaderForHost_returnsFormattedHeaderString() {
        val url = "https://api.example.com/data".toHttpUrl()
        val cookie1 = Cookie.Builder()
            .domain("api.example.com")
            .path("/")
            .name("c1")
            .value("v1")
            .build()
        val cookie2 = Cookie.Builder()
            .domain("api.example.com")
            .path("/")
            .name("c2")
            .value("v2")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie1, cookie2))

        val header = cookieJar.getCookieHeaderForHost("api.example.com")
        assertEquals("c1=v1; c2=v2", header)
    }

    @Test
    fun getCookieHeaderForHost_returnsNullWhenNoCookiesMatch() {
        val header = cookieJar.getCookieHeaderForHost("nonexistent.com")
        assertNull(header)
    }

    @Test
    fun clear_removesCookiesFromMemoryAndPreferences() {
        val url = "https://example.com/".toHttpUrl()
        val cookie = Cookie.Builder()
            .domain("example.com")
            .path("/")
            .name("auth")
            .value("token123")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))
        cookieJar.clear()

        val loaded = cookieJar.loadForRequest(url)
        assertTrue(loaded.isEmpty())
        verify(editor).remove("cookies")
    }
}
