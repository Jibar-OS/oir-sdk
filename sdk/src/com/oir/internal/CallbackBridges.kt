/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.errors.OirException
import com.oir.errors.OirInternalException
import com.oir.models.ScoreVector
import com.oir.models.TextCompletion
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Suspends until the token-stream capability completes, collecting all
 * emitted tokens into a single [TextCompletion]. Used by the non-
 * streaming `complete()` / `translate()` entry points where the app
 * doesn't need per-token UI updates.
 *
 * Cancellation of the calling coroutine scope is forwarded to the
 * worker via [OirBinderAdapter.cancel].
 */
internal object TokenStreamCollector {

    suspend fun awaitFull(
        adapter: OirBinderAdapter,
        capability: String,
        prompt: String,
        options: TokenStreamOptions,
    ): TextCompletion = suspendCancellableCoroutine { cont ->
        val buf = StringBuilder()
        var firstTokenMs = -1L
        val startedAt = System.currentTimeMillis()
        val handle = adapter.submitTokenStream(
            capability = capability,
            prompt     = prompt,
            options    = options,
            onStart    = { /* no-op */ },
            onToken    = { text, _ ->
                if (firstTokenMs < 0) firstTokenMs = System.currentTimeMillis() - startedAt
                buf.append(text)
            },
            onComplete = { totalMs ->
                if (cont.isActive) cont.resume(
                    TextCompletion(
                        text         = buf.toString(),
                        firstTokenMs = firstTokenMs.coerceAtLeast(0L),
                        totalMs      = totalMs,
                    ),
                )
            },
            onError    = { code, message ->
                if (cont.isActive) cont.resumeWithException(
                    ErrorMapping.toException(code, message, capability),
                )
            },
        )
        cont.invokeOnCancellation { adapter.cancel(handle) }
    }
}

/**
 * Suspends until a Vector-shape capability (text.embed, text.classify,
 * vision.embed) emits its one-shot [FloatArray] result.
 */
internal object VectorAwaiter {

    suspend fun await(
        adapter: OirBinderAdapter,
        capability: String,
        input: String,
    ): FloatArray = suspendCancellableCoroutine { cont ->
        val handle = adapter.submitVector(
            capability = capability,
            text       = input,
            onVector   = { v -> if (cont.isActive) cont.resume(v) },
            onError    = { code, msg ->
                if (cont.isActive) cont.resumeWithException(
                    ErrorMapping.toException(code, msg, capability),
                )
            },
        )
        cont.invokeOnCancellation { adapter.cancel(handle) }
    }
}

/**
 * Suspends until text.rerank emits its per-candidate score vector.
 * Separate from [VectorAwaiter] because rerank takes query+candidates
 * instead of a single text.
 */
internal object RerankAwaiter {

    suspend fun await(
        adapter: OirBinderAdapter,
        capability: String,
        query: String,
        candidates: List<String>,
    ): ScoreVector = suspendCancellableCoroutine { cont ->
        val handle = adapter.submitRerank(
            capability = capability,
            query      = query,
            candidates = candidates,
            onVector   = { v -> if (cont.isActive) cont.resume(ScoreVector(v)) },
            onError    = { code, msg ->
                if (cont.isActive) cont.resumeWithException(
                    ErrorMapping.toException(code, msg, capability),
                )
            },
        )
        cont.invokeOnCancellation { adapter.cancel(handle) }
    }
}
