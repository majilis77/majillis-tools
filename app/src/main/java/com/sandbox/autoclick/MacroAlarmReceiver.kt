package com.sandbox.autoclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MacroAlarmReceiver : BroadcastReceiver() {
    companion object { const val EXTRA_MACRO_ID = "macro_id"; private const val CHANNEL = "macro_schedule" }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_MACRO_ID, -1L)
        if (id < 0) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val macro = MacroDatabase.get(context).macroDao().getById(id)
                if (macro != null) showNotification(context, macro)
            } finally { pendingResult.finish() }
        }
    }

    private fun showNotification(context: Context, macro: MacroEntity) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Macros programadas", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(context, macro.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra("macro_id", macro.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Macro programada: ${macro.name}")
            .setContentText("Abre Sandbox AutoClicker para iniciar la macro y autorizar la captura de pantalla.")
            .setContentIntent(open).setAutoCancel(true).build()
        manager.notify(macro.id.toInt(), notification)
    }
}
