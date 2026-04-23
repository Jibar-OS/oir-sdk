/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal.aosp

import android.oir.IOIRService
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import com.oir.internal.OirBinderAdapter
import com.oir.internal.TokenStreamOptions
import com.oir.models.AudioChunk
import com.oir.models.VadState

/**
 * The one place where the SDK's abstract SPI meets the real
 * `android.oir.IOIRService` binder. Each method:
 *
 *   1. Translates Kotlin-domain inputs (Options → Bundle,
 *      List<String> → String[]) into the AIDL shape.
 *   2. Builds a transient Stub-subclass callback that routes AIDL
 *      invocations back into the Kotlin lambdas the SDK passed in.
 *   3. Fires the AIDL call and returns the platform-issued handle.
 *
 * No coroutine machinery lives here — the SDK's
 * [com.oir.internal.CallbackBridges] (TokenStreamCollector et al.)
 * wraps these lambdas into suspend / Flow bindings. This class is
 * the pure "value moves through 4 layers" translator.
 *
 * Thread-safety: the underlying AIDL proxy is thread-safe; multiple
 * concurrent calls from different coroutine scopes are fine. Callbacks
 * fire on binder threads — the SDK's coroutine bridges use
 * `suspendCancellableCoroutine` / `callbackFlow` to hop back to the
 * caller's dispatcher.
 */
public class AidlOirBinderAdapter(
    private val service: IOIRService,
) : OirBinderAdapter {

    public override fun isCapabilityRunnable(capability: String): Int =
        try {
            service.isCapabilityRunnable(capability)
        } catch (e: RemoteException) {
            Log.w(TAG, "isCapabilityRunnable('$capability') binder failed", e)
            0   // UNKNOWN
        }

    public override fun submitTokenStream(
        capability: String,
        prompt: String,
        options: TokenStreamOptions,
        onStart: () -> Unit,
        onToken: (String, Int) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val bundle = Bundle().apply {
            options.maxTokens?.let    { putInt("maxTokens", it) }
            options.temperature?.let  { putFloat("temperature", it) }
            options.topP?.let         { putFloat("topP", it) }
            options.topK?.let         { putInt("topK", it) }
            options.stopSequences?.let { putStringArray("stopSequences", it.toTypedArray()) }
        }
        val cb = TokenCallbackBridge(onStart, onToken, onComplete, onError)
        return try {
            service.submit(capability, prompt, bundle, cb, null)
        } catch (e: RemoteException) {
            onError(6, "submit binder failed: ${e.message ?: "unknown"}")   // 6 = WORKER_UNAVAILABLE
            0L
        }
    }

    public override fun submitVector(
        capability: String,
        text: String,
        onVector: (FloatArray) -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val cb = VectorCallbackBridge(onVector, onError)
        return try {
            service.submitEmbedText(capability, text, cb, null)
        } catch (e: RemoteException) {
            onError(6, "submitEmbedText binder failed: ${e.message ?: "unknown"}")
            0L
        }
    }

    public override fun submitRerank(
        capability: String,
        query: String,
        candidates: List<String>,
        onVector: (FloatArray) -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val cb = VectorCallbackBridge(onVector, onError)
        return try {
            service.submitRerank(capability, query, candidates.toTypedArray(), cb, null)
        } catch (e: RemoteException) {
            onError(6, "submitRerank binder failed: ${e.message ?: "unknown"}")
            0L
        }
    }

    public override fun submitAudioStream(
        capability: String,
        text: String,
        onChunk: (AudioChunk) -> Unit,
        onComplete: (Long) -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val cb = AudioStreamCallbackBridge(onChunk, onComplete, onError)
        return try {
            service.submitSynthesize(capability, text, cb, null)
        } catch (e: RemoteException) {
            onError(6, "submitSynthesize binder failed: ${e.message ?: "unknown"}")
            0L
        }
    }

    public override fun submitBoundingBoxes(
        capability: String,
        imagePath: String,
        onBoxes: (IntArray, IntArray, IntArray, IntArray, IntArray, Array<String>, FloatArray) -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val cb = BboxCallbackBridge(onBoxes, onError)
        return try {
            service.submitDetect(capability, imagePath, cb, null)
        } catch (e: RemoteException) {
            onError(6, "submitDetect binder failed: ${e.message ?: "unknown"}")
            0L
        }
    }

    public override fun submitVadStates(
        capability: String,
        pcmPath: String,
        onState: (VadState) -> Unit,
        onComplete: () -> Unit,
        onError: (Int, String) -> Unit,
    ): Long {
        val cb = RealtimeBooleanCallbackBridge(onState, onComplete, onError)
        return try {
            service.submitVad(capability, pcmPath, cb, null)
        } catch (e: RemoteException) {
            onError(6, "submitVad binder failed: ${e.message ?: "unknown"}")
            0L
        }
    }

    public override fun cancel(requestHandle: Long) {
        try {
            service.cancel(requestHandle)
        } catch (e: RemoteException) {
            Log.w(TAG, "cancel(handle=$requestHandle) binder failed", e)
        }
    }

    private companion object {
        const val TAG = "OirSdkAosp"
    }
}
