package br.com.centralmidia.android.recording

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Intercepts Volume + only while a screen recording is active. */
class VolumeKeyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_VOLUME_UP) return false
        if (!ScreenRecordService.isRecording) return false

        // Consume the complete key press while recording, but toggle only once.
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
