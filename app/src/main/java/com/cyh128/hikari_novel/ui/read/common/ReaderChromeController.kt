package com.cyh128.hikari_novel.ui.read.common

import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Owns the reader toolbar/bottom dock pair, the always-on status line, and
 * their visibility state.
 *
 * The dock is translated as a whole — background and top stroke included — so
 * hiding it never leaves a floating footprint behind. The status line is a
 * sibling of the dock: it fades out while the menu is shown (the menu surface
 * already occupies the bottom edge) and fades back in for immersive reading.
 *
 * The bottom translation is always calculated from the dock's parent bounds;
 * never mix decor/window coordinates with CoordinatorLayout coordinates.
 */
class ReaderChromeController(
    private val window: Window,
    private val chromeHost: ViewGroup,
    private val toolbar: View,
    private val bottomDock: View,
    private val statusLine: View,
    private val shownBarColor: () -> Int,
    private val hiddenBarColor: () -> Int,
    private val onVisibilityChanged: (Boolean) -> Unit,
    private val onContentBoundsChanged: () -> Unit = {}
) {
    private var targetShown = true

    init {
        val relayout = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            if (!targetShown) syncHiddenPosition()
            onContentBoundsChanged()
        }
        chromeHost.addOnLayoutChangeListener(relayout)
        bottomDock.addOnLayoutChangeListener(relayout)
    }

    fun show() {
        targetShown = true
        cancelAnimations()
        applySystemBars(shownBarColor())
        toolbar.animate()
            .translationY(0f)
            .setDuration(180L)
            .start()
        bottomDock.animate()
            .translationY(0f)
            .setDuration(180L)
            .start()
        setStatusLineVisible(false)
        onVisibilityChanged(true)
        onContentBoundsChanged()
    }

    /**
     * Re-applies the system bar colours after the reader palette changed.
     *
     * The reader chrome follows the reading colours, so switching day/night or
     * picking a preset must also repaint the status and navigation bars.
     */
    fun refreshSystemBars() {
        applySystemBars(if (targetShown) shownBarColor() else hiddenBarColor())
    }

    fun hide() {
        targetShown = false
        cancelAnimations()
        applySystemBars(hiddenBarColor())
        toolbar.animate()
            .translationY(-toolbar.bottom.toFloat())
            .setDuration(180L)
            .start()
        bottomDock.animate()
            .translationY(hideTranslation())
            .setDuration(180L)
            .start()
        setStatusLineVisible(true)
        onVisibilityChanged(false)
        onContentBoundsChanged()
    }

    /** Reapply the hidden position after insets, rotation, or split-screen relayout. */
    fun onWindowGeometryChanged() {
        if (targetShown) {
            onContentBoundsChanged()
            return
        }
        cancelAnimations()
        syncHiddenPosition()
        onContentBoundsChanged()
    }

    /** The status line is mutually exclusive with the menu surface. */
    private fun setStatusLineVisible(visible: Boolean) {
        statusLine.animate().cancel()
        if (visible) {
            if (statusLine.visibility != View.VISIBLE) {
                statusLine.alpha = 0f
                statusLine.visibility = View.VISIBLE
            }
            statusLine.animate()
                .alpha(1f)
                .setDuration(180L)
                .withEndAction(null)
                .start()
        } else {
            statusLine.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction { statusLine.visibility = View.INVISIBLE }
                .start()
        }
    }

    private fun hideTranslation(): Float {
        val parent = bottomDock.parent as? ViewGroup ?: chromeHost
        return ReaderChromeGeometry.hideTranslation(
            parentHeight = parent.height,
            contentBottomInset = 0,
            barTop = bottomDock.top
        )
    }

    private fun syncHiddenPosition() {
        toolbar.translationY = -toolbar.bottom.toFloat()
        bottomDock.translationY = hideTranslation()
    }

    private fun cancelAnimations() {
        toolbar.animate().cancel()
        bottomDock.animate().cancel()
    }

    private fun applySystemBars(color: Int) {
        window.statusBarColor = color
        window.navigationBarColor = color
        val lightBars = ColorUtils.calculateLuminance(color) > 0.5
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
    }
}
