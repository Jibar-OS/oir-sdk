/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.models

/**
 * One PCM audio chunk streamed from audio.synthesize. The SDK holds no
 * pool — apps consume the buffer or copy bytes they need to keep.
 *
 * [encoding] follows the worker-side convention in
 * `IOIRAudioStreamCallback`:
 *
 *   | Code | Meaning         |
 *   |------|-----------------|
 *   | 1    | PCM_8BIT        |
 *   | 2    | PCM_16BIT       |
 *   | 3    | PCM_24BIT_PACKED|
 *   | 4    | PCM_FLOAT (32)  |
 *
 * Piper voices stream [encoding] = 4 (float32) at 22050 Hz mono today.
 */
public data class AudioChunk(
    val pcm: ByteArray,
    val sampleRateHz: Int,
    val channels: Int,
    val encoding: Int,
    val isLast: Boolean,
) {
    override fun equals(other: Any?): Boolean = this === other ||
            (other is AudioChunk
             && pcm.contentEquals(other.pcm)
             && sampleRateHz == other.sampleRateHz
             && channels == other.channels
             && encoding == other.encoding
             && isLast == other.isLast)
    override fun hashCode(): Int {
        var h = pcm.contentHashCode()
        h = 31 * h + sampleRateHz
        h = 31 * h + channels
        h = 31 * h + encoding
        h = 31 * h + if (isLast) 1 else 0
        return h
    }
}

/**
 * Terminal result of a non-streaming [com.oir.AudioCapabilities.synthesize]
 * call — the fully collected PCM plus timing telemetry.
 */
public data class AudioBuffer(
    val pcm: ByteArray,
    val sampleRateHz: Int,
    val channels: Int,
    val encoding: Int,
    val totalMs: Long,
) {
    override fun equals(other: Any?): Boolean = this === other ||
            (other is AudioBuffer
             && pcm.contentEquals(other.pcm)
             && sampleRateHz == other.sampleRateHz
             && channels == other.channels
             && encoding == other.encoding
             && totalMs == other.totalMs)
    override fun hashCode(): Int {
        var h = pcm.contentHashCode()
        h = 31 * h + sampleRateHz
        h = 31 * h + channels
        h = 31 * h + encoding
        h = 31 * h + totalMs.toInt()
        return h
    }
}

/** Transcript chunk streamed from audio.transcribe. */
public data class TranscriptChunk(
    val text: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
)

/** Terminal transcript result (non-streaming `transcribe()` call). */
public data class Transcript(
    val text: String,
    val totalMs: Long,
)

/**
 * VAD state transition emitted from audio.vad on every analysis window
 * (typically 32 ms at 16 kHz). [timestampMs] is the window's anchor
 * time from the start of the audio stream / file.
 */
public data class VadState(
    val isVoicePresent: Boolean,
    val timestampMs: Long,
)
