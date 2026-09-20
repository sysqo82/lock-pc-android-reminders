package com.locpc.reminders.api

import android.content.Context
import android.content.SharedPreferences
import com.locpc.reminders.App
import com.locpc.reminders.SecurePrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull

class ApiManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val prefsStorage = mutableMapOf<String, Any>()

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

        `when`(editor.putBoolean(anyString(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<Boolean>(1)
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
            (prefsStorage[key] as? String) ?: default
        }

        `when`(prefs.getBoolean(anyString(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val default = invocation.getArgument<Boolean>(1)
            (prefsStorage[key] as? Boolean) ?: default
        }

        val app = mock(App::class.java)
        `when`(app.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        App.instance = app

        SecurePrefs.initialize(context)
    }

    @Test
    fun setLoggedIn_updatesEmailAndLoggedInState() {
        assertFalse(ApiManager.isLoggedIn())
        assertNull(ApiManager.getLoggedInEmail())

        ApiManager.setLoggedIn("user@example.com")

        assertTrue(ApiManager.isLoggedIn())
        assertEquals("user@example.com", ApiManager.getLoggedInEmail())
    }

    @Test
    fun clearSession_clearsLoggedInStateAndEmail() {
        ApiManager.setLoggedIn("user@example.com")
        assertTrue(ApiManager.isLoggedIn())

        ApiManager.clearSession()

        assertFalse(ApiManager.isLoggedIn())
        assertNull(ApiManager.getLoggedInEmail())
    }
}
