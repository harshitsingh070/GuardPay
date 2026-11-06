package com.guardpay.device

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant

/**
 * Reports the binary call state to GuardPay. Sends deviceId + activeCall +
 * timestamp only — never audio or call metadata.
 */
class CallStateReporter(
    private val apiBaseUrl: String,
    initialDeviceToken: String = "",
) {

    private val client = OkHttpClient()

    @Volatile
    private var deviceToken: String = initialDeviceToken

    fun updateToken(token: String) {
        deviceToken = token
    }

    fun interface ResultListener {
        fun onResult(connected: Boolean)
    }

    fun report(deviceId: String, activeCall: Boolean, listener: ResultListener) {
        val token = deviceToken
        if (token.isBlank()) {
            listener.onResult(false)
            return
        }
        val body =
            """
            {"deviceId": "$deviceId", "activeCall": $activeCall, "observedAt": "${Instant.now()}"}
            """.trimIndent()
                .toRequestBody("application/json".toMediaType())
        val request =
            Request.Builder()
                .url("$apiBaseUrl/api/device-signals/call-state")
                .header("X-GuardPay-Device-Token", token)
                .post(body)
                .build()
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    listener.onResult(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.close()
                    listener.onResult(response.isSuccessful)
                }
            },
        )
    }
}
