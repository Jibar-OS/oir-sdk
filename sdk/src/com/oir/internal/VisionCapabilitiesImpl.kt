/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.VisionCapabilities
import com.oir.models.DetectedObject
import com.oir.models.EmbeddingVector
import com.oir.models.ImageDescription
import com.oir.models.TokenChunk
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

internal class VisionCapabilitiesImpl(
    private val client: OirServiceClient,
) : VisionCapabilities {

    private val adapter: OirBinderAdapter get() = client.adapter()

    override suspend fun describe(
        imagePath: String,
        prompt: String,
        retryThrottle: Int,
    ): ImageDescription = retryOnThrottle(retryThrottle) {
        // vision.describe wire format: "<imagePath> | <prompt>" when both
        // present, bare "<imagePath>" when prompt is empty. Worker
        // splits on " | ".
        val wireFormat = if (prompt.isEmpty()) imagePath else "$imagePath | $prompt"
        val buf = StringBuilder()
        suspendCancellableCoroutine<ImageDescription> { cont ->
            val startedAt = System.currentTimeMillis()
            var firstTokenMs = -1L
            val handle = adapter.submitTokenStream(
                capability = "vision.describe",
                prompt     = wireFormat,
                options    = TokenStreamOptions(),
                onStart    = { },
                onToken    = { text, _ ->
                    if (firstTokenMs < 0) firstTokenMs = System.currentTimeMillis() - startedAt
                    buf.append(text)
                },
                onComplete = { totalMs ->
                    if (cont.isActive) cont.resume(
                        ImageDescription(
                            text         = buf.toString(),
                            firstTokenMs = firstTokenMs.coerceAtLeast(0L),
                            totalMs      = totalMs,
                        ),
                    )
                },
                onError    = { code, msg ->
                    if (cont.isActive) cont.resumeWithException(
                        ErrorMapping.toException(code, msg, "vision.describe"),
                    )
                },
            )
            cont.invokeOnCancellation { adapter.cancel(handle) }
        }
    }

    override fun describeStream(
        imagePath: String,
        prompt: String,
    ): Flow<TokenChunk> = callbackFlow {
        val wireFormat = if (prompt.isEmpty()) imagePath else "$imagePath | $prompt"
        val handle = adapter.submitTokenStream(
            capability = "vision.describe",
            prompt     = wireFormat,
            options    = TokenStreamOptions(),
            onStart    = { },
            onToken    = { text, idx -> trySend(TokenChunk(text, idx)) },
            onComplete = { _ -> close() },
            onError    = { code, msg ->
                close(ErrorMapping.toException(code, msg, "vision.describe"))
            },
        )
        awaitClose { adapter.cancel(handle) }
    }

    override suspend fun embed(imagePath: String, retryThrottle: Int): EmbeddingVector =
        retryOnThrottle(retryThrottle) {
            // vision.embed uses the Vector shape; the wire format passes the
            // imagePath through the text parameter (see OIRService's
            // submitEmbedText routing for vision.embed capabilities).
            val v = VectorAwaiter.await(adapter, capability = "vision.embed", input = imagePath)
            EmbeddingVector(v)
        }

    override suspend fun detect(imagePath: String, retryThrottle: Int): List<DetectedObject> =
        retryOnThrottle(retryThrottle) {
            BoundingBoxAwaiter.await(adapter, capability = "vision.detect", imagePath = imagePath)
        }

    override suspend fun ocr(imagePath: String, retryThrottle: Int): List<DetectedObject> =
        retryOnThrottle(retryThrottle) {
            BoundingBoxAwaiter.await(adapter, capability = "vision.ocr", imagePath = imagePath)
        }
}

/**
 * Suspends until a BoundingBoxes-shape capability emits. Re-packs the
 * worker's flat parallel arrays (xs[], ys[], widths[], heights[],
 * labelsPerBox[], labelsFlat[], scoresFlat[]) into a list of
 * [DetectedObject]s preserving per-box label ordering.
 */
internal object BoundingBoxAwaiter {

    suspend fun await(
        adapter: OirBinderAdapter,
        capability: String,
        imagePath: String,
    ): List<DetectedObject> = suspendCancellableCoroutine { cont ->
        val handle = adapter.submitBoundingBoxes(
            capability = capability,
            imagePath  = imagePath,
            onBoxes    = { xs, ys, widths, heights, labelsPerBox, labelsFlat, scoresFlat ->
                if (!cont.isActive) return@submitBoundingBoxes
                val out = ArrayList<DetectedObject>(xs.size)
                var flatIdx = 0
                for (i in xs.indices) {
                    val labelCount = labelsPerBox.getOrNull(i) ?: 1
                    // Take first label/score as the canonical ones; more than
                    // one per box is rare outside multi-label OCR future use.
                    val label = labelsFlat.getOrNull(flatIdx) ?: ""
                    val score = scoresFlat.getOrNull(flatIdx) ?: 0f
                    out += DetectedObject(
                        x      = xs[i],
                        y      = ys[i],
                        width  = widths[i],
                        height = heights[i],
                        label  = label,
                        score  = score,
                    )
                    flatIdx += labelCount.coerceAtLeast(1)
                }
                cont.resume(out)
            },
            onError    = { code, msg ->
                if (cont.isActive) cont.resumeWithException(
                    ErrorMapping.toException(code, msg, capability),
                )
            },
        )
        cont.invokeOnCancellation { adapter.cancel(handle) }
    }
}
