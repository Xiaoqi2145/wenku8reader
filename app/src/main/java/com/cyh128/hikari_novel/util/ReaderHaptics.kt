package com.cyh128.hikari_novel.util

import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptic feedback for reader interactions, gated by the user's setting.
 *
 * The reader fires a light tap on three kinds of gesture — toggling the menu
 * bars, stepping a chapter, and releasing the progress slider. All of them go
 * through here so the "震动反馈" switch in Settings has exactly one thing to
 * turn off, and so a new call site cannot silently ignore the setting.
 *
 * The flag is passed in rather than read from a singleton: the setting is read
 * at call time from the injected config, so flipping the switch takes effect
 * immediately and nothing has to be kept in sync.
 */
object ReaderHaptics {

    /**
     * Fires [HapticFeedbackConstants.KEYBOARD_TAP] on [view], or does nothing
     * when [enabled] is false.
     */
    fun tap(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
