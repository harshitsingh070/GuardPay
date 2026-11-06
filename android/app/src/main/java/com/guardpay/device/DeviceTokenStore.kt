package com.guardpay.device

import android.content.Context

/**
 * Per-device credential issued by the GuardPay backend after login. Kept only
 * on this device and never embedded in the APK.
 */
class DeviceTokenStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun token(): String = prefs.getString(KEY_DEVICE_TOKEN, "").orEmpty()

    fun save(token: String) {
        prefs.edit().putString(KEY_DEVICE_TOKEN, token).apply()
    }

    companion object {
        private const val PREFS = "guardpay_device"
        private const val KEY_DEVICE_TOKEN = "device_token"
    }
}
