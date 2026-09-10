package com.cyh128.hikari_novel.ui.other

import android.content.Context
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** Uses the actual list width, including split-screen, rail and list insets. */
class NovelGridLayoutManager(context: Context) : GridLayoutManager(context, 3)
