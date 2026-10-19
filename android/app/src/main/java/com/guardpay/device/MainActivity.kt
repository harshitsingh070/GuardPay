package com.guardpay.device

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Minimal device-signal screen. Observes call state only while the activity
 * is in the foreground (no foreground service, no background monitoring —
 * see README for lifecycle limitations).
 */
class MainActivity : Activity() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var deviceId: String
    private lateinit var reporter: CallStateReporter

    private lateinit var callStatusView: TextView
    private lateinit var backendStatusView: TextView
    private lateinit var deviceIdView: TextView

    private var callback: TelephonyCallback? = null
    private var legacyListener: android.telephony.PhoneStateListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        callStatusView = findViewById(R.id.callStatus)
        backendStatusView = findViewById(R.id.backendStatus)
        deviceIdView = findViewById(R.id.deviceId)

        deviceId = DeviceIdStore(this).deviceId().toString()
        deviceIdView.text = deviceId
        reporter = CallStateReporter(BuildConfig.GUARDPAY_API_BASE_URL, BuildConfig.GUARDPAY_DEVICE_TOKEN)
        telephonyManager = getSystemService(TelephonyManager::class.java)

        findViewById<Button>(R.id.reportIdleButton).setOnClickListener {
            publish(false)
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startObserving()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE), PERMISSION_CODE)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_CODE
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startObserving()
        } else {
            Toast.makeText(this, "Call-state permission is required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        stopObserving()
    }

    private fun startObserving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val cb =
                object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                    override fun onCallStateChanged(state: Int) {
                        publish(CallStateMapper.isActiveCall(state))
                    }
                }
            callback = cb
            telephonyManager.registerTelephonyCallback(Executors.newSingleThreadExecutor(), cb)
        } else {
            @Suppress("DEPRECATION")
            val listener =
                object : android.telephony.PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                        // phoneNumber is intentionally ignored — never stored or sent.
                        publish(CallStateMapper.isActiveCall(state))
                    }
                }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
        }
    }

    private fun stopObserving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            callback?.let { telephonyManager.unregisterTelephonyCallback(it) }
            callback = null
        } else {
            legacyListener?.let {
                @Suppress("DEPRECATION")
                telephonyManager.listen(it, android.telephony.PhoneStateListener.LISTEN_NONE)
            }
            legacyListener = null
        }
    }

    private fun publish(activeCall: Boolean) {
        runOnUiThread {
            callStatusView.text = if (activeCall) "ACTIVE" else "IDLE"
        }
        reporter.report(deviceId, activeCall) { connected ->
            runOnUiThread {
                backendStatusView.text = if (connected) "CONNECTED" else "DISCONNECTED"
            }
        }
    }

    companion object {
        private const val PERMISSION_CODE = 1001
    }
}
