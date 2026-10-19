package com.guardpay.device

import android.content.Context
import java.util.UUID

/**
 * Stable demo device identity. Random UUID on first launch, kept until the
 * user clears app data. Never IMEI/hardware identifiers.
 */
class DeviceIdStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun deviceId(): UUID {
        val stored = prefs.getString(KEY_DEVICE_ID, null)
        if (stored != null) return UUID.fromString(stored)
        val fresh = UUID.randomUUID()
        prefs.edit().putString(KEY_DEVICE_ID, fresh.toString()).apply()
        return fresh
    }

    companion object {
        private const val PREFS = "guardpay_device"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
