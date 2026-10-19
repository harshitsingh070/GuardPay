package com.guardpay.device

import android.telephony.TelephonyManager

/**
 * Pure mapping from Android call state to the GuardPay binary signal.
 * Only an actual off-hook call counts — ringing is NOT active.
 */
object CallStateMapper {

    fun isActiveCall(callState: Int): Boolean =
        callState == TelephonyManager.CALL_STATE_OFFHOOK

    fun label(callState: Int): String =
        if (isActiveCall(callState)) "ACTIVE" else "IDLE"
}
