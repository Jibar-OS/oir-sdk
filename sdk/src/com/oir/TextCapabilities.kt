/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir

import com.oir.models.CompletionOptions
import com.oir.models.ScoreVector
import com.oir.models.TextCompletion
import com.oir.models.TokenChunk
import com.oir.models.TranslationOptions
import kotlinx.coroutines.flow.Flow

/**
 * Text-namespace capabilities exposed through [OpenIntelligence.text]. Every method
 * returns either a `suspend` result or a cold [Flow] — cold so that each
 * collector opens a fresh oird session rather than fanning out one
 * inference to many collectors. Cancellation propagates via coroutine
 * scope cancellation; the SDK forwards to the worker's
 * `ICancellationSignal`.
 *
 * Implementations of this interface are either the real binder-backed
 * one ([com.oir.internal.TextCapabilitiesImpl]) or the test fake
 * (`com.oir.testing.OirFake.text`).
 */
public interface TextCapabilities {

    /**
     * Submit [prompt] to `text.complete`. Returns the fully-assembled
     * [TextCompletion] once the model has finished generating.
     *
     * Throws [com.oir.errors.OirCapabilityUnavailableException] if the
     * capability is declared but unbacked, [com.oir.errors.OirThrottledException]
     * if the caller's rate bucket is exhausted, and so on — see the
     * sealed hierarchy in [com.oir.errors.OirException].
     */
    public suspend fun complete(
        prompt: String,
        options: CompletionOptions = CompletionOptions(),
    ): TextCompletion

    /**
     * Streaming variant of [complete] — tokens arrive as they are
     * generated. Collect on the calling scope; cancelling the scope
     * cancels the inference.
     */
    public fun completeStream(
        prompt: String,
        options: CompletionOptions = CompletionOptions(),
    ): Flow<TokenChunk>

    /**
     * Submit [text] to `text.embed`. Returns the pooled embedding
     * vector as a [FloatArray] (dim = model-dependent).
     */
    public suspend fun embed(text: String): FloatArray

    /**
     * Submit [text] to `text.classify`. Returns per-label softmax
     * scores. Label names are not carried by this method — the
     * capability's tokenizer sidecar defines them; apps either bundle
     * their own label list or read the OEM's.
     *
     * Throws [com.oir.errors.OirCapabilityUnavailableException] if
     * no classifier model is baked on this device (ship-empty policy
     * for v0.6).
     */
    public suspend fun classify(text: String): ScoreVector

    /**
     * Submit [query] + [candidates] to `text.rerank`. Returns one
     * relevance score per candidate, aligned to the input order.
     * Apps sort descending.
     */
    public suspend fun rerank(
        query: String,
        candidates: List<String>,
    ): ScoreVector

    /**
     * Submit [text] to `text.translate` with [options] containing
     * source and target language tags. Returns the translation as a
     * fully-assembled [TextCompletion].
     *
     * v0.6 implementation rewrites the prompt into an instruction for
     * the text.complete LLM (no dedicated seq2seq model) — OEMs who
     * bake an NLLB/Marian model get dedicated behaviour automatically.
     */
    public suspend fun translate(
        text: String,
        options: TranslationOptions,
    ): TextCompletion

    /** Streaming variant of [translate]. */
    public fun translateStream(
        text: String,
        options: TranslationOptions,
    ): Flow<TokenChunk>
}
