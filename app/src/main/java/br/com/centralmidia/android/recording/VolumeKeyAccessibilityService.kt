package br.com.centralmidia.android.recording

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Intercepts Volume + only while a screen recording is active. */
class VolumeKeyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()

        // Reassert the key-filter request when Android binds the service.
        // The XML metadata also declares this flag; doing both avoids relying
        // on a stale accessibility-service configuration after an app update.
        runCatching {
            serviceInfo = serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // All other keys, and Volume + outside an active recording, behave normally.
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (!ScreenRecordService.isRecording) return false

        // Consume DOWN and UP while recording, but toggle only once per press.
        // This prevents the physical volume from changing and avoids repeat toggles.
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            runCatching {
                startService(
                    Intent(this, ScreenRecordService::class.java)
                        .setAction(ScreenRecordService.ACTION_TOGGLE_PAUSE),
                )
            }
        }
        return true
    }
}
