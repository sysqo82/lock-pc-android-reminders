package com.locpc.reminders

import android.content.Context
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import timber.log.Timber

object NetworkClient {
    private val cookieJar = SessionCookieJar(App.instance)

    private val okHttp: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val authInterceptor = Interceptor { chain ->
            val reqBuilder: Request.Builder = chain.request().newBuilder()

            // Add bypass header to avoid tunnel password pages when using proxies/tunnels
            reqBuilder.addHeader("bypass-tunnel-reminder", "true")
            reqBuilder.addHeader("Content-Type", "application/json")

            chain.proceed(reqBuilder.build())
        }

        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(com.locpc.reminders.api.GsonProvider.converterFactory)
            .build()
    }

    fun <T> create(service: Class<T>): T = retrofit.create(service)

    // Return the Cookie header string for the provided base URL if cookies are present.
    // This is used to supply cookies to non-HTTP clients (e.g. Socket.IO handshake).
    fun getCookieHeader(url: String): String? {
        return try {
            val parsed = url.toHttpUrlOrNull()
            parsed?.host?.let { cookieJar.getCookieHeaderForHost(it) }
        } catch (e: Exception) {
            Timber.e(e, "Error getting cookie header")
            null
        }
    }

    fun clearCookies() {
        cookieJar.clear()
        Timber.d("Cleared session cookies")
    }
}
