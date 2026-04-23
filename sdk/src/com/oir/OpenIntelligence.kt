/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir

import android.content.Context
import com.oir.errors.OirInternalException
import com.oir.errors.OirWorkerUnavailableException
import com.oir.internal.OirImpl
import com.oir.models.CapabilityStatus

/**
 * Top-level entry point for the OIR SDK.
 *
 * Usage:
 *
 * ```
 * // Nothing to wire up — the SDK auto-initialises via a zero-data
 * // ContentProvider during app start-up. Just call through the
 * // namespace facades from any coroutine context:
 *
 * val completion = OpenIntelligence.text.complete("Summarise this: ...")
 *
 * // Streaming:
 * OpenIntelligence.text.completeStream("...").collect { chunk ->
 *     renderToken(chunk.text)
 * }
 *
 * // Feature-detect before submitting:
 * if (OpenIntelligence.isCapabilityRunnable("vision.describe") == CapabilityStatus.RUNNABLE) {
 *     OpenIntelligence.vision.describe("/sdcard/DCIM/photo.jpg")
 * }
 * ```
 *
 * **Dependencies:** only `kotlin-stdlib` + `kotlinx.coroutines`. No
 * AndroidX. Apps opting into AndroidX still work — the SDK does not
 * propagate anything onto the classpath you do not already import.
 *
 * **Thread-safety:** `Oir` is a thread-safe singleton. Call the
 * suspend / Flow methods from any coroutine scope; the implementation
 * serialises through a dedicated binder dispatcher internally.
 *
 * **Concurrency scope:** as of v0.6.2 the runtime serves concurrent
 * submits to the same loaded model across **every** backend:
 *
 *   - llama + VLM (`text.complete`, `text.embed`, `text.translate`,
 *     `vision.describe`) — pooled via `ContextPool`.
 *   - whisper (`audio.transcribe`) — pooled via `WhisperPool`.
 *   - ONNX Runtime (`audio.vad`, `audio.synthesize`, `vision.detect`,
 *     `vision.embed`, `vision.ocr`, `text.classify`, `text.rerank`) —
 *     `Ort::Session::Run()` is thread-safe; submits run concurrently
 *     without any pool abstraction needed.
 *
 * What's not yet shipped is a **cross-backend scheduler** — priority
 * today applies only within a single backend's pool. So `audio.*`
 * still doesn't preempt `text.*` across the worker as a whole; that
 * lands in v0.7. The SDK intentionally does not expose a
 * per-submit priority parameter until the scheduler is real — same
 * rationale as before.
 */
public object OpenIntelligence {

    @Volatile
    private var impl: OirImpl? = null

    /**
     * Install the [Context] used for service binding. Called
     * automatically by [com.oir.internal.OirContextProvider] during app
     * start-up; apps that remove the provider from their manifest must
     * call this explicitly from `Application.onCreate()`.
     *
     * Safe to call multiple times — later calls replace the context,
     * which is useful for test harnesses that swap a stub Context.
     */
    public fun installContext(context: Context) {
        // Always store the application context; holding an Activity
        // reference would leak.
        val app = context.applicationContext ?: context
        impl = OirImpl(app)
    }

    /**
     * Text namespace — text.complete, text.embed, text.classify,
     * text.rerank, text.translate. See [TextCapabilities].
     *
     * Requires `oir.permission.USE_TEXT` (normal-level).
     */
    public val text: TextCapabilities
        get() = requireImpl().text

    /**
     * Audio namespace — audio.transcribe, audio.synthesize, audio.vad.
     * See [AudioCapabilities].
     *
     * Requires `oir.permission.USE_AUDIO` (dangerous).
     */
    public val audio: AudioCapabilities
        get() = requireImpl().audio

    /**
     * Vision namespace — vision.embed, vision.describe, vision.detect,
     * vision.ocr. See [VisionCapabilities].
     *
     * Requires `oir.permission.USE_VISION` (dangerous).
     */
    public val vision: VisionCapabilities
        get() = requireImpl().vision

    /**
     * Pre-flight a capability. Returns a [CapabilityStatus] that lets
     * the app decide whether to surface the capability to the user
     * without paying the cost of a failed submit. No permission
     * required — capability names are already public via the on-
     * device `capabilities.xml`.
     *
     * When called before the SDK is initialised (e.g. pre-
     * [installContext] or in a fake context), returns
     * [CapabilityStatus.UNKNOWN] rather than throwing.
     */
    public fun isCapabilityRunnable(capability: String): CapabilityStatus =
        impl?.isCapabilityRunnable(capability) ?: CapabilityStatus.UNKNOWN

    // v0.7: exposed package-private for OirTestRule to install a fake in
    // place of the real binder-backed implementation. Not part of the
    // public API surface — apps that want to inject a fake use
    // OirTestRule; hostile use is caught by R8 shrinking removing the
    // non-public accessor in release builds.
    @JvmSynthetic
    internal fun swapImplementationForTest(fakeImpl: OirImpl) {
        impl = fakeImpl
    }

    @JvmSynthetic
    internal fun resetForTest() {
        impl = null
    }

    private fun requireImpl(): OirImpl = impl
        ?: throw OirWorkerUnavailableException(
            "OpenIntelligence.installContext(context) was not called and OirContextProvider is missing " +
            "from the merged manifest. Either add the provider back (default) or call " +
            "OpenIntelligence.installContext(applicationContext) from Application.onCreate().",
        )
}
