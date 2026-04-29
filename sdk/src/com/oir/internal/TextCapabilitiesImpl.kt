/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.TextCapabilities
import com.oir.models.CompletionOptions
import com.oir.models.ScoreVector
import com.oir.models.TextCompletion
import com.oir.models.TokenChunk
import com.oir.models.TranslationOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class TextCapabilitiesImpl(
    private val client: OirServiceClient,
) : TextCapabilities {

    private val adapter: OirBinderAdapter get() = client.adapter()

    override suspend fun complete(
        prompt: String,
        options: CompletionOptions,
        retryThrottle: Int,
    ): TextCompletion = retryOnThrottle(retryThrottle) {
        TokenStreamCollector.awaitFull(
            adapter    = adapter,
            capability = "text.complete",
            prompt     = prompt,
            options    = options.toTokenStream(),
        )
    }

    override fun completeStream(
        prompt: String,
        options: CompletionOptions,
    ): Flow<TokenChunk> = callbackFlow {
        val handle = adapter.submitTokenStream(
            capability = "text.complete",
            prompt     = prompt,
            options    = options.toTokenStream(),
            onStart    = { /* no-op — Flow is already hot by this point */ },
            onToken    = { text, idx -> trySend(TokenChunk(text, idx)) },
            onComplete = { _ -> close() },
            onError    = { code, msg -> close(ErrorMapping.toException(code, msg, "text.complete")) },
        )
        awaitClose { adapter.cancel(handle) }
    }

    override suspend fun embed(text: String, retryThrottle: Int): FloatArray =
        retryOnThrottle(retryThrottle) {
            VectorAwaiter.await(adapter, capability = "text.embed", input = text)
        }

    override suspend fun classify(text: String, retryThrottle: Int): ScoreVector =
        retryOnThrottle(retryThrottle) {
            ScoreVector(VectorAwaiter.await(adapter, capability = "text.classify", input = text))
        }

    override suspend fun rerank(
        query: String,
        candidates: List<String>,
        retryThrottle: Int,
    ): ScoreVector = retryOnThrottle(retryThrottle) {
        RerankAwaiter.await(
            adapter    = adapter,
            capability = "text.rerank",
            query      = query,
            candidates = candidates,
        )
    }

    override suspend fun translate(
        text: String,
        options: TranslationOptions,
        retryThrottle: Int,
    ): TextCompletion = retryOnThrottle(retryThrottle) {
        TokenStreamCollector.awaitFull(
            adapter    = adapter,
            capability = "text.translate",
            prompt     = text,
            options    = options.toTokenStream(),
        )
    }

    override fun translateStream(
        text: String,
        options: TranslationOptions,
    ): Flow<TokenChunk> = callbackFlow {
        val handle = adapter.submitTokenStream(
            capability = "text.translate",
            prompt     = text,
            options    = options.toTokenStream(),
            onStart    = { },
            onToken    = { t, idx -> trySend(TokenChunk(t, idx)) },
            onComplete = { _ -> close() },
            onError    = { code, msg -> close(ErrorMapping.toException(code, msg, "text.translate")) },
        )
        awaitClose { adapter.cancel(handle) }
    }

    private fun CompletionOptions.toTokenStream() = TokenStreamOptions(
        maxTokens     = maxTokens,
        temperature   = temperature,
        topP          = topP,
        topK          = topK,
        stopSequences = stopSequences,
    )

    private fun TranslationOptions.toTokenStream() = TokenStreamOptions(
        maxTokens     = maxTokens,
        // Translation-leaning sampler defaults — worker may override if the
        // capability has specific `<capability_tuning>` values configured.
        temperature   = 0.2f,
    )
}
