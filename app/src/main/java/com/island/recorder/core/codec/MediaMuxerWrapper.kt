package com.island.recorder.core.codec

import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import timber.log.Timber
import java.io.FileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

/**
 * Combines video and audio tracks into MP4 container with HDR metadata support
 */
class MediaMuxerWrapper(
    private val output: FileDescriptor,
    private val displayName: String
) {

    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var isMuxerStarted = false
    private var videoFormatReceived = false
    private var audioFormatReceived = false
    private var videoFormat: MediaFormat? = null

    private companion object {
        // HDR transfer functions (matching MediaFormat constants)
        const val COLOR_TRANSFER_HLG = 7
        const val COLOR_TRANSFER_PQ = 6
        const val DIAGNOSTIC_MUXER_TAG = "IR-Muxer"
        const val VIDEO_SAMPLE_LOG_WINDOW_US = 2_000_000L
    }

    /**
     * Initialize the muxer
     */
    fun prepare() {
        try {
            mediaMuxer = MediaMuxer(
                output,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )
            Timber.d("MediaMuxer initialized: $displayName")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize MediaMuxer")
            throw e
        }
    }

    /**
     * Add video track with optional HDR metadata
     */
    fun addVideoTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")

        // Store format for HDR metadata injection
        videoFormat = format

        // Add HDR metadata for H.265/HEVC if HDR is active
        injectHdrMetadata(format)

        videoTrackIndex = muxer.addTrack(format)
        videoFormatReceived = true
        Timber.d("Video track added: $videoTrackIndex, HDR=${isHdrFormat(format)}")

        tryStartMuxer()
        return videoTrackIndex
    }

    /**
     * Inject HDR metadata into the video format for MP4 container
     */
    private fun injectHdrMetadata(format: MediaFormat) {
        if (!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return

        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)

        // ONLY inject static metadata for PQ (ST2084). 
        // Adding static metadata to HLG is non-standard and causes severe color shifts 
        // because decoders try to tone-map HLG as if it were PQ.
        if (transfer != COLOR_TRANSFER_PQ) return

        Timber.d("Injecting static metadata for PQ content")

        // HDR metadata using KEY_HDR_STATIC_INFO (API 24+)
        // Mastering display color volume (Rec. ITU-R BT.2020)
        // G(8500, 39850), B(65535, 2300), R(35400, 14600), White(15635, 16450), L(10000000, 50)
        val masteringData = createMasteringDisplayData(
            rX = 35400, rY = 14600,
            gX = 8500, gY = 39850,
            bX = 65535, bY = 2300,
            whiteX = 15635, whiteY = 16450,
            maxLum = 10000000, minLum = 50
        )

        // Content light level (MaxCLL, MaxFALL)
        val contentLightData = createContentLightLevelData(maxCLL = 1000, maxFALL = 400)

        // Combine descriptors into one ByteBuffer
        val staticInfo = ByteBuffer.allocate(masteringData.limit() + contentLightData.limit())
        staticInfo.order(ByteOrder.LITTLE_ENDIAN)
        staticInfo.put(masteringData)
        staticInfo.put(contentLightData)
        staticInfo.flip()

        format.setByteBuffer(MediaFormat.KEY_HDR_STATIC_INFO, staticInfo)
    }

    /**
     * Create mastering display color volume data (25 bytes: 1 byte ID + 24 bytes data)
     */
    private fun createMasteringDisplayData(
        rX: Int, rY: Int, gX: Int, gY: Int, bX: Int, bY: Int,
        whiteX: Int, whiteY: Int, maxLum: Int, minLum: Int
    ): ByteBuffer {
        val data = ByteBuffer.allocate(25)
        data.order(ByteOrder.LITTLE_ENDIAN)
        data.put(0.toByte()) // Descriptor ID 0 (Mastering Display Color Volume)
        data.putShort(gX.toShort()); data.putShort(gY.toShort())
        data.putShort(bX.toShort()); data.putShort(bY.toShort())
        data.putShort(rX.toShort()); data.putShort(rY.toShort())
        data.putShort(whiteX.toShort()); data.putShort(whiteY.toShort())
        data.putInt(maxLum)
        data.putInt(minLum)
        data.flip()
        return data
    }

    /**
     * Create content light level data (5 bytes: 1 byte ID + 4 bytes data)
     */
    private fun createContentLightLevelData(maxCLL: Int, maxFALL: Int): ByteBuffer {
        val data = ByteBuffer.allocate(5)
        data.order(ByteOrder.LITTLE_ENDIAN)
        data.put(1.toByte()) // Descriptor ID 1 (Content Light Level)
        data.putShort(maxCLL.toShort())
        data.putShort(maxFALL.toShort())
        data.flip()
        return data
    }

    /**
     * Check if format indicates HDR content
     */
    private fun isHdrFormat(format: MediaFormat): Boolean {
        if (!format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) return false
        val transfer = format.getInteger(MediaFormat.KEY_COLOR_TRANSFER)
        return transfer == COLOR_TRANSFER_HLG || transfer == COLOR_TRANSFER_PQ
    }

    /**
     * Add audio track
     */
    fun addAudioTrack(format: MediaFormat): Int {
        val muxer = mediaMuxer ?: throw IllegalStateException("Muxer not initialized")

        audioTrackIndex = muxer.addTrack(format)
        audioFormatReceived = true
        Timber.d("Audio track added: $audioTrackIndex")

        tryStartMuxer()
        return audioTrackIndex
    }

    private var expectAudio = false
    private var recordingStartTimeUs = -1L
    private var timestampOffsetUs = 0L
    private var pauseStartUs = 0L
    private var videoSampleWindowStartUs = -1L
    private var lastVideoSamplePtsUs = -1L
    private var videoSamplesInWindow = 0
    private var videoMinDeltaUs = Long.MAX_VALUE
    private var videoMaxDeltaUs = 0L
    private var totalVideoSamplesWritten = 0L
    private var totalVideoBytesWritten = 0L
    private var totalVideoKeyFrames = 0L
    private var totalVideoCodecConfigSamples = 0L
    private var totalVideoWriteFailures = 0L

    /**
     * Mark the start of a pause — samples between pause and resume will be dropped
     */
    fun onPause() {
        pauseStartUs = System.nanoTime() / 1000
    }

    /**
     * Mark the end of a pause — accumulate the gap into the timestamp offset
     */
    fun onResume() {
        if (pauseStartUs > 0) {
            timestampOffsetUs += (System.nanoTime() / 1000) - pauseStartUs
            pauseStartUs = 0
        }
    }

    /**
     * Set if audio track is expected
     */
    fun setAudioExpected(expected: Boolean) {
        expectAudio = expected
    }

    /**
     * MediaCodec surface input timestamps are based on the system monotonic clock.
     * Normalize samples to the moment VirtualDisplay is attached so the MP4 does
     * not preserve startup latency as an empty leading timeline segment.
     */
    fun setRecordingStartTimestampUs(timestampUs: Long) {
        recordingStartTimeUs = timestampUs
        timestampOffsetUs = 0L
        pauseStartUs = 0L
    }

    /**
     * Start muxer when all tracks are added
     */
    private fun tryStartMuxer() {
        if (!isMuxerStarted && videoFormatReceived && (!expectAudio || audioFormatReceived)) {
            // Start muxer when video track is ready (audio is optional)
            mediaMuxer?.start()
            isMuxerStarted = true
            resetVideoSampleDiagnostics()
            Timber.tag(DIAGNOSTIC_MUXER_TAG).d(
                "started videoTrack=$videoTrackIndex audioTrack=$audioTrackIndex " +
                    "expectAudio=$expectAudio displayName=$displayName"
            )
        }
    }

    /**
     * Write video sample
     */
    fun writeVideoSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Timber.w("Muxer not started, dropping video sample")
            return
        }

        if (videoTrackIndex < 0) {
            Timber.w("Video track not added, dropping sample")
            return
        }

        try {
            val adjusted = MediaCodec.BufferInfo()
            adjusted.set(
                bufferInfo.offset, bufferInfo.size,
                adjustedPresentationTimeUs(bufferInfo.presentationTimeUs), bufferInfo.flags
            )
            mediaMuxer?.writeSampleData(videoTrackIndex, buffer, adjusted)
            recordVideoSampleWrite(adjusted)
        } catch (e: Exception) {
            totalVideoWriteFailures += 1
            Timber.tag(DIAGNOSTIC_MUXER_TAG).e(
                e,
                "videoWriteFailed size=${bufferInfo.size} ptsUs=${bufferInfo.presentationTimeUs} " +
                    "offsetUs=$timestampOffsetUs flags=${bufferInfo.flags} failures=$totalVideoWriteFailures"
            )
            Timber.e(e, "Error writing video sample")
        }
    }

    private fun resetVideoSampleDiagnostics() {
        videoSampleWindowStartUs = -1L
        lastVideoSamplePtsUs = -1L
        videoSamplesInWindow = 0
        videoMinDeltaUs = Long.MAX_VALUE
        videoMaxDeltaUs = 0L
        totalVideoSamplesWritten = 0L
        totalVideoBytesWritten = 0L
        totalVideoKeyFrames = 0L
        totalVideoCodecConfigSamples = 0L
        totalVideoWriteFailures = 0L
    }

    private fun recordVideoSampleWrite(bufferInfo: MediaCodec.BufferInfo) {
        val ptsUs = bufferInfo.presentationTimeUs
        totalVideoSamplesWritten += 1
        totalVideoBytesWritten += bufferInfo.size.toLong()

        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
            totalVideoKeyFrames += 1
        }
        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            totalVideoCodecConfigSamples += 1
        }

        if (videoSampleWindowStartUs < 0) {
            videoSampleWindowStartUs = ptsUs
            lastVideoSamplePtsUs = ptsUs
            videoSamplesInWindow = 1
            Timber.tag(DIAGNOSTIC_MUXER_TAG).d(
                "videoWrite firstPtsUs=$ptsUs size=${bufferInfo.size} flags=${bufferInfo.flags}"
            )
            return
        }

        val deltaUs = ptsUs - lastVideoSamplePtsUs
        if (deltaUs > 0) {
            videoMinDeltaUs = minOf(videoMinDeltaUs, deltaUs)
            videoMaxDeltaUs = maxOf(videoMaxDeltaUs, deltaUs)
        } else {
            Timber.tag(DIAGNOSTIC_MUXER_TAG).w(
                "Non-increasing muxer video PTS: last=$lastVideoSamplePtsUs current=$ptsUs"
            )
        }

        lastVideoSamplePtsUs = ptsUs
        videoSamplesInWindow += 1

        val windowDurationUs = ptsUs - videoSampleWindowStartUs
        if (windowDurationUs < VIDEO_SAMPLE_LOG_WINDOW_US || videoSamplesInWindow < 2) return

        val sampleIntervals = videoSamplesInWindow - 1
        val fps = sampleIntervals * 1_000_000.0 / windowDurationUs
        val avgDeltaUs = windowDurationUs.toDouble() / sampleIntervals
        val minDelta = if (videoMinDeltaUs == Long.MAX_VALUE) 0L else videoMinDeltaUs

        Timber.tag(DIAGNOSTIC_MUXER_TAG).d(
            String.format(
                Locale.US,
                "videoWrite windowFps=%.2f samples=%d total=%d windowUs=%d " +
                    "avgDeltaUs=%.1f minDeltaUs=%d maxDeltaUs=%d lastPtsUs=%d " +
                    "bytes=%d keyFrames=%d codecConfig=%d failures=%d",
                fps,
                videoSamplesInWindow,
                totalVideoSamplesWritten,
                windowDurationUs,
                avgDeltaUs,
                minDelta,
                videoMaxDeltaUs,
                ptsUs,
                totalVideoBytesWritten,
                totalVideoKeyFrames,
                totalVideoCodecConfigSamples,
                totalVideoWriteFailures
            )
        )

        videoSampleWindowStartUs = ptsUs
        videoSamplesInWindow = 1
        videoMinDeltaUs = Long.MAX_VALUE
        videoMaxDeltaUs = 0L
    }

    /**
     * Write audio sample
     */
    fun writeAudioSample(buffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) {
            Timber.w("Muxer not started, dropping audio sample")
            return
        }

        if (audioTrackIndex < 0) {
            Timber.w("Audio track not added, dropping sample")
            return
        }

        try {
            val adjusted = MediaCodec.BufferInfo()
            adjusted.set(
                bufferInfo.offset, bufferInfo.size,
                adjustedPresentationTimeUs(bufferInfo.presentationTimeUs), bufferInfo.flags
            )
            mediaMuxer?.writeSampleData(audioTrackIndex, buffer, adjusted)
        } catch (e: Exception) {
            Timber.e(e, "Error writing audio sample")
        }
    }

    /**
     * Stop and release muxer
     */
    fun release() {
        try {
            if (isMuxerStarted) {
                mediaMuxer?.stop()
                isMuxerStarted = false
            }

            mediaMuxer?.release()
            mediaMuxer = null

            Timber.tag(DIAGNOSTIC_MUXER_TAG).d(
                "released videoSamples=$totalVideoSamplesWritten videoBytes=$totalVideoBytesWritten " +
                    "keyFrames=$totalVideoKeyFrames codecConfig=$totalVideoCodecConfigSamples " +
                    "failures=$totalVideoWriteFailures lastPtsUs=$lastVideoSamplePtsUs"
            )
            Timber.d("MediaMuxer released")
        } catch (e: Exception) {
            Timber.e(e, "Error releasing MediaMuxer")
        }
    }

    /**
     * Check if muxer is started
     */
    fun isStarted(): Boolean {
        return isMuxerStarted
    }

    private fun adjustedPresentationTimeUs(sampleTimeUs: Long): Long {
        if (recordingStartTimeUs < 0L) {
            recordingStartTimeUs = sampleTimeUs
        }
        return (sampleTimeUs - recordingStartTimeUs - timestampOffsetUs).coerceAtLeast(0L)
    }
}
