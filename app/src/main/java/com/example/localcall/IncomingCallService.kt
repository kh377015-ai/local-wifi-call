package com.example.localcall

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class IncomingCallService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, "call_channel")
            .setContentTitle("Local Wi-Fi Call")
            .setContentText("ခေါ်ဆိုမှုများကို စောင့်ဆိုင်းနေသည်...")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "call_channel",
                "Call Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }

        startForeground(1, notification)
        return START_STICKY
    }
}
