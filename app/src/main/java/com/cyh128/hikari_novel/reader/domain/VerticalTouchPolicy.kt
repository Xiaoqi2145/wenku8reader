package com.cyh128.hikari_novel.reader.domain

/**
 * Pure policy for deciding whether a gesture toggles reader controls.
 *
 * Vertical reading is a continuous scroll, so there is no page to turn and no
 * chapter boundary at the screen edge. A tap anywhere over the text toggles the
 * chrome; chapter switching belongs to the explicit controls only — the bottom
 * bar buttons, the chapter buttons at the start and end of the session, and the
 * catalog.
 *
 * An earlier revision split the screen into three tap zones and made the left
 * and right thirds jump a chapter outright. That mixed paging semantics into a
 * scrolling reader: a tap meant to stop a fling could leave the chapter the
 * reader was on, and it took the persisted position with it. The zones are gone
 * — the decision is now only "is this a tap that no interactive child owns".
 */
object VerticalTouchPolicy {
    fun shouldToggleReaderBar(isTap: Boolean, startedOnInteractiveChild: Boolean): Boolean =
        isTap && !startedOnInteractiveChild
}
