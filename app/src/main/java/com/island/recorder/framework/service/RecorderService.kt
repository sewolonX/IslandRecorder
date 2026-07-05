package com.island.recorder.framework.service

import android.Manifest
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.IBinder
import android.service.quicksettings.TileService
import android.view.Display
import com.island.recorder.R
import com.island.recorder.core.audio.AudioRecorder
import com.island.recorder.core.codec.AudioEncoder
import com.island.recorder.core.codec.MediaMuxerWrapper
import com.island.recorder.core.codec.VideoEncoder
import com.island.recorder.core.projection.ScreenCaptureManager
import com.island.recorder.domain.recording.model.AudioSource
import com.island.recorder.domain.recording.model.RecordingOutput
import com.island.recorder.domain.recording.model.RecordingSettings
import com.island.recorder.domain.recording.model.RecordingState
import com.island.recorder.domain.recording.model.ScreenOrientation
import com.island.recorder.domain.recording.provider.RecordingStorageProvider
import com.island.recorder.framework.notification.RecordingNotificationManager
import com.island.recorder.framework.privileged.provider.PrivilegedOperationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Foreground service that manages the screen recording process
 */
class RecorderService : Service() {

    private val binder = RecorderBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val privilegedOperations: PrivilegedOperationProvider by inject()
    private val recordingStorageProvider: RecordingStorageProvider by inject()

    private val recordingNotificationManager: RecordingNotificationManager by inject()
    private val screenCaptureManager: ScreenCaptureManager by inject()

    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    private var audioRecorder: AudioRecorder? = null

    private var muxer: MediaMuxerWrapper? = null
    private var recordingOutput: RecordingOutput? = null
    private var startJob: Job? = null
    private var recordingJob: Job? = null
    private var audioJob: Job? = null
    private var cleanupJob: Job? = null

    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var pauseStartTime: Long = 0
    private var currentSettings: RecordingSettings? = null
    private var touchVisualizationEnabled = false

