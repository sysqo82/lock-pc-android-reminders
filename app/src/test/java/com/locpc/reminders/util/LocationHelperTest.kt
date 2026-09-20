package com.locpc.reminders.util

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.anyOrNull

class LocationHelperTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val prefsMap = mutableMapOf<String, String>()

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        prefs = mock(SharedPreferences::class.java)
        editor = mock(SharedPreferences.Editor::class.java)

        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        `when`(prefs.edit()).thenReturn(editor)

        `when`(editor.putString(anyString(), anyString())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val valStr = invocation.getArgument<String>(1)
            prefsMap[key] = valStr
            editor
        }

        `when`(prefs.getString(anyString(), anyOrNull())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val default = invocation.getArgument<String?>(1)
            prefsMap[key] ?: default
        }
    }

    @Test
    fun getDeviceId_generatesAndPersistsDeviceIdWhenFirstCalled() {
        var locationServicesMock: MockedStatic<LocationServices>? = null
        try {
            locationServicesMock = mockStatic(LocationServices::class.java)
            locationServicesMock.`when`<Any> { LocationServices.getFusedLocationProviderClient(context) }
                .thenReturn(mock(FusedLocationProviderClient::class.java))

            val helper = LocationHelper(context)
            val generatedId = helper.getDeviceId()

            assertNotNull(generatedId)
            assertEquals(generatedId, prefsMap["device_id"])

            // Second call retrieves cached id
            val cachedId = helper.getDeviceId()
            assertEquals(generatedId, cachedId)
        } finally {
            locationServicesMock?.close()
        }
    }
}
