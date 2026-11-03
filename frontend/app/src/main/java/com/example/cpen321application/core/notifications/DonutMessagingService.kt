package com.example.cpen321application.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.cpen321application.MainActivity
import com.example.cpen321application.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Shows pushes that arrive while the app is open. In the background Android shows them itself.
 */
class DonutMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        val content = message.notification ?: return
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            return
        }

        val openApp = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_GAME_ID, message.data[EXTRA_GAME_ID])
        val onTap = PendingIntent.getActivity(
            this, 0, openApp, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, getString(R.string.match_channel_id))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(content.title)
            .setContentText(content.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(onTap)
            .build()

        createMatchChannel(this)
        NotificationManagerCompat.from(this).notify(message.messageId.hashCode(), notification)
    }
}

/**
 * Creates the channel match pushes are shown in. Does nothing if it already exists.
 */
fun createMatchChannel(context: Context) {
    val channel = NotificationChannel(
        context.getString(R.string.match_channel_id),
        context.getString(R.string.match_channel_name),
        NotificationManager.IMPORTANCE_HIGH
    )
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
