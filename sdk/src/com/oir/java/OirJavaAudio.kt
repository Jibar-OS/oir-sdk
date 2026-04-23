/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.java

import com.oir.OpenIntelligence
import com.oir.models.AudioBuffer
import com.oir.models.AudioChunk
import com.oir.models.Transcript
import com.oir.models.TranscriptChunk
import com.oir.models.VadState
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Java-facing entry point for [OpenIntelligence.audio]. Same pattern as
 * [OirJavaText] — `suspend` → [CompletableFuture], `Flow` → callback
 * triple + [AutoCloseable] for cancellation.
 *
 * VAD is nested: [vadStates] takes a PCM path and emits one
 * [VadState] per analysis window via [onState].
 */
public object OirJavaAudio {

    private val audioScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @JvmStatic
    public fun transcribe(pcmPath: String): CompletableFuture<Transcript> {
        val cf = CompletableFuture<Transcript>()
        val job = audioScope.launch {
            try { cf.complete(OpenIntelligence.audio.transcribe(pcmPath)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun transcribeStream(
        pcmPath: String,
        onChunk:    java.util.function.Consumer<TranscriptChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job = audioScope.launch {
            OpenIntelligence.audio.transcribeStream(pcmPath)
                .onEach { onChunk.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { }
        }
        return AutoCloseable { job.cancel() }
    }

    @JvmStatic
    public fun synthesize(text: String): CompletableFuture<AudioBuffer> {
        val cf = CompletableFuture<AudioBuffer>()
        val job = audioScope.launch {
            try { cf.complete(OpenIntelligence.audio.synthesize(text)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun synthesizeStream(
        text: String,
        onChunk:    java.util.function.Consumer<AudioChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job = audioScope.launch {
            OpenIntelligence.audio.synthesizeStream(text)
                .onEach { onChunk.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { }
        }
        return AutoCloseable { job.cancel() }
    }

    @JvmStatic
    public fun vadStates(
        pcmPath: String,
        onState:    java.util.function.Consumer<VadState>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job = audioScope.launch {
            OpenIntelligence.audio.vad.states(pcmPath)
                .onEach { onState.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { }
        }
        return AutoCloseable { job.cancel() }
    }

    @JvmStatic
    public fun shutdown() { audioScope.cancel() }
}
