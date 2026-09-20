package com.locpc.reminders

import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class NetworkClientTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val prefsStorage = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)

        `when`(editor.remove(anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            prefsStorage.remove(key)
            editor
        }

        val app = mock(App::class.java)
        `when`(app.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        App.instance = app
    }

    @Test
    fun retrofit_hasCorrectBaseUrl() {
        val retrofit = NetworkClient.retrofit
        assertNotNull(retrofit)
        assertEquals(ApiConfig.BASE_URL, retrofit.baseUrl().toString())
    }

    @Test
    fun getCookieHeader_returnsNullForInvalidOrEmptyUrl() {
        val result = NetworkClient.getCookieHeader("invalid-url")
        assertNull(result)
    }

    @Test
    fun clearCookies_clearsWithoutThrowingException() {
        NetworkClient.clearCookies()
    }
}