    private var lastHdrState = false
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY) {
                checkHdrState()
            }
        }
    }

    companion object {
        const val ACTION_START_RECORDING = "com.island.recorder.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.island.recorder.STOP_RECORDING"
        const val ACTION_PAUSE_RECORDING = "com.island.recorder.PAUSE_RECORDING"
        const val ACTION_RESUME_RECORDING = "com.island.recorder.RESUME_RECORDING"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_SETTINGS = "settings"

        private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
        val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

        fun requestTileRefresh(context: Context) {
            TileService.requestListeningState(
                context,
                ComponentName(context, QuickTileService::class.java)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, null)

        Timber.d("RecorderService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                val settings =
                    intent.getParcelableExtra(EXTRA_SETTINGS, RecordingSettings::class.java)

                if (resultData != null && settings != null) {
                    startRecording(resultCode, resultData, settings)
                }
            }

            ACTION_STOP_RECORDING -> stopRecording()
            ACTION_PAUSE_RECORDING -> pauseRecording()
            ACTION_RESUME_RECORDING -> resumeRecording()
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun startRecording(resultCode: Int, data: Intent, settings: RecordingSettings) {
        if (_recordingState.value !is RecordingState.Idle) {
            Timber.w("Recording already in progress")
            return
        }
        if (cleanupJob?.isActive == true) {
            Timber.w("Recording cleanup is still in progress")
            return
        }
        if (settings.audioSource.usesMicrophone() && !hasRecordAudioPermission()) {
            Timber.e("RECORD_AUDIO is required for ${settings.audioSource}")
            _recordingState.value =
                RecordingState.Error(getString(R.string.permission_audio_required))
            requestTileRefresh(this)
            stopSelf()
            return
        }
        currentSettings = settings

        // Show processing state immediately for responsive UI
        _recordingState.value = RecordingState.Processing(0)

        try {
            // Start foreground service (must happen on main thread)
            val notification = recordingNotificationManager.createRecordingNotification(
                0L,
                bypass = settings.bypassFocusIsland
            )
            var foregroundServiceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            if (settings.audioSource.usesMicrophone()) {
                foregroundServiceType =
                    foregroundServiceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(
                RecordingNotificationManager.NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )

            // Initialize MediaProjection (must happen on main thread before API calls)
            if (!screenCaptureManager.initializeProjection(resultCode, data)) {
                _recordingState.value =
                    RecordingState.Error(getString(R.string.error_screen_capture))
                requestTileRefresh(this)
                stopSelf()
                return
            }

        } catch (e: Exception) {
            Timber.e(e, "Error starting foreground/projection")
            _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
            requestTileRefresh(this)
            stopSelf()
            return
        }

        // Heavy initialization in background
        startJob = serviceScope.launch(Dispatchers.Default) {
            try {
                // Root/Shizuku privileged calls may initialize app_process/binder wrappers; keep them off main.
                if (settings.showTouches) {
                    val touchVisualizationEnabled = withContext(Dispatchers.IO) {
                        privilegedOperations.setShowTouches(true)
                    }
                    if (touchVisualizationEnabled) {
                        this@RecorderService.touchVisualizationEnabled = true
                        Timber.d("Touch visualization enabled for recording")
                    }
                }

                // Create output file
                recordingOutput = recordingStorageProvider.createRecordingOutput()

                // Calculate dimensions based on device screen and quality tier
                val (screenWidth, screenHeight) = screenCaptureManager.getScreenDimensions()
                val (rawW, rawH) = settings.videoQuality.computeDimensions(
                    screenWidth,
                    screenHeight
                )

                // Apply orientation override
                val (width, height) = when (settings.screenOrientation) {
                    ScreenOrientation.AUTO -> Pair(rawW, rawH)
                    ScreenOrientation.PORTRAIT -> if (rawW < rawH) Pair(rawW, rawH) else Pair(
                        rawH,
                        rawW
                    )

                    ScreenOrientation.LANDSCAPE -> if (rawW > rawH) Pair(rawW, rawH) else Pair(
                        rawH,
                        rawW
                    )
                }

                // Initialize encoder
                val maxRefreshRate = screenCaptureManager.getMaxRefreshRate()
                val frameRate = settings.frameRate.fps.takeIf { it > 0 } ?: maxRefreshRate
                val bitrate = settings.calculateBitrate(width, height, maxRefreshRate)
                videoEncoder = get {
                    parametersOf(
                        width,
                        height,
                        bitrate,
                        frameRate,
                        settings.frameRate.fps.takeIf { it > 0 },
                        settings.videoCodec.mimeType,
                        settings.videoCodec.isHdrEnabled
                    )
                }

                val surface = videoEncoder?.prepare()
                if (surface == null) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value =
                            RecordingState.Error(getString(R.string.error_encoder))
                        stopSelf()
                    }
                    return@launch
                }

                // Create virtual display
                val virtualDisplay = screenCaptureManager.createVirtualDisplay(
                    surface,
                    width,
                    height,
                    screenCaptureManager.getScreenDensity()
                )

                if (virtualDisplay == null) {
                    withContext(Dispatchers.Main) {
                        _recordingState.value =
                            RecordingState.Error(getString(R.string.error_virtual_display))
                        stopSelf()
                    }
                    return@launch
                }

                // Setup Audio Encoder & Recorder
                var audioEnabled = false
                Timber.d("Audio Source Setting: ${settings.audioSource}")

                if (settings.audioSource != AudioSource.NONE) {
                    Timber.d("Initializing audio encoder and recorder...")
                    audioEncoder = get()
                    audioEncoder?.prepare()

                    audioRecorder = get()

                    // Keep the permission check adjacent to the call site so lint can verify it,
                    // and so we still fail safely if the permission is revoked mid-session.
                    val success = if (hasRecordAudioPermission()) {
                        try {
                            audioRecorder?.start(
                                screenCaptureManager.getMediaProjection(),
                                settings.audioSource
                            ) ?: false
                        } catch (e: SecurityException) {
                            Timber.e(e, "Missing RECORD_AUDIO when starting audio recorder")
                            false
                        }
                    } else {
                        Timber.e("RECORD_AUDIO permission missing when starting audio recorder")
                        false
                    }

                    if (success) {
                        audioEnabled = true
                        Timber.d("Audio recording enabled: ${settings.audioSource}")
                    } else {
                        Timber.e(
                            "Failed to start audio recorder for source: ${settings.audioSource}"
                        )
                        audioEncoder?.release()
                        audioEncoder = null
                    }
                } else {
                    Timber.w("Audio source is NONE - no audio will be recorded")
                }

                // Initialize muxer
                val output = recordingOutput
                    ?: throw IllegalStateException("Recording output is unavailable")
                muxer = MediaMuxerWrapper(
                    output = output.fileDescriptor.fileDescriptor,
                    displayName = output.displayName
                ).apply {
                    prepare()
                    setAudioExpected(audioEnabled)
                }

                // Start recording loop
                startTime = System.currentTimeMillis()
                _recordingState.value = RecordingState.Recording(0)
                requestTileRefresh(this@RecorderService)
                val bypass = settings.bypassFocusIsland
                recordingNotificationManager.updateNotification(
                    recordingNotificationManager.createRecordingNotification(0L, bypass = bypass),
                    bypass = bypass
                )

                recordingJob = serviceScope.launch {
                    recordingLoop()
                }

                if (audioEnabled) {
                    audioJob = serviceScope.launch(Dispatchers.IO) {
                        audioLoop()
                    }
                }

                Timber.d("Recording started")

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error starting recording")
                _recordingState.value = RecordingState.Error(e.message ?: "Unknown error")
                requestTileRefresh(this@RecorderService)
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
    }

    private suspend fun recordingLoop() {
        var videoTrackAdded = false

        while (currentCoroutineContext().isActive) {
            val state = _recordingState.value
            if (state !is RecordingState.Recording && state !is RecordingState.Paused) break

            if (state is RecordingState.Paused) {
                // Drain encoder output and discard (don't write to muxer)
                val drainOutput = videoEncoder?.getEncodedData()
                if (drainOutput is VideoEncoder.EncoderOutput.Data) {
                    videoEncoder?.releaseOutputBuffer(drainOutput.index)
                }

                delay(10.milliseconds)
                continue
            }

            try {
                // Get encoded video data
                val output = videoEncoder?.getEncodedData() ?: VideoEncoder.EncoderOutput.TryAgain

                when (output) {
                    is VideoEncoder.EncoderOutput.FormatChanged -> {
                        Timber.d("Encoder format changed")
                        val format = videoEncoder?.getOutputFormat()
                        if (format != null && !videoTrackAdded) {
                            muxer?.addVideoTrack(format)
                            videoTrackAdded = true
                            Timber.d(
                                "Video track added to muxer, HDR=${videoEncoder?.isHdrActive}"
                            )
                        } else if (format != null) {
                            // Format changed during recording - HDR state may have changed
                            Timber.d(
                                "Format changed during recording, HDR=${videoEncoder?.isHdrActive}"
                            )
                        }
                    }

                    is VideoEncoder.EncoderOutput.Data -> {
                        val (buffer, bufferInfo, bufferIndex) = output

                        if (videoTrackAdded && (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer?.writeVideoSample(buffer, bufferInfo)
                        }

                        videoEncoder?.releaseOutputBuffer(bufferIndex)
                    }

                    is VideoEncoder.EncoderOutput.TryAgain -> {
                        // No data available yet, just continue
                    }
                }

                // Update duration
                val currentDuration = System.currentTimeMillis() - startTime - pausedDuration
                _recordingState.value = RecordingState.Recording(currentDuration)

                delay(10.milliseconds) // Small delay to prevent busy waiting

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error in recording loop")
                break
            }
        }
    }

    private suspend fun audioLoop() {
        var audioTrackAdded = false
        val bufferSize = audioRecorder?.getBufferSize() ?: 4096
        val audioBuffer = ByteArray(bufferSize)
        var readCount = 0

        Timber.d("Starting audio loop with buffer size: $bufferSize")

        while (currentCoroutineContext().isActive) {
            val state = _recordingState.value
            if (state !is RecordingState.Recording && state !is RecordingState.Paused) break

            if (state is RecordingState.Paused) {
                // Drain audio buffer to prevent stale data on resume
                audioRecorder?.read(audioBuffer, bufferSize)
                delay(10.milliseconds)
                continue
            }
            try {
                // 1. Read Audio
                val readResult = audioRecorder?.read(audioBuffer, bufferSize) ?: -1

                if (readResult > 0) {
                    readCount++
                    if (readCount % 100 == 0) {
                        Timber.d("Audio read count: $readCount, bytes: $readResult")
                    }

                    val timestampUs = System.nanoTime() / 1000

                    // 2. Encode Audio
                    audioEncoder?.encode(audioBuffer, readResult, timestampUs)

                    // 3. Retrieve Encoded Data
                    var outputAvailable = true
                    while (outputAvailable) {
                        val output = audioEncoder?.getEncodedData() ?: AudioEncoder.Output.TryAgain

                        when (output) {
                            is AudioEncoder.Output.Data -> {
                                if (output.info.size > 0) {
                                    if (audioTrackAdded) {
                                        muxer?.writeAudioSample(output.buffer, output.info)
                                    } else {
                                        Timber.w(
                                            "Audio data available but track not added yet"
                                        )
                                    }
                                }
                                audioEncoder?.releaseOutputBuffer(output.index)
                            }

                            is AudioEncoder.Output.FormatChanged -> {
                                val format = audioEncoder?.getOutputFormat()
                                if (format != null && !audioTrackAdded) {
                                    muxer?.addAudioTrack(format)
                                    audioTrackAdded = true
                                    Timber.d("?Audio track added to muxer")
                                }
                            }

                            is AudioEncoder.Output.TryAgain -> {
                                outputAvailable = false
                            }
                        }
                    }
                } else {
                    if (readCount == 0 && readResult == -1) {
                        Timber.e("Audio recorder returning -1 (no data)")
                    }
                    delay(5.milliseconds)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Error in audio loop")
                break
            }
        }

        Timber.d("Audio loop ended. Total reads: $readCount, Track added: $audioTrackAdded")
    }

    private fun pauseRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Recording) {
            pauseStartTime = System.currentTimeMillis()
            screenCaptureManager.pause()
            muxer?.onPause()
            _recordingState.value = RecordingState.Paused(currentState.durationMs)
            requestTileRefresh(this)

            val notification = recordingNotificationManager.createRecordingNotification(
                currentState.durationMs,
                isPaused = true,
                bypass = currentSettings?.bypassFocusIsland ?: false
            )
            recordingNotificationManager.updateNotification(
                notification,
                bypass = currentSettings?.bypassFocusIsland ?: false
            )
        }
    }

    private fun resumeRecording() {
        val currentState = _recordingState.value
        if (currentState is RecordingState.Paused) {
            pausedDuration += System.currentTimeMillis() - pauseStartTime
            muxer?.onResume()
            val surface = videoEncoder?.inputSurface
            if (surface != null) {
                screenCaptureManager.resume(surface)
            }
            _recordingState.value = RecordingState.Recording(currentState.durationMs)
            requestTileRefresh(this)

            val notification = recordingNotificationManager.createRecordingNotification(
                currentState.durationMs,
                bypass = currentSettings?.bypassFocusIsland ?: false
            )
            recordingNotificationManager.updateNotification(
                notification,
                bypass = currentSettings?.bypassFocusIsland ?: false
            )
        }
    }

    private fun checkHdrState() {
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY) ?: return

        val isHdr = display.mode.supportedHdrTypes.isNotEmpty()

        if (isHdr != lastHdrState) {
            lastHdrState = isHdr
            Timber.d("Dynamic content HDR state changed to: $isHdr")
            // With global HDR enabled, we stay in HLG mode to avoid glitches during transition.
        }
    }

    private fun stopRecording() {
        if (cleanupJob?.isActive == true) return
        if (_recordingState.value is RecordingState.Idle && startJob?.isActive != true) return
        Timber.d("Stopping recording")

        // Update state immediately for responsive UI
        _recordingState.value = RecordingState.Idle
        requestTileRefresh(this)

        cleanupJob = serviceScope.launch(Dispatchers.IO) {
            startJob?.cancelAndJoin()
            startJob = null
            recordingJob?.cancelAndJoin()
            recordingJob = null
            audioJob?.cancelAndJoin()
            audioJob = null

            // Heavy cleanup must finish even if the service is being destroyed.
            withContext(NonCancellable) {
                val currentVideoEncoder = videoEncoder
                val currentAudioEncoder = audioEncoder
                val currentAudioRecorder = audioRecorder
                val currentMuxer = muxer
                val currentRecordingOutput = recordingOutput

                videoEncoder = null
                audioEncoder = null
                audioRecorder = null
                muxer = null
                recordingOutput = null
                currentSettings = null
                startTime = 0L
                pausedDuration = 0L
                pauseStartTime = 0L

                try {
                    // Signal end of stream
                    currentVideoEncoder?.signalEndOfStream()
                    delay(100.milliseconds) // Ensure last frames are written

                    // Release resources
                    currentMuxer?.release()
                    currentVideoEncoder?.release()
                    currentAudioRecorder?.stop()
                    currentAudioEncoder?.release()
                } catch (e: Exception) {
                    Timber.e(e, "Error releasing resources")
                }
                try {
                    currentRecordingOutput?.fileDescriptor?.close()
                } catch (e: Exception) {
                    Timber.e(e, "Error closing recording output")
                }

                try {
                    screenCaptureManager.stop()
                } catch (e: Exception) {
                    Timber.e(e, "Error stopping screen capture")
                }

                // Disable touch visualization only if this service enabled it.
                if (touchVisualizationEnabled) {
                    if (privilegedOperations.setShowTouches(false)) {
                        Timber.d("Touch visualization disabled after recording")
                    }
                    touchVisualizationEnabled = false
                }

                withContext(Dispatchers.Main) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }

                Timber.d("Recording stopped, file saved: ${currentRecordingOutput?.uri}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        val displayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager
        displayManager.unregisterDisplayListener(displayListener)

        if (_recordingState.value !is RecordingState.Idle) {
            stopRecording()
        }
        recordingNotificationManager.release()
        val cleanup = cleanupJob
        if (cleanup?.isActive == true) {
            cleanup.invokeOnCompletion {
                serviceScope.cancel()
            }
        } else {
            serviceScope.cancel()
        }
        Timber.d("RecorderService destroyed")
    }

    private fun hasRecordAudioPermission(): Boolean =
        checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun AudioSource.usesMicrophone(): Boolean =
        this == AudioSource.MICROPHONE || this == AudioSource.BOTH

    inner class RecorderBinder : Binder() {
        fun getService(): RecorderService = this@RecorderService
    }
}
