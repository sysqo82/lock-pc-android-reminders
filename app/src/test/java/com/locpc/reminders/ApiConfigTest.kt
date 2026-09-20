package com.locpc.reminders

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigTest {

    @Test
    fun baseUrl_isValidAndEndsWithSlash() {
        val url = ApiConfig.BASE_URL
        assertTrue("BASE_URL must end with '/' for Retrofit", url.endsWith("/"))
        val parsed = url.toHttpUrlOrNull()
        assertNotNull("BASE_URL must be a valid HTTP/HTTPS URL", parsed)
    }
}
