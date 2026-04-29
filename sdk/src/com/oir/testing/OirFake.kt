/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.testing

import com.oir.AudioCapabilities
import com.oir.TextCapabilities
import com.oir.VadCapability
import com.oir.VisionCapabilities
import com.oir.models.AudioBuffer
import com.oir.models.AudioChunk
import com.oir.models.CapabilityStatus
import com.oir.models.CompletionOptions
import com.oir.models.DetectedObject
import com.oir.models.EmbeddingVector
import com.oir.models.ImageDescription
import com.oir.models.ScoreVector
import com.oir.models.TextCompletion
import com.oir.models.TokenChunk
import com.oir.models.Transcript
import com.oir.models.TranscriptChunk
import com.oir.models.TranslationOptions
import com.oir.models.VadState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-memory fake of the OIR SDK for unit tests. No binder, no AOSP
 * service — every capability method is configurable via a `whenXxx`
 * handler lambda, and every call is recorded in an `xxxCalls` list
 * so tests can verify the interaction shape.
 *
 * Default behaviour: every method returns a deterministic placeholder
 * (`"stub"` for text, a single-sample PCM buffer for audio, etc.) so
 * tests that don't care about exact outputs still run green. Tests
 * that do care override the handler:
 *
 * ```
 * @get:Rule val oirRule = OirTestRule()
 *
 * @Test fun summariser_uses_text_complete() = runTest {
 *     oirRule.fake.text.whenComplete { prompt, _ ->
 *         TextCompletion(text = "summary of $prompt", 0, 0)
 *     }
 *
 *     val s = Summariser()
 *     assertEquals("summary of hi", s.summarise("hi"))
 *     assertEquals(1, oirRule.fake.text.completeCalls.size)
 * }
 * ```
 *
 * Throw from a handler to exercise an error branch:
 *
 * ```
 * oirRule.fake.text.whenComplete { _, _ ->
 *     throw OirPermissionDeniedException(OirPermission.USE_TEXT, "test")
 * }
 * ```
 */
public class OirFake {
    public val text:   FakeTextCapabilities   = FakeTextCapabilities()
    public val audio:  FakeAudioCapabilities  = FakeAudioCapabilities()
    public val vision: FakeVisionCapabilities = FakeVisionCapabilities()

    /** Default status override for feature-detect calls. */
    public var capabilityStatus: (String) -> CapabilityStatus = { CapabilityStatus.RUNNABLE }

    public fun reset() {
        text.reset()
        audio.reset()
        vision.reset()
        capabilityStatus = { CapabilityStatus.RUNNABLE }
    }
}

// ------------------------------------------------------------------
// text.*
// ------------------------------------------------------------------

public class FakeTextCapabilities internal constructor() : TextCapabilities {

    public val completeCalls:       MutableList<Pair<String, CompletionOptions>>   = mutableListOf()
    public val completeStreamCalls: MutableList<Pair<String, CompletionOptions>>   = mutableListOf()
    public val embedCalls:          MutableList<String>                            = mutableListOf()
    public val classifyCalls:       MutableList<String>                            = mutableListOf()
    public val rerankCalls:         MutableList<Pair<String, List<String>>>        = mutableListOf()
    public val translateCalls:      MutableList<Pair<String, TranslationOptions>>  = mutableListOf()

    private var completeHandler:       suspend (String, CompletionOptions) -> TextCompletion   = { _, _ -> TextCompletion("stub", 0, 0) }
    private var completeStreamHandler: (String, CompletionOptions) -> Flow<TokenChunk>         = { p, _ -> flow { emit(TokenChunk(p, 0)) } }
    private var embedHandler:          suspend (String) -> FloatArray                          = { FloatArray(4) }
    private var classifyHandler:       suspend (String) -> ScoreVector                         = { ScoreVector(floatArrayOf(0.5f, 0.5f)) }
    private var rerankHandler:         suspend (String, List<String>) -> ScoreVector           = { _, cs -> ScoreVector(FloatArray(cs.size) { 1f / (it + 1) }) }
    private var translateHandler:      suspend (String, TranslationOptions) -> TextCompletion  = { t, _ -> TextCompletion(t, 0, 0) }
    private var translateStreamHandler:(String, TranslationOptions) -> Flow<TokenChunk>        = { t, _ -> flow { emit(TokenChunk(t, 0)) } }

