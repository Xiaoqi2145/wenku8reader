package com.cyh128.hikari_novel.ui.read.common

import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.cyh128.hikari_novel.R

/**
 * Installs the reader window policy in one place.
 *
 * Reader windows are edge-to-edge: the chrome surfaces (toolbar, bottom dock,
 * status line) extend into the system bar areas through inset padding, so the
 * menu surface is continuous with the gesture area instead of sitting next to
 * a separately painted slab. Insets are re-applied on navigation mode,
 * rotation, split-screen and IME changes, and every change triggers a fresh
 * chrome measurement so the hidden dock stays fully off screen.
 */
class ReaderWindowInsetsCoordinator(
    private val window: Window,
    private val root: View,
    private val toolbar: View,
    private val bottomDock: View,
    private val statusLine: View,
    private val onChanged: (WindowInsetsCompat) -> Unit
) {
    private val statusLineBaseHeight =
        statusLine.resources.getDimensionPixelSize(R.dimen.gi_reader_status_line_height)

    fun install() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.updatePadding(top = bars.top)
            bottomDock.updatePadding(bottom = bars.bottom)
            // The status line has a semantic base height; the gesture inset
            // extends its surface instead of shrinking the content row.
            statusLine.updatePadding(bottom = bars.bottom)
            statusLine.updateLayoutParams { height = statusLineBaseHeight + bars.bottom }
            onChanged(insets)
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            onChanged(ViewCompat.getRootWindowInsets(root) ?: WindowInsetsCompat.CONSUMED)
        }
        ViewCompat.requestApplyInsets(root)
    }
}
