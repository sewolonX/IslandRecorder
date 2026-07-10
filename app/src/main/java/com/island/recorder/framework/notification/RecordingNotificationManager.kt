package com.island.recorder.framework.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.island.recorder.R
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import com.island.recorder.framework.service.RecorderService
import com.island.recorder.ui.activity.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

class RecordingNotificationManager(
    private val context: Context,
    private val privilegedOperations: PrivilegedOperationProvider
) {

    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val superIslandMutex = Mutex()

    @Volatile
    private var isXmsfNetworkBlocked = false

    private val xmsfUid: Int? by lazy {
        runCatching {
            context.packageManager.getPackageUid(
                XMSF_PACKAGE,
                PackageManager.PackageInfoFlags.of(0)
            )
        }.getOrNull()
    }

    companion object {
        const val CHANNEL_ID = "recording_channel"
        const val NOTIFICATION_ID = 1001
        private const val XMSF_PACKAGE = "com.xiaomi.xmsf"
        private const val SUPER_ISLAND_BLOCKING_INTERVAL_MS = 125
        private const val ACTION_PAUSE_RESUME_REQUEST_CODE = 1
        private const val ACTION_STOP_REQUEST_CODE = 2
        private const val CONTENT_REQUEST_CODE = 3
    }

    init {
        createNotificationChannel()
    }

    fun createRecordingNotification(
        durationMs: Long,
        isPaused: Boolean = false,
        bypass: Boolean = false
    ): Notification {
        val formattedDuration = formatDuration(durationMs)
        val payload = RecordingNotificationPayload(
            durationMs = durationMs,
            isPaused = isPaused,
            title = context.getString(
                if (isPaused) R.string.notification_paused_title else R.string.notification_recording_title,
                formattedDuration
            ),
            contentText = context.getString(
                if (isPaused) R.string.notification_paused_message else R.string.notification_recording_message
            ),
            bypassSuperIslandRestriction = bypass
        )
        val actions = createActions(payload)
        return buildNotification(payload, actions)
    }

    fun updateNotification(notification: Notification, bypass: Boolean = false) {
        if (!bypass || !privilegedOperations.capability().hasPrivilegedOperations) {
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }

        notifyWithSuperIslandBypass(notification)
    }

    fun runWithSuperIslandBypass(bypass: Boolean = false, block: () -> Unit) {
        if (!bypass || !privilegedOperations.capability().hasPrivilegedOperations) {
            block()
            return
        }

        val targetUid = xmsfUid
        if (targetUid == null) {
            block()
            return
        }

        val blocked = try {
            runBlocking(Dispatchers.IO) {
                privilegedOperations.setPackageNetworkingEnabled(targetUid, false)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to block XMSF network for Super Island bypass")
            false
        }
        if (blocked) {
            isXmsfNetworkBlocked = true
        }

        try {
            block()
        } finally {
            if (blocked) {
                scope.launch {
                    delay(SUPER_ISLAND_BLOCKING_INTERVAL_MS.milliseconds)
                    superIslandMutex.withLock {
                        withContext(NonCancellable) {
                            restoreXmsfNetworkIfNeeded()
                        }
                    }
                }
            }
        }
    }

    fun release() {
        scope.cancel()
        restoreXmsfNetworkIfNeeded()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.notification_channel_desc)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        payload: RecordingNotificationPayload,
        actions: RecordingNotificationActions
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(payload.title)
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(actions.content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setWhen(payload.timerWhen)
            .setShowWhen(true)
            .setUsesChronometer(!payload.isPaused)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(
                actions.pauseResumeIconRes,
                actions.pauseResumeTitle,
                actions.pauseResume
            )
            .addAction(R.drawable.ic_stop, context.getString(R.string.action_stop), actions.stop)

        return builder.build().apply {
            extras.putAll(buildSuperIslandExtras(payload, actions))
        }
    }

    private fun createActions(payload: RecordingNotificationPayload): RecordingNotificationActions {
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            contentIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val pauseResumeAction = if (payload.isPaused) {
            RecorderService.ACTION_RESUME_RECORDING
        } else {
            RecorderService.ACTION_PAUSE_RECORDING
        }
        val pauseResumePendingIntent = PendingIntent.getService(
            context,
            ACTION_PAUSE_RESUME_REQUEST_CODE,
            Intent(context, RecorderService::class.java).apply { action = pauseResumeAction },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopPendingIntent = PendingIntent.getService(
            context,
            ACTION_STOP_REQUEST_CODE,
            Intent(context, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP_RECORDING
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return RecordingNotificationActions(
            content = contentPendingIntent,
            pauseResume = pauseResumePendingIntent,
            stop = stopPendingIntent,
            pauseResumeTitle = context.getString(
                if (payload.isPaused) R.string.action_resume else R.string.action_pause
            ),
            pauseResumeIconRes = if (payload.isPaused) R.drawable.ic_resume else R.drawable.ic_pause
        )
    }

    private fun buildSuperIslandExtras(
        payload: RecordingNotificationPayload,
        actions: RecordingNotificationActions
    ): Bundle {
        val timerInfo = JSONObject().apply {
            put("timerWhen", payload.timerWhen)
            put("timerType", if (payload.isPaused) 2 else 1)
            put("timerSystemCurrent", payload.now)
        }
        val tickerIconKey = "miui.focus.pic_ticker"
        val pauseIconKey = if (payload.isPaused) {
            "miui.focus.pic_resume"
        } else {
            "miui.focus.pic_pause"
        }
        val pauseIconDarkKey = if (payload.isPaused) {
            "miui.focus.pic_resume_dark"
        } else {
            "miui.focus.pic_pause_dark"
        }
        val stopIconKey = "miui.focus.pic_stop"
        val stopIconDarkKey = "miui.focus.pic_stop_dark"

        val focusParam = JSONObject().apply {
            put(
                "param_v2",
                JSONObject().apply {
                    put("protocol", 1)
                    put("updatable", true)
                    put("enableFloat", false)
                    put("business", "screen_recording")
                    put("scene", "recorder")
                    put("content", payload.contentText)
                    put("notifyId", "${context.packageName}$NOTIFICATION_ID")
                    put("islandFirstFloat", false)
                    put("ticker", payload.contentText)
                    put("tickerPic", tickerIconKey)
                    put("tickerPicDark", tickerIconKey)
                    put("param_island", buildSuperIslandArea(timerInfo, tickerIconKey))
                    put(
                        "animTextInfo",
                        buildSuperIslandTimerAnimation(timerInfo, tickerIconKey, payload)
                    )
                    put(
                        "actions",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("actionIntentType", 0)
                                    put("action", "miui.focus.action_1")
                                    put("type", 0)
                                    put("actionIcon", pauseIconKey)
                                    put("actionIconDark", pauseIconDarkKey)
                                }
                            )
                            put(
                                JSONObject().apply {
                                    put("actionIntentType", 0)
                                    put("action", "miui.focus.action_2")
                                    put("type", 0)
                                    put("actionIcon", stopIconKey)
                                    put("actionIconDark", stopIconDarkKey)
                                }
                            )
                        }
                    )
                }
            )
        }

        return Bundle().apply {
            putString("miui.focus.param", focusParam.toString())
            putBundle("miui.focus.actions", buildSuperIslandActions(actions))
            putBundle("miui.focus.pics", buildSuperIslandPictures())
        }
    }

    private fun buildSuperIslandArea(timerInfo: JSONObject, tickerIconKey: String): JSONObject =
        JSONObject().apply {
            put("islandPriority", 1)
            put("islandTimeout", 43200)
            put("islandProperty", 2)
            put(
                "bigIslandArea",
                JSONObject().apply {
                    put(
                        "imageTextInfoLeft",
                        JSONObject().apply {
                            put("type", 1)
                            put(
                                "picInfo",
                                JSONObject().apply {
                                    put("type", 1)
                                    put("pic", tickerIconKey)
                                }
                            )
                        }
                    )
                    put(
                        "sameWidthDigitInfo",
                        JSONObject().apply {
                            put("timerInfo", timerInfo)
                        }
                    )
                }
            )
            put(
                "smallIslandArea",
                JSONObject().apply {
                    put(
                        "picInfo",
                        JSONObject().apply {
                            put("type", 1)
                            put("pic", tickerIconKey)
                        }
                    )
                }
            )
        }

    private fun buildSuperIslandTimerAnimation(
        timerInfo: JSONObject,
        tickerIconKey: String,
        payload: RecordingNotificationPayload
    ): JSONObject =
        JSONObject().apply {
            put("timerInfo", timerInfo)
            put(
                "animIconInfo",
                JSONObject().apply {
                    put("type", 1)
                    put("src", "voiceWaveBig")
                    put("number", 0)
                    put("loop", !payload.isPaused)
                    put("autoplay", !payload.isPaused)
                }
            )
            put(
                "picInfo",
                JSONObject().apply {
                    put("type", 1)
                    put("pic", tickerIconKey)
                }
            )
        }

    private fun buildSuperIslandActions(actions: RecordingNotificationActions): Bundle {
        val pauseResumeAction = Notification.Action.Builder(
            null,
            actions.pauseResumeTitle,
            actions.pauseResume
        ).build()
        val stopAction = Notification.Action.Builder(
            null,
            context.getString(R.string.action_stop),
            actions.stop
        ).build()

        return Bundle().apply {
            putParcelable("miui.focus.action_1", pauseResumeAction)
            putParcelable("miui.focus.action_2", stopAction)
        }
    }

    private fun buildSuperIslandPictures(): Bundle =
        Bundle().apply {
            putParcelable(
                "miui.focus.pic_ticker",
                Icon.createWithResource(context, R.drawable.ic_focus_ticker)
            )
            putParcelable(
                "miui.focus.pic_pause",
                Icon.createWithResource(context, R.drawable.ic_focus_pause_light)
            )
            putParcelable(
                "miui.focus.pic_resume",
                Icon.createWithResource(context, R.drawable.ic_focus_resume_light)
            )
            putParcelable(
                "miui.focus.pic_stop",
                Icon.createWithResource(context, R.drawable.ic_focus_stop_light)
            )
            putParcelable(
                "miui.focus.pic_pause_dark",
                Icon.createWithResource(context, R.drawable.ic_focus_pause)
            )
            putParcelable(
                "miui.focus.pic_resume_dark",
                Icon.createWithResource(context, R.drawable.ic_focus_resume)
            )
            putParcelable(
                "miui.focus.pic_stop_dark",
                Icon.createWithResource(context, R.drawable.ic_focus_stop)
            )
        }

    private fun notifyWithSuperIslandBypass(notification: Notification) {
        val targetUid = xmsfUid
        if (targetUid == null) {
            notificationManager.notify(NOTIFICATION_ID, notification)
            return
        }

        scope.launch {
            superIslandMutex.withLock {
                try {
                    if (!privilegedOperations.setPackageNetworkingEnabled(targetUid, false)) {
                        notificationManager.notify(NOTIFICATION_ID, notification)
                        return@withLock
                    }
                    isXmsfNetworkBlocked = true
                    notificationManager.notify(NOTIFICATION_ID, notification)
                    delay(SUPER_ISLAND_BLOCKING_INTERVAL_MS.milliseconds)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to notify with Super Island bypass")
                    notificationManager.notify(NOTIFICATION_ID, notification)
                } finally {
                    withContext(NonCancellable) {
                        restoreXmsfNetworkIfNeeded()
                    }
                }
            }
        }
    }

    private fun restoreXmsfNetworkIfNeeded() {
        val targetUid = xmsfUid ?: return
        if (!isXmsfNetworkBlocked) return

        try {
            if (privilegedOperations.setPackageNetworkingEnabled(targetUid, true)) {
                isXmsfNetworkBlocked = false
                Timber.d("Restored XMSF network after Super Island bypass")
            } else {
                Timber.e(
                    "Failed to restore XMSF network after Super Island bypass; " +
                        "keeping blocked state for retry"
                )
            }
        } catch (e: Exception) {
            Timber.e(
                e,
                "Failed to restore XMSF network after Super Island bypass; " +
                    "keeping blocked state for retry"
            )
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }

    private data class RecordingNotificationPayload(
        val durationMs: Long,
        val isPaused: Boolean,
        val title: String,
        val contentText: String,
        val bypassSuperIslandRestriction: Boolean
    ) {
        val now: Long = System.currentTimeMillis()
        val timerWhen: Long = now - durationMs
    }

    private data class RecordingNotificationActions(
        val content: PendingIntent,
        val pauseResume: PendingIntent,
        val stop: PendingIntent,
        val pauseResumeTitle: String,
        val pauseResumeIconRes: Int
    )
}
