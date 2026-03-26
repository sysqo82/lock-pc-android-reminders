package com.locpc.reminders.api

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import retrofit2.Converter
import retrofit2.converter.gson.GsonConverterFactory
import timber.log.Timber

object GsonProvider {
    val lenientGson: Gson = GsonBuilder()
        .setLenient()
        .create()

    val converterFactory: Converter.Factory by lazy {
        GsonConverterFactory.create(lenientGson)
    }

    init {
        Timber.d("GsonProvider initialized with lenient GSON")
    }
}
