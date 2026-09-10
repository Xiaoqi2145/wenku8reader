package com.cyh128.hikari_novel.util

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReaderStatusText {
    fun current(context: Context): String {
        val battery = batteryLevel(context)
        val time = time()
        return if (battery == null) time else "$time | $battery%"
    }

    /** 当前时间，形如 19:43 */
    fun time(): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    /** 剩余电量百分比，取不到时返回 null */
    fun batteryLevel(context: Context): Int? =
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it >= 0 }
}
