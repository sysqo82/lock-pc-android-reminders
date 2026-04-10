package com.locpc.reminders

import android.os.Handler
import android.os.Looper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.locpc.reminders.data.Reminder
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import timber.log.Timber

object SocketManager {

    private var socket: Socket? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Called on the main thread whenever the server pushes a fresh reminder list. */
    var onRemindersUpdated: ((List<Reminder>) -> Unit)? = null

    /** Called on the main thread when the server requests a one-shot location fix. */
    var onLocateDevice: (() -> Unit)? = null

    fun connect(cookieHeader: String?) {
        if (socket?.connected() == true) return

        try {
            val opts = IO.Options().apply {
                transports = arrayOf("websocket")
                reconnection = true
                reconnectionDelay = 2000
                if (!cookieHeader.isNullOrBlank()) {
                    extraHeaders = mapOf("Cookie" to listOf(cookieHeader))
                }
            }

            val s = IO.socket(ApiConfig.BASE_URL.trimEnd('/'), opts)

            s.on(Socket.EVENT_CONNECT) {
                Timber.d("SocketManager: connected, identifying as dashboard")
                s.emit("identify", JSONObject().put("type", "dashboard"))
            }

            s.on("reminder_update") { args ->
                try {
                    val raw = args.firstOrNull()?.toString() ?: return@on
                    val type = object : TypeToken<List<Reminder>>() {}.type
                    val reminders: List<Reminder> = Gson().fromJson(raw, type)
                    Timber.d("SocketManager: reminder_update received, ${reminders.size} items")
                    mainHandler.post { onRemindersUpdated?.invoke(reminders) }
                } catch (e: Exception) {
                    Timber.e(e, "SocketManager: failed to parse reminder_update")
                }
            }

            s.on("locate_device") {
                Timber.d("SocketManager: locate_device received")
                mainHandler.post { onLocateDevice?.invoke() }
            }

            s.on(Socket.EVENT_DISCONNECT) { Timber.d("SocketManager: disconnected") }
            s.on(Socket.EVENT_CONNECT_ERROR) { args ->
                Timber.e("SocketManager: connect error: ${args.firstOrNull()}")
            }

            socket = s
            s.connect()
        } catch (e: Exception) {
            Timber.e(e, "SocketManager: failed to connect")
        }
    }

    fun disconnect() {
        socket?.disconnect()
        socket = null
        Timber.d("SocketManager: disconnected and cleared")
    }
}
