package com.cyh128.hikari_novel.ui.read.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.cyh128.hikari_novel.R

/**
 * One circular colour block on a reader colour axis.
 *
 * Mirrors Koodo Reader's `background-color-circle`: the fill is the colour
 * itself, the ring separates it from the panel surface (so a white swatch stays
 * visible), and a saved custom colour carries a small ✕ badge in its corner.
 * The selected entry gains an accent ring instead of a tick, which reads at a
 * glance without covering the colour being judged.
 *
 * The badge is drawn rather than inflated from an icon: it is two strokes, and
 * keeping it here means the touch target and the drawing can never disagree.
 *
 * See docs/reader-settings-round4-plan.md R4-2.
 */
class ReaderColorSwatchView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** The colour this swatch represents, as a resolved ARGB int. */
    var swatchColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** Ring colour, derived from the reader text colour by the caller. */
    var ringColor: Int = Color.GRAY
        set(value) {
            field = value
            invalidate()
        }

    /** Accent used for the selection ring; comes from the App theme. */
    var accentColor: Int = Color.BLACK
        set(value) {
            field = value
            invalidate()
        }

    var isAxisSelected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    /** Non-null turns the ✕ badge on and routes badge taps to it. */
    var onRemove: (() -> Unit)? = null
        set(value) {
            field = value
            invalidate()
        }

    var onPick: (() -> Unit)? = null

    /**
     * Draws a "+" instead of a fill.
     *
     * Used by the axis' trailing "add" entry: a bare ring would be ambiguous
     * with a white swatch, and the plus states the affordance outright.
     */
    var isAddAffordance: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gi_stroke)
    }

    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gi_color_swatch_ring)
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.getDimension(R.dimen.gi_stroke)
        strokeCap = Paint.Cap.ROUND
    }

    private val badgeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val badgeRadius =
        resources.getDimension(R.dimen.gi_color_swatch_badge_size) / 2f

    /** True only for the duration of a badge click, so it does not also pick. */
    private var suppressPick = false

    private val accentInset =
        resources.getDimension(R.dimen.gi_color_swatch_ring)

    init {
        isClickable = true
        isFocusable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = resources.getDimensionPixelSize(R.dimen.gi_color_swatch_size)
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f

        // The badge overhangs the circle, so the circle is inset by its radius
        // to keep both inside the measured bounds.
        val radius = minOf(cx, cy) - badgeRadius

        fillPaint.color = swatchColor
        // A pressed swatch dims slightly, mirroring the ripple the rest of the
        // panel's buttons get.
        fillPaint.alpha = if (isPressed) PRESSED_ALPHA else FULL_ALPHA
        canvas.drawCircle(cx, cy, radius, fillPaint)

        strokePaint.color = ringColor
        canvas.drawCircle(cx, cy, radius, strokePaint)

        if (isAxisSelected) {
            accentPaint.color = accentColor
            canvas.drawCircle(cx, cy, radius + accentInset, accentPaint)
        }

        if (isAddAffordance) {
            badgePaint.color = accentColor
            val arm = radius * ADD_ARM_RATIO
            canvas.drawLine(cx - arm, cy, cx + arm, cy, badgePaint)
            canvas.drawLine(cx, cy - arm, cx, cy + arm, badgePaint)
        }

        if (onRemove != null) {
            val bx = cx + radius * BADGE_DIAGONAL
            val by = cy - radius * BADGE_DIAGONAL
            badgeFillPaint.color = accentColor
            canvas.drawCircle(bx, by, badgeRadius, badgeFillPaint)

            // The ✕ sits on the accent fill, so it needs its own contrast: the
            // reader-derived ring colour can land close to the accent.
            badgePaint.color = if (isLight(accentColor)) Color.BLACK else Color.WHITE
            val arm = badgeRadius * BADGE_ARM_RATIO
            canvas.drawLine(bx - arm, by - arm, bx + arm, by + arm, badgePaint)
            canvas.drawLine(bx - arm, by + arm, bx + arm, by - arm, badgePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A removable swatch carries a second touch target (the ✕ badge), so it
        // has to resolve the hit itself. Everything else keeps the stock
        // View behaviour, where a click ends in [performClick].
        if (onRemove == null) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Hand-rolled touch handling bypasses View's pressed state, so
                // it is set here: a swatch that gives no feedback on touch reads
                // as unresponsive.
                isPressed = true
                return true
            }

            MotionEvent.ACTION_UP -> {
                isPressed = false
                if (isInsideBadge(event.x, event.y)) {
                    // The badge removes; it must not also re-pick the colour.
                    suppressPick = true
                    performClick()
                    suppressPick = false
                    onRemove?.invoke()
                } else if (isInsideCircle(event.x, event.y)) {
                    performClick()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                isPressed = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        if (!suppressPick) onPick?.invoke()
        return true
    }

    /** Perceived-luminance test, used to pick a legible ✕ colour. */
    private fun isLight(color: Int): Boolean {
        val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
        return luminance > 160
    }

    private fun isInsideBadge(x: Float, y: Float): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - badgeRadius
        val bx = cx + radius * BADGE_DIAGONAL
        val by = cy - radius * BADGE_DIAGONAL
        // A slightly generous target: the badge is small by design.
        val slop = badgeRadius * BADGE_TOUCH_SLOP
        return (x - bx) * (x - bx) + (y - by) * (y - by) <= slop * slop
    }

    private fun isInsideCircle(x: Float, y: Float): Boolean {
        val cx = width / 2f
        val cy = height / 2f
        val radius = minOf(cx, cy) - badgeRadius
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= radius * radius
    }

    private companion object {
        /** Badge offset from the centre, as a fraction of the circle radius. */
        const val BADGE_DIAGONAL = 0.72f

        /** Badge touch radius, as a multiple of its drawn radius. */
        const val BADGE_TOUCH_SLOP = 1.8f

        /** ✕ arm length, as a fraction of the badge radius. */
        const val BADGE_ARM_RATIO = 0.42f

        /** "+" arm length, as a fraction of the swatch radius. */
        const val ADD_ARM_RATIO = 0.34f

        /** Alpha of a pressed swatch. */
        const val PRESSED_ALPHA = 150

        const val FULL_ALPHA = 255
    }
}
