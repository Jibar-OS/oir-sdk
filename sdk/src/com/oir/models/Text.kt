/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.models

/**
 * A single token chunk streamed from a TokenStream-shape capability
 * (text.complete / text.translate / vision.describe / audio.transcribe).
 *
 * Tokens are already UTF-8 decoded and assembled by the worker — a
 * [text] chunk may be a single character, a word, or several words
 * depending on the backend's tokenizer. [outputIndex] allows tool-
 * aware streaming (see AgentKit) to interleave multiple logical
 * outputs over a single stream; for plain generation it is always 0.
 */
public data class TokenChunk(
    val text: String,
    val outputIndex: Int = 0,
)

/**
 * Terminal result of a non-streaming `complete()` call — the fully
 * assembled response plus timing telemetry. [firstTokenMs] is the
 * latency from submit to the first token streaming back (useful for
 * time-to-first-token metrics); [totalMs] is end-to-end wall time.
 */
public data class TextCompletion(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
    val outputIndex: Int = 0,
)

/**
 * Options for a text.complete / text.translate call. All fields are
 * optional; omitting any uses the capability's default from
 * `oir_config.xml` (platform) or `<capability_tuning>` fragments
 * (OEM override).
 */
public data class CompletionOptions(
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val stopSequences: List<String>? = null,
)

/** Options specific to [com.oir.TextCapabilities.translate]. */
public data class TranslationOptions(
    val sourceLang: String = "auto",
    val targetLang: String,
    val maxTokens: Int? = null,
)

/** Score vector returned from text.classify / text.rerank. */
public data class ScoreVector(
    val scores: FloatArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is ScoreVector && scores.contentEquals(other.scores))
    override fun hashCode(): Int = scores.contentHashCode()
}
