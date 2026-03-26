package com.locpc.reminders.api

import com.locpc.reminders.data.LocationResponse
import com.locpc.reminders.data.LocationUpdate
import com.locpc.reminders.data.Reminder
import com.locpc.reminders.data.ReminderResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {

    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("email") email: String,
        @Field("password") password: String
    ): Response<ResponseBody>

    @GET("api/reminder")
    suspend fun getReminders(): Response<List<Reminder>>

    @POST("api/reminders/sync")
    suspend fun syncReminders(@Body reminders: List<Map<String, Any>>): Response<List<Reminder>>

    @POST("api/location/update")
    suspend fun updateLocation(@Body location: LocationUpdate): Response<LocationResponse>

    @GET("logout")
    suspend fun logout(): Response<ResponseBody>
}

data class UserData(
    val id: String,
    val email: String,
    val username: String? = null
)
