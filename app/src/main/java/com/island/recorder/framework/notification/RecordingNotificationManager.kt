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
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import com.island.recorder.framework.service.RecorderService
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
        private const val SUPER_ISLAND_HIGHLIGHT_COLOR = "#FB382F"
        private const val SUPER_ISLAND_BLOCKING_INTERVAL_MS = 125
        private const val ACTION_PAUSE_RESUME_REQUEST_CODE = 1
        private const val ACTION_STOP_REQUEST_CODE = 2
        private const val TICKER_ICON_KEY = "miui.focus.pic_ticker"
    }

    init {
        createNotificationChannel()
    }

    fun createRecordingNotification(
        durationMs: Long,
        tileStyle: TileStyle,
        isPaused: Boolean = false,
        bypass: Boolean = false
    ): Notification {
        val payload = RecordingNotificationPayload(
            durationMs = durationMs,
            isPaused = isPaused,
            tileStyle = tileStyle,
            title = context.getString(
                if (isPaused) R.string.notification_paused_title else R.string.notification_recording_title
            ) + "\u00A0",
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
            .setSmallIcon(R.drawable.ic_focus_ticker)
            .setOngoing(true)
            .setRequestPromotedOngoing(true)
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
                    put("tickerPic", TICKER_ICON_KEY)
                    put("tickerPicDark", TICKER_ICON_KEY)
                    put("param_island", buildSuperIslandArea(timerInfo))
                    put(
                        "animTextInfo",
                        buildSuperIslandTimerAnimation(timerInfo, payload)
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
                                    put("actionTextColor", "#FB382F")
                                    put("actionTextColorDark", "#FB382F")
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
            putBundle("miui.focus.pics", buildSuperIslandPictures(payload.tileStyle))
        }
    }

    private fun buildSuperIslandArea(timerInfo: JSONObject): JSONObject =
        JSONObject().apply {
            put("islandPriority", 1)
            put("islandTimeout", 43200)
            put("islandProperty", 2)
            put("highlightColor", SUPER_ISLAND_HIGHLIGHT_COLOR)
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
                                    put("pic", TICKER_ICON_KEY)
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
                            put("pic", TICKER_ICON_KEY)
                        }
                    )
                }
            )
        }

    private fun buildSuperIslandTimerAnimation(
        timerInfo: JSONObject,
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
                    put("pic", TICKER_ICON_KEY)
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

    private fun buildSuperIslandPictures(tileStyle: TileStyle): Bundle =
        Bundle().apply {
            val tickerIconRes = if (tileStyle == TileStyle.APP_ICON) {
                R.drawable.ic_focus_ticker
            } else {
                R.drawable.ic_focus_ticker_recorder
            }
            putParcelable(
                "miui.focus.pic_ticker",
                Icon.createWithResource(context, tickerIconRes)
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

    private data class RecordingNotificationPayload(
        val durationMs: Long,
        val isPaused: Boolean,
        val tileStyle: TileStyle,
        val title: String,
        val contentText: String,
        val bypassSuperIslandRestriction: Boolean
    ) {
        val now: Long = System.currentTimeMillis()
        val timerWhen: Long = now - durationMs
    }

    private data class RecordingNotificationActions(
        val pauseResume: PendingIntent,
        val stop: PendingIntent,
        val pauseResumeTitle: String,
        val pauseResumeIconRes: Int
    )
}
