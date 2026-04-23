/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.AudioCapabilities
import com.oir.VadCapability
import com.oir.models.AudioBuffer
import com.oir.models.AudioChunk
import com.oir.models.Transcript
import com.oir.models.TranscriptChunk
import com.oir.models.VadState
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AudioCapabilitiesImpl(
    private val client: OirServiceClient,
) : AudioCapabilities {

    private val adapter: OirBinderAdapter get() = client.adapter()

    override val vad: VadCapability = VadCapabilityImpl(client)

    override suspend fun transcribe(pcmPath: String): Transcript {
        val buf = StringBuilder()
        // audio.transcribe is wired through the TokenStream shape on the
        // worker (whisper emits segments as tokens) so we reuse the
        // token-stream adapter method.
        return suspendCancellableCoroutine { cont ->
            val startedAt = System.currentTimeMillis()
            val handle = adapter.submitTokenStream(
                capability = "audio.transcribe",
                prompt     = pcmPath,     // worker treats prompt as the audio path for audio.*
                options    = TokenStreamOptions(),
                onStart    = { },
                onToken    = { text, _ -> buf.append(text) },
                onComplete = { totalMs ->
                    if (cont.isActive) cont.resume(
                        Transcript(text = buf.toString(), totalMs = totalMs),
                    )
                },
                onError    = { code, msg ->
                    if (cont.isActive) cont.resumeWithException(
                        ErrorMapping.toException(code, msg, "audio.transcribe"),
                    )
                },
            )
            cont.invokeOnCancellation { adapter.cancel(handle) }
        }
    }

    override fun transcribeStream(pcmPath: String): Flow<TranscriptChunk> = callbackFlow {
        val handle = adapter.submitTokenStream(
            capability = "audio.transcribe",
            prompt     = pcmPath,
            options    = TokenStreamOptions(),
            onStart    = { },
            onToken    = { text, _ -> trySend(TranscriptChunk(text)) },
            onComplete = { _ -> close() },
            onError    = { code, msg ->
                close(ErrorMapping.toException(code, msg, "audio.transcribe"))
            },
        )
        awaitClose { adapter.cancel(handle) }
    }

    override suspend fun synthesize(text: String): AudioBuffer = suspendCancellableCoroutine { cont ->
        val chunks = mutableListOf<AudioChunk>()
        val handle = adapter.submitAudioStream(
            capability = "audio.synthesize",
            text       = text,
            onChunk    = { chunk -> chunks.add(chunk) },
            onComplete = { totalMs ->
                if (cont.isActive) {
                    val merged = mergeChunks(chunks, totalMs)
                    cont.resume(merged)
                }
            },
            onError    = { code, msg ->
                if (cont.isActive) cont.resumeWithException(
                    ErrorMapping.toException(code, msg, "audio.synthesize"),
                )
            },
        )
        cont.invokeOnCancellation { adapter.cancel(handle) }
    }

    override fun synthesizeStream(text: String): Flow<AudioChunk> = callbackFlow {
        val handle = adapter.submitAudioStream(
            capability = "audio.synthesize",
            text       = text,
            onChunk    = { chunk -> trySend(chunk) },
            onComplete = { _ -> close() },
            onError    = { code, msg ->
                close(ErrorMapping.toException(code, msg, "audio.synthesize"))
            },
        )
        awaitClose { adapter.cancel(handle) }
    }

    private fun mergeChunks(chunks: List<AudioChunk>, totalMs: Long): AudioBuffer {
        if (chunks.isEmpty()) return AudioBuffer(
            pcm          = ByteArray(0),
            sampleRateHz = 0,
            channels     = 0,
            encoding     = 0,
            totalMs      = totalMs,
        )
        val first    = chunks.first()
        val total    = chunks.sumOf { it.pcm.size }
        val merged   = ByteArray(total)
        var offset   = 0
        for (c in chunks) {
            System.arraycopy(c.pcm, 0, merged, offset, c.pcm.size)
            offset += c.pcm.size
        }
        return AudioBuffer(
            pcm          = merged,
            sampleRateHz = first.sampleRateHz,
            channels     = first.channels,
            encoding     = first.encoding,
            totalMs      = totalMs,
        )
    }
}

internal class VadCapabilityImpl(
    private val client: OirServiceClient,
) : VadCapability {

    private val adapter: OirBinderAdapter get() = client.adapter()

    override fun states(pcmPath: String): Flow<VadState> = callbackFlow {
        val handle = adapter.submitVadStates(
            capability = "audio.vad",
            pcmPath    = pcmPath,
            onState    = { s -> trySend(s) },
            onComplete = { close() },
            onError    = { code, msg ->
                close(ErrorMapping.toException(code, msg, "audio.vad"))
            },
        )
        awaitClose { adapter.cancel(handle) }
    }
}
