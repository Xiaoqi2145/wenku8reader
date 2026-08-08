package com.cyh128.hikari_novel.util

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReaderStatusText {
    fun current(context: Context): String {
        val battery = context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it >= 0 }
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        return if (battery == null) time else "$time | $battery%"
    }
}