    public fun whenComplete(handler: suspend (String, CompletionOptions) -> TextCompletion) {
        completeHandler = handler
    }
    public fun whenCompleteStream(handler: (String, CompletionOptions) -> Flow<TokenChunk>) {
        completeStreamHandler = handler
    }
    public fun whenEmbed(handler: suspend (String) -> FloatArray) { embedHandler = handler }
    public fun whenClassify(handler: suspend (String) -> ScoreVector) { classifyHandler = handler }
    public fun whenRerank(handler: suspend (String, List<String>) -> ScoreVector) { rerankHandler = handler }
    public fun whenTranslate(handler: suspend (String, TranslationOptions) -> TextCompletion) {
        translateHandler = handler
    }
    public fun whenTranslateStream(handler: (String, TranslationOptions) -> Flow<TokenChunk>) {
        translateStreamHandler = handler
    }

    // -------- TextCapabilities implementation --------

    override suspend fun complete(prompt: String, options: CompletionOptions, retryThrottle: Int): TextCompletion {
        completeCalls += prompt to options
        return completeHandler(prompt, options)
    }
    override fun completeStream(prompt: String, options: CompletionOptions): Flow<TokenChunk> {
        completeStreamCalls += prompt to options
        return completeStreamHandler(prompt, options)
    }
    override suspend fun embed(text: String, retryThrottle: Int): FloatArray {
        embedCalls += text
        return embedHandler(text)
    }
    override suspend fun classify(text: String, retryThrottle: Int): ScoreVector {
        classifyCalls += text
        return classifyHandler(text)
    }
    override suspend fun rerank(query: String, candidates: List<String>, retryThrottle: Int): ScoreVector {
        rerankCalls += query to candidates
        return rerankHandler(query, candidates)
    }
    override suspend fun translate(text: String, options: TranslationOptions, retryThrottle: Int): TextCompletion {
        translateCalls += text to options
        return translateHandler(text, options)
    }
    override fun translateStream(text: String, options: TranslationOptions): Flow<TokenChunk> {
        translateCalls += text to options
        return translateStreamHandler(text, options)
    }

    internal fun reset() {
        completeCalls.clear()
        completeStreamCalls.clear()
        embedCalls.clear()
        classifyCalls.clear()
        rerankCalls.clear()
        translateCalls.clear()
    }
}

// ------------------------------------------------------------------
// audio.*
// ------------------------------------------------------------------

public class FakeAudioCapabilities internal constructor() : AudioCapabilities {

    public override val vad: FakeVadCapability = FakeVadCapability()

    public val transcribeCalls:       MutableList<String> = mutableListOf()
    public val transcribeStreamCalls: MutableList<String> = mutableListOf()
    public val synthesizeCalls:       MutableList<String> = mutableListOf()
    public val synthesizeStreamCalls: MutableList<String> = mutableListOf()

    private var transcribeHandler:       suspend (String) -> Transcript                 = { Transcript("stub transcript", 0) }
    private var transcribeStreamHandler: (String) -> Flow<TranscriptChunk>              = { p -> flow { emit(TranscriptChunk("stub $p")) } }
    private var synthesizeHandler:       suspend (String) -> AudioBuffer                = { AudioBuffer(ByteArray(0), 22050, 1, 4, 0) }
    private var synthesizeStreamHandler: (String) -> Flow<AudioChunk>                   = { _ ->
        flow { emit(AudioChunk(ByteArray(0), 22050, 1, 4, isLast = true)) }
    }

    public fun whenTranscribe(handler: suspend (String) -> Transcript) { transcribeHandler = handler }
    public fun whenTranscribeStream(handler: (String) -> Flow<TranscriptChunk>) { transcribeStreamHandler = handler }
    public fun whenSynthesize(handler: suspend (String) -> AudioBuffer) { synthesizeHandler = handler }
    public fun whenSynthesizeStream(handler: (String) -> Flow<AudioChunk>) { synthesizeStreamHandler = handler }

