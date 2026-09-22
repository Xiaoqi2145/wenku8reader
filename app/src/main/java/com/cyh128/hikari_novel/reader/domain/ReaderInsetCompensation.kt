package com.cyh128.hikari_novel.reader.domain

/**
 * Scroll compensation for a change in the reader's reserved inset.
 *
 * Showing or hiding the reader menu changes the reserved top inset by a toolbar
 * height, which reflows the text. The scroll offset has to move by the same
 * amount so the visible anchor does not shift under the user.
 *
 * The compensation is normally applied *after* layout, so a second inset change
 * can arrive while the first is still undrawn. [target] therefore builds on the
 * pending target when there is one instead of on the raw scroll offset — queuing
 * both would compensate the first change twice and throw the text off by a
 * toolbar height.
 *
 * Kept here rather than in the fragment so the rule is testable on the JVM, the
 * same reason [VerticalProgressCalculator] lives in this package.
 */
object ReaderInsetCompensation {

    /**
     * The scroll offset to apply, or null when nothing needs compensating.
     *
     * Nothing is owed when the inset did not actually change ([topDelta] is 0) or
     * when the reader sits at the top of the content: there the text simply starts
     * below the chrome, which is the intended reading position, and scrolling to a
     * positive offset would push the first line out of view.
     *
     * @param currentScrollY the live scroll offset, used only when no compensation
     *   is already pending.
     * @param pendingTarget the compensation waiting to be drawn, if any.
     * @param topDelta the change in reserved top inset; positive when the chrome
     *   appeared and the content moved down.
     */
    fun target(currentScrollY: Int, pendingTarget: Int?, topDelta: Int): Int? {
        val base = pendingTarget ?: currentScrollY
        if (topDelta == 0 || base <= 0) return null
        return (base + topDelta).coerceAtLeast(0)
    }
}
