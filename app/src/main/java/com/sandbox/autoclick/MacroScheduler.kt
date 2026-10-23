package com.sandbox.autoclick

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class MacroScheduler(private val context: Context) {
    fun scheduleMacroAt(macroId: Long, hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val intent = Intent(context, MacroAlarmReceiver::class.java).putExtra(MacroAlarmReceiver.EXTRA_MACRO_ID, macroId)
        val pending = PendingIntent.getBroadcast(context, macroId.toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 23) {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            } else {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            }
        } else alarm.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
    }

    fun cancel(macroId: Long) {
        val intent = Intent(context, MacroAlarmReceiver::class.java)
        PendingIntent.getBroadcast(context, macroId.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(it)
        }
    }
}
