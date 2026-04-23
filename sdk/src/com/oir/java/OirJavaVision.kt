/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.java

import com.oir.OpenIntelligence
import com.oir.models.DetectedObject
import com.oir.models.EmbeddingVector
import com.oir.models.ImageDescription
import com.oir.models.TokenChunk
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
 * Java-facing entry point for [OpenIntelligence.vision]. Same pattern as the other
 * `OirJava*` façades.
 *
 * Image parameters are file paths on the local file system. Apps with
 * a Bitmap or a Uri write to a temp file first — the worker runs in
 * a separate process and cannot see the caller's heap.
 */
public object OirJavaVision {

    private val visionScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @JvmStatic
    public fun describe(imagePath: String): CompletableFuture<ImageDescription> =
        describe(imagePath, "")

    @JvmStatic
    public fun describe(
        imagePath: String,
        prompt: String,
    ): CompletableFuture<ImageDescription> {
        val cf = CompletableFuture<ImageDescription>()
        val job = visionScope.launch {
            try { cf.complete(OpenIntelligence.vision.describe(imagePath, prompt)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun describeStream(
        imagePath: String,
        prompt: String,
        onChunk:    java.util.function.Consumer<TokenChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job = visionScope.launch {
            OpenIntelligence.vision.describeStream(imagePath, prompt)
                .onEach { onChunk.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { }
        }
        return AutoCloseable { job.cancel() }
    }

    @JvmStatic
    public fun embed(imagePath: String): CompletableFuture<EmbeddingVector> {
        val cf = CompletableFuture<EmbeddingVector>()
        val job = visionScope.launch {
            try { cf.complete(OpenIntelligence.vision.embed(imagePath)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun detect(imagePath: String): CompletableFuture<List<DetectedObject>> {
        val cf = CompletableFuture<List<DetectedObject>>()
        val job = visionScope.launch {
            try { cf.complete(OpenIntelligence.vision.detect(imagePath)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun ocr(imagePath: String): CompletableFuture<List<DetectedObject>> {
        val cf = CompletableFuture<List<DetectedObject>>()
        val job = visionScope.launch {
            try { cf.complete(OpenIntelligence.vision.ocr(imagePath)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun shutdown() { visionScope.cancel() }
}
