/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import android.content.Context
import android.os.IBinder
import com.oir.errors.OirWorkerUnavailableException
import com.oir.models.AudioChunk
import com.oir.models.VadState

/**
 * Stable SDK-facing shape over the raw AIDL. The AOSP-companion module
 * (`oir_sdk-aosp`, built in-tree against the AIDL-generated stubs)
 * provides the real implementation of this interface. Off-tree, the
 * companion is absent; all paths fail closed with
 * [OirWorkerUnavailableException], and unit tests bypass the adapter
 * entirely by injecting [com.oir.testing.OirFake] through
 * [com.oir.testing.OirTestRule].
 *
 * Why an adapter and not direct AIDL calls? Three reasons:
 *   1. Off-tree compilation (this library targets non-AOSP consumers
 *      via `.aar`) can't see the AIDL-generated stubs.
 *   2. The adapter keeps the callback-bridge code small and testable
 *      in isolation.
 *   3. Future transports (e.g. AIDL-via-HAL, socket transport for
 *      non-Android hosts) slot in by providing an alternative adapter.
 *
 * Every method translates one worker RPC → one coroutine-friendly
 * result (either suspend or Flow-producing lambda callbacks
 * parameters). The [OirServiceClient] dispatches these on its own
 * thread pool so the app's main thread is never blocked.
 *
 * **Visibility:** public so the AOSP-companion module in a separate
 * Gradle/Soong module can implement it. Apps should not touch it
 * directly — use the [com.oir.Oir] facade instead. The
 * `com.oir.internal` package is excluded from the documented API
 * surface and subject to change without notice.
 */
public interface OirBinderAdapter {

    // v0.6 AIDL: isCapabilityRunnable pre-flight.
    fun isCapabilityRunnable(capability: String): Int

    // ---- text.complete / text.translate / vision.describe ----
    // All TokenStream-shape capabilities share this callback shape. The
    // adapter forwards the worker's onStart / onToken / onComplete /
    // onError into the four lambdas. outputIndex is 0 for plain
    // generation, non-zero for AgentKit tool-call interleaving.
    fun submitTokenStream(
        capability: String,
        prompt: String,
        options: TokenStreamOptions,
        onStart:    () -> Unit,
        onToken:    (text: String, outputIndex: Int) -> Unit,
        onComplete: (totalMs: Long) -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    // ---- text.embed / text.classify / vision.embed ----
    fun submitVector(
        capability: String,
        text: String,
        onVector:   (FloatArray) -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    // ---- text.rerank ----
    fun submitRerank(
        capability: String,
        query: String,
        candidates: List<String>,
        onVector:   (FloatArray) -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    // ---- audio.synthesize ----
    fun submitAudioStream(
        capability: String,
        text: String,
        onChunk:    (AudioChunk) -> Unit,
        onComplete: (totalMs: Long) -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    // ---- vision.detect / vision.ocr ----
    fun submitBoundingBoxes(
        capability: String,
        imagePath: String,
        onBoxes:    (xs: IntArray, ys: IntArray, widths: IntArray, heights: IntArray,
                     labelsPerBox: IntArray, labelsFlat: Array<String>,
                     scoresFlat: FloatArray) -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    // ---- audio.vad ----
    fun submitVadStates(
        capability: String,
        pcmPath: String,
        onState:    (VadState) -> Unit,
        onComplete: () -> Unit,
        onError:    (code: Int, message: String) -> Unit,
    ): Long

    /** Cancel an in-flight request. No-op if the request already completed. */
    fun cancel(requestHandle: Long)
}

/**
 * Sampler / generation options for all TokenStream capabilities.
 * Only fields that differ from the platform defaults need to be set;
 * nulls pass through as "use the capability's tuning knob".
 */
public data class TokenStreamOptions(
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val stopSequences: List<String>? = null,
)

/**
 * Service-provider factory for the AOSP-companion adapter. The
 * companion module ships with a `META-INF/services/com.oir.internal.OirBinderAdapterFactory`
 * entry so the SDK can pick it up via [java.util.ServiceLoader].
 *
 * Off-tree (this unit-test build) the factory has no service provider
 * registered and every call returns null — which surfaces as
 * [OirWorkerUnavailableException] at the call site.
 */
internal object OirBinderAdapterFactory {

    private val spi: OirBinderAdapterProvider? by lazy {
        try {
            val loader = java.util.ServiceLoader.load(
                OirBinderAdapterProvider::class.java,
                OirBinderAdapterProvider::class.java.classLoader,
            )
            loader.firstOrNull()
        } catch (_: Throwable) {
            null
        }
    }

    fun from(binder: IBinder): OirBinderAdapter? = spi?.wrap(binder)

    fun fromContext(context: Context): OirBinderAdapter? = spi?.bindAndWrap(context)
}

/**
 * Implemented by the in-tree AOSP companion module to bridge raw AIDL
 * binder onto [OirBinderAdapter]. Registered via [java.util.ServiceLoader]
 * — the companion ships a `META-INF/services/com.oir.internal.OirBinderAdapterProvider`
 * file containing the fully-qualified class name of its implementation.
 *
 * Public because the companion lives in a separate module (`oir_sdk_aosp`)
 * and `ServiceLoader` needs the interface reachable from both sides.
 */
public interface OirBinderAdapterProvider {
    public fun wrap(binder: IBinder): OirBinderAdapter
    public fun bindAndWrap(context: Context): OirBinderAdapter?
}
