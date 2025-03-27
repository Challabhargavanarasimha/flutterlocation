package com.lyokone.location

import android.Manifest
import android.app.*
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

const val kDefaultChannelName = "Location background service"
const val kDefaultNotificationTitle = "Location background service running"
const val kDefaultNotificationIconName = "navigation_empty_icon"

data class NotificationOptions(
        val channelName: String = kDefaultChannelName,
        val title: String = kDefaultNotificationTitle,
        val iconName: String = kDefaultNotificationIconName,
        val subtitle: String? = null,
        val description: String? = null,
        val color: Int? = null,
        val onTapBringToFront: Boolean = false
)

class BackgroundNotification(
        private val context: Context,
        private val channelId: String,
        private val notificationId: Int
) {
    private var options: NotificationOptions = NotificationOptions()
    private var builder: NotificationCompat.Builder = NotificationCompat.Builder(context, channelId)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

    init {
        updateNotification(options, false)
    }

    private fun getDrawableId(iconName: String): Int {
        return context.resources.getIdentifier(iconName, "drawable", context.packageName)
    }

    private fun buildBringToFrontIntent(): PendingIntent? {
        val intent: Intent? = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?.setPackage(null)
                ?.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

        return if (intent != null) {
            PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }
    }

    private fun updateChannel(channelName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = NotificationManagerCompat.from(context)
            val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun canShowNotification(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateNotification(options: NotificationOptions, notify: Boolean) {
        val iconId = getDrawableId(options.iconName).let {
            if (it != 0) it else getDrawableId(kDefaultNotificationIconName)
        }
        builder = builder
                .setContentTitle(options.title)
                .setSmallIcon(iconId)
                .setContentText(options.subtitle)
                .setSubText(options.description)

        builder = if (options.color != null) {
            builder.setColor(options.color).setColorized(true)
        } else {
            builder.setColor(0).setColorized(false)
        }

        builder = if (options.onTapBringToFront) {
            builder.setContentIntent(buildBringToFrontIntent())
        } else {
            builder.setContentIntent(null)
        }

        if (notify) {
            try {
                if (canShowNotification()) {
                    val notificationManager = NotificationManagerCompat.from(context)
                    notificationManager.notify(notificationId, builder.build())
                } else {
                    Log.w(TAG, "Cannot show notification - POST_NOTIFICATIONS permission not granted")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to show notification due to permission issue", e)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show notification", e)
            }
        }
    }

    fun updateOptions(options: NotificationOptions, isVisible: Boolean) {
        if (options.channelName != this.options.channelName) {
            updateChannel(options.channelName)
        }
        updateNotification(options, isVisible)
        this.options = options
    }

    fun build(): Notification {
        updateChannel(options.channelName)
        return builder.build()
    }

    companion object {
        private const val TAG = "BackgroundNotification"
    }
}

class FlutterLocationService : Service(), PluginRegistry.RequestPermissionsResultListener {
    companion object {
        private const val TAG = "FlutterLocationService"
        private const val REQUEST_PERMISSIONS_REQUEST_CODE = 641
        private const val ONGOING_NOTIFICATION_ID = 75418
        private const val CHANNEL_ID = "flutter_location_channel_01"
        private const val REQUEST_NOTIFICATION_PERMISSION = 1001
    }

    private val binder = LocalBinder()
    private var isForeground = false
    private var activity: Activity? = null
    private var backgroundNotification: BackgroundNotification? = null

    var location: FlutterLocation? = null
        private set

    var result: MethodChannel.Result? = null

    inner class LocalBinder : Binder() {
        fun getService(): FlutterLocationService = this@FlutterLocationService
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Creating service.")
        location = FlutterLocation(applicationContext, null)
        backgroundNotification = BackgroundNotification(applicationContext, CHANNEL_ID, ONGOING_NOTIFICATION_ID)
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Binding to location service.")
        return binder
    }

    override fun onDestroy() {
        Log.d(TAG, "Destroying service.")
        location = null
        backgroundNotification = null
        super.onDestroy()
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity?.let {
                ActivityCompat.requestPermissions(
                        it,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        REQUEST_NOTIFICATION_PERMISSION
                )
            }
        }
    }

    fun enableBackgroundMode() {
        if (isForeground) {
            Log.d(TAG, "Service already in foreground mode.")
        } else {
            Log.d(TAG, "Start service in foreground mode.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    requestNotificationPermission()
                    return
                }
            }
            val notification = backgroundNotification!!.build()
            startForeground(ONGOING_NOTIFICATION_ID, notification)
            isForeground = true
        }
    }

    fun disableBackgroundMode() {
        Log.d(TAG, "Stop service in foreground.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        isForeground = false
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
    ): Boolean {
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isForeground) {
                    val notification = backgroundNotification!!.build()
                    startForeground(ONGOING_NOTIFICATION_ID, notification)
                }
                return true
            }
        }
        return false
    }
}
