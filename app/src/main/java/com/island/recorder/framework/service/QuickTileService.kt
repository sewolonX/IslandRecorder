package com.island.recorder.framework.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import timber.log.Timber
import com.island.recorder.R
import com.island.recorder.domain.recording.model.RecordingState
import com.island.recorder.domain.recording.model.TileStyle
import com.island.recorder.domain.settings.repository.AppSettingsRepository
import com.island.recorder.ui.activity.RecordingShortcutActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject

class QuickTileService : TileService() {

    private val appSettingsRepo: AppSettingsRepository by inject()
    override fun onStartListening() {
        super.onStartListening()
        val isRecording = RecorderService.recordingState.value.let {
            it is RecordingState.Recording || it is RecordingState.Paused
        }
        updateTile(isRecording)
    }

    override fun onClick() {
        super.onClick()

        val state = RecorderService.recordingState.value

        if (state is RecordingState.Recording || state is RecordingState.Paused) {
            // Stop directly via service intent - no Activity needed
            Timber.d("Quick tile clicked - stopping recording")
            val intent = Intent(this, RecorderService::class.java).apply {
                action = RecorderService.ACTION_STOP_RECORDING
            }
            startService(intent)
        } else {
            // Launch transparent activity for MediaProjection consent
            Timber.d("Quick tile clicked - launching shortcut for recording")
            val intent = Intent(this, RecordingShortcutActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile(isRecording: Boolean) {
        val settings = runBlocking { appSettingsRepo.recordingSettingsFlow.first() }
        val tileStyle = settings.tileStyle
        val iconRes = if (tileStyle == TileStyle.APP_ICON) {
            R.drawable.ic_launcher_foreground_large
        } else {
            R.drawable.ic_record
        }

        qsTile?.apply {
            state = if (isRecording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label =
                if (isRecording) getString(R.string.tile_stop_recording) else getString(R.string.tile_start_recording)
            icon = Icon.createWithResource(this@QuickTileService, iconRes)
            updateTile()
        }
    }
}
