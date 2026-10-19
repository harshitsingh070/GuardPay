package com.guardpay.device

import android.telephony.TelephonyManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure mapping test — TelephonyManager constants are compile-time ints, so
 * this runs on the JVM with no emulator or device.
 */
class CallStateMapperTest {

    @Test
    fun offhookMeansActiveCall() {
        assertTrue(CallStateMapper.isActiveCall(TelephonyManager.CALL_STATE_OFFHOOK))
    }

    @Test
    fun idleMeansNoActiveCall() {
        assertFalse(CallStateMapper.isActiveCall(TelephonyManager.CALL_STATE_IDLE))
    }

    @Test
    fun ringingMeansNoActiveCall() {
        assertFalse(CallStateMapper.isActiveCall(TelephonyManager.CALL_STATE_RINGING))
    }
}