    override suspend fun transcribe(pcmPath: String, retryThrottle: Int): Transcript {
        transcribeCalls += pcmPath
        return transcribeHandler(pcmPath)
    }
    override fun transcribeStream(pcmPath: String): Flow<TranscriptChunk> {
        transcribeStreamCalls += pcmPath
        return transcribeStreamHandler(pcmPath)
    }
    override suspend fun synthesize(text: String, retryThrottle: Int): AudioBuffer {
        synthesizeCalls += text
        return synthesizeHandler(text)
    }
    override fun synthesizeStream(text: String): Flow<AudioChunk> {
        synthesizeStreamCalls += text
        return synthesizeStreamHandler(text)
    }

    internal fun reset() {
        transcribeCalls.clear()
        transcribeStreamCalls.clear()
        synthesizeCalls.clear()
        synthesizeStreamCalls.clear()
        vad.reset()
    }
}

public class FakeVadCapability internal constructor() : VadCapability {

    public val statesCalls: MutableList<String> = mutableListOf()

    private var statesHandler: (String) -> Flow<VadState> = { _ -> flow { emit(VadState(false, 0)) } }

    public fun whenStates(handler: (String) -> Flow<VadState>) { statesHandler = handler }

    override fun states(pcmPath: String): Flow<VadState> {
        statesCalls += pcmPath
        return statesHandler(pcmPath)
    }

    internal fun reset() {
        statesCalls.clear()
    }
}

// ------------------------------------------------------------------
// vision.*
// ------------------------------------------------------------------

public class FakeVisionCapabilities internal constructor() : VisionCapabilities {

    public val describeCalls:       MutableList<Pair<String, String>> = mutableListOf()
    public val describeStreamCalls: MutableList<Pair<String, String>> = mutableListOf()
    public val embedCalls:          MutableList<String>               = mutableListOf()
    public val detectCalls:         MutableList<String>               = mutableListOf()
    public val ocrCalls:            MutableList<String>               = mutableListOf()

    private var describeHandler:       suspend (String, String) -> ImageDescription = { p, _ -> ImageDescription("stub description of $p", 0, 0) }
    private var describeStreamHandler: (String, String) -> Flow<TokenChunk>          = { p, _ -> flow { emit(TokenChunk("stub $p", 0)) } }
    private var embedHandler:          suspend (String) -> EmbeddingVector           = { EmbeddingVector(FloatArray(4)) }
    private var detectHandler:         suspend (String) -> List<DetectedObject>      = { emptyList() }
    private var ocrHandler:            suspend (String) -> List<DetectedObject>      = { emptyList() }

    public fun whenDescribe(handler: suspend (String, String) -> ImageDescription) { describeHandler = handler }
    public fun whenDescribeStream(handler: (String, String) -> Flow<TokenChunk>) { describeStreamHandler = handler }
    public fun whenEmbed(handler: suspend (String) -> EmbeddingVector) { embedHandler = handler }
    public fun whenDetect(handler: suspend (String) -> List<DetectedObject>) { detectHandler = handler }
    public fun whenOcr(handler: suspend (String) -> List<DetectedObject>) { ocrHandler = handler }

    override suspend fun describe(imagePath: String, prompt: String, retryThrottle: Int): ImageDescription {
        describeCalls += imagePath to prompt
        return describeHandler(imagePath, prompt)
    }
    override fun describeStream(imagePath: String, prompt: String): Flow<TokenChunk> {
        describeStreamCalls += imagePath to prompt
        return describeStreamHandler(imagePath, prompt)
    }
    override suspend fun embed(imagePath: String, retryThrottle: Int): EmbeddingVector {
        embedCalls += imagePath
        return embedHandler(imagePath)
    }
    override suspend fun detect(imagePath: String, retryThrottle: Int): List<DetectedObject> {
        detectCalls += imagePath
        return detectHandler(imagePath)
    }
    override suspend fun ocr(imagePath: String, retryThrottle: Int): List<DetectedObject> {
        ocrCalls += imagePath
        return ocrHandler(imagePath)
    }

    internal fun reset() {
        describeCalls.clear()
        describeStreamCalls.clear()
        embedCalls.clear()
        detectCalls.clear()
        ocrCalls.clear()
    }
}
