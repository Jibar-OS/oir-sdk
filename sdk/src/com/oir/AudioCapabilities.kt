/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir

import com.oir.models.AudioBuffer
import com.oir.models.AudioChunk
import com.oir.models.Transcript
import com.oir.models.TranscriptChunk
import com.oir.models.VadState
import kotlinx.coroutines.flow.Flow

/**
 * Audio-namespace capabilities exposed through [OpenIntelligence.audio].
 *
 * Requires `oir.permission.USE_AUDIO` (dangerous). Apps must request
 * it via the platform runtime-permissions flow; [VadCapability] lives
 * under this permission too.
 */
public interface AudioCapabilities {

    /** Voice-activity-detection sub-facade — stream / file both supported. */
    public val vad: VadCapability

    /**
     * Transcribe the audio at [pcmPath] (PCM16 LE 16 kHz mono file).
     * Returns the fully-assembled transcript.
     */
    public suspend fun transcribe(pcmPath: String, retryThrottle: Int = 0): Transcript

    /** Streaming variant — transcript chunks arrive as whisper emits them. */
    public fun transcribeStream(pcmPath: String): Flow<TranscriptChunk>

    /**
     * Synthesize [text] to PCM audio. Returns the full [AudioBuffer]
     * once the model completes. For interactive / conversational UX,
     * prefer [synthesizeStream] so playback can start before inference
     * finishes.
     *
     * Throws [com.oir.errors.OirCapabilityUnavailableException] if the
     * OEM has not baked a Piper voice + `<voice>.phonemes.json`
     * companion on this device.
     */
    public suspend fun synthesize(text: String, retryThrottle: Int = 0): AudioBuffer

    /** Streaming variant of [synthesize] — 100 ms PCM chunks. */
    public fun synthesizeStream(text: String): Flow<AudioChunk>
}

/**
 * Voice-activity-detection sub-facade. Lives under [AudioCapabilities.vad]
 * rather than the top-level Audio namespace because OEMs will want to
 * swap the VAD model independently of the transcribe model.
 */
public interface VadCapability {

    /**
     * Run VAD over the audio file at [pcmPath] and emit one [VadState]
     * per analysis window (32 ms at 16 kHz by default). Cold Flow:
     * each collector opens a fresh VAD session with its own internal
     * LSTM state.
     */
    public fun states(pcmPath: String): Flow<VadState>
}
