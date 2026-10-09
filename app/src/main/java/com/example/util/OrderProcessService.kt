package com.example.util

import android.app.*
import android.content.Intent
import android.os.IBinder
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R

/** Keeps a user-started import/confirmation active when the app is minimized. */
class OrderProcessService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Importação de ofertas", NotificationManager.IMPORTANCE_LOW)
        )
        val running = intent?.getBooleanExtra("running", false) == true
        val failed = intent?.getStringExtra("failure")
        val target = Intent(this, MainActivity::class.java)
            .setAction(ACTION_RESUME)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(this, NOTIFICATION_ID, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_default)
            .setContentTitle(if (running) intent?.getStringExtra("phase") ?: "Processando…" else if (failed != null) "Processo interrompido" else "Processo Concluído")
            .setContentText(if (running) "Toque para acompanhar na mesma página." else failed ?: intent?.getStringExtra("result"))
            .setContentIntent(pending)
            .setOnlyAlertOnce(true)
            .setOngoing(running)
            .setAutoCancel(!running)
            .apply { if (running) setProgress(0, 0, true) }
            .build()
        val completedId = intent?.getStringExtra("completedId")
        if (completedId != null) {
            val completedTarget = Intent(this, MainActivity::class.java).setAction(ACTION_RESUME)
                .putExtra("orderSessionId", completedId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val completedPending = PendingIntent.getActivity(this, completedId.hashCode(), completedTarget,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val completedError = intent?.getStringExtra("completedError")
            val completion = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification_default)
                .setContentTitle(if (completedError == null) "Processo Concluído" else "Processo interrompido")
                .setContentText(intent?.getStringExtra("completedName") + " • " + (completedError ?: "Toque para continuar."))
                .setContentIntent(completedPending).setAutoCancel(true).build()
            manager.notify(completedId, completedId.hashCode(), completion)
        }
        if (running) startForeground(NOTIFICATION_ID, notification)
        else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            if (completedId == null) manager.notify(NOTIFICATION_ID, notification)
            stopSelf()
        }
        return START_NOT_STICKY
    }
    companion object {
        const val ACTION_RESUME = "com.example.RESUME_ORDER_PROCESS"
        const val NOTIFICATION_ID = 8041
        private const val CHANNEL = "order_process"
    }
}
