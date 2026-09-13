package com.cyh128.hikari_novel.ui.read.common

import android.animation.Animator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.gyf.immersionbar.ImmersionBar

/** Owns the single reader toolbar/bottom bar pair and their visibility state. */
class ReaderChromeController(
    private val activity: AppCompatActivity,
    private val toolbar: View,
    private val bottomBar: View,
    private val shownBarColor: Int,
    private val hiddenBarColor: () -> String,
    private val onVisibilityChanged: (Boolean) -> Unit
) {
    fun show() {
        cancelAnimations()
        toolbar.animate()
            .translationY(0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(immersionListener(shown = true))
            .start()
        bottomBar.animate()
            .translationY(0f)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        onVisibilityChanged(true)
    }

    fun hide() {
        cancelAnimations()
        toolbar.animate()
            .translationY(-toolbar.bottom.toFloat())
            .setInterpolator(AccelerateDecelerateInterpolator())
            .setListener(immersionListener(shown = false))
            .start()
        // The bottom bar may have a status-bar margin, so use its actual position.
        bottomBar.animate()
            .translationY((activity.window.decorView.height - bottomBar.top).toFloat())
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        onVisibilityChanged(false)
    }

    private fun cancelAnimations() {
        toolbar.animate().cancel()
        bottomBar.animate().cancel()
    }

    private fun immersionListener(shown: Boolean): Animator.AnimatorListener =
        object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {
                val immersion = ImmersionBar.with(activity)
                if (shown) {
                    immersion
                        .statusBarColor(shownBarColor)
                        .navigationBarColor(shownBarColor)
                } else {
                    immersion
                        .statusBarColor(hiddenBarColor())
                        .navigationBarColor(hiddenBarColor())
                }
                immersion
                    .fitsSystemWindows(true)
                    .autoDarkModeEnable(true)
                    .init()
            }

            override fun onAnimationEnd(animation: Animator) = Unit
            override fun onAnimationCancel(animation: Animator) = Unit
            override fun onAnimationRepeat(animation: Animator) = Unit
        }
}
