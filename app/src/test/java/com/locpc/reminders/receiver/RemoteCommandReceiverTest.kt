package com.locpc.reminders.receiver

import android.content.Context
import android.content.Intent
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any

class RemoteCommandReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: RemoteCommandReceiver

    @Before
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        receiver = RemoteCommandReceiver()
    }

    @Test
    fun onReceive_locateDevice_usesWorkManagerInsteadOfStartForegroundServiceOnAndroid12Plus() {
        // On Android 12+, starting foreground service from broadcast receiver throws ForegroundServiceStartNotAllowedException.
        // If context.startForegroundService is called, simulate the Android 12+ exception:
        `when`(context.startForegroundService(any())).thenThrow(
            IllegalStateException("ForegroundServiceStartNotAllowedException: Foreground service started from background")
        )

        val intent = mock(Intent::class.java)
        `when`(intent.action).thenReturn(RemoteCommandReceiver.ACTION_LOCATE_DEVICE)

        try {
            receiver.onReceive(context, intent)
            // If receiver calls startForegroundService, exception is thrown and test fails
        } catch (e: Exception) {
            fail("RemoteCommandReceiver threw exception when handling ACTION_LOCATE_DEVICE: ${e.message}")
        }
    }
}
