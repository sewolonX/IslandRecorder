package com.island.recorder.domain.recording.provider

import com.island.recorder.domain.recording.model.RecordingOutput

interface RecordingStorageProvider {
    suspend fun createRecordingOutput(): RecordingOutput

    suspend fun delete(output: RecordingOutput)
}
