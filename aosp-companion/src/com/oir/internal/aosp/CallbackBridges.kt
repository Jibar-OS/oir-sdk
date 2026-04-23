/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal.aosp

import android.oir.BoundingBox
import android.oir.IOIRAudioStreamCallback
import android.oir.IOIRBoundingBoxCallback
import android.oir.IOIRRealtimeBooleanCallback
import android.oir.IOIRTokenCallback
import android.oir.IOIRVectorCallback
import android.os.Bundle
import com.oir.models.AudioChunk
import com.oir.models.VadState

/*
 * Five AIDL callback-Stub subclasses, each one a thin shim that
 * forwards an AIDL binder invocation into a Kotlin lambda triple (or
 * quad) that the SDK passed down through [OirBinderAdapter].
 *
 * Why one file: every bridge is under ~25 lines, shares the same
 * shape (Stub override → lambda.invoke), and they'd be single-use
 * top-level classes anyway. Keeping them co-located makes it easier
 * to diff any AIDL surface change (a new `onMetric` callback on one
 * shape would edit one file instead of hunting across five).
 *
 * Lifetime: callers construct one bridge per request; the AIDL layer
 * keeps a reference for as long as the request is in-flight; once
 * onComplete/onError fires, the AIDL proxy drops the ref and GC
 * reclaims. No manual cleanup needed.
 */

// ---------------------------------------------------------------------
// TokenStream — submit / submitTranslate / (via audio.transcribe)
// ---------------------------------------------------------------------

internal class TokenCallbackBridge(
    private val onStart:    () -> Unit,
    private val onToken:    (String, Int) -> Unit,
    private val onComplete: (Long) -> Unit,
    private val onError:    (Int, String) -> Unit,
) : IOIRTokenCallback.Stub() {

    override fun onStart(meta: Bundle) { onStart.invoke() }

    override fun onToken(token: String, outputIndex: Int) {
        onToken.invoke(token, outputIndex)
    }

    override fun onComplete(stats: Bundle) {
        android.util.Log.w("OirSdkAosp", "TokenCallbackBridge.onComplete fired, totalMs=" + stats.getLong("totalMs", 0))
        // OIRService packs totalMs + totalTokens etc into the Bundle;
        // the SDK only cares about wall time today, but we could
        // surface more via OirBinderAdapter if the public surface
        // wanted it.
        onComplete.invoke(stats.getLong("totalMs", 0))
    }

    override fun onError(errorCode: Int, message: String) {
        onError.invoke(errorCode, message)
    }
}

// ---------------------------------------------------------------------
// Vector — submitEmbed / submitClassify / submitRerank (per candidate)
// ---------------------------------------------------------------------

internal class VectorCallbackBridge(
    private val onVector: (FloatArray) -> Unit,
    private val onError:  (Int, String) -> Unit,
) : IOIRVectorCallback.Stub() {

    override fun onVector(vector: FloatArray) { onVector.invoke(vector) }

    override fun onError(errorCode: Int, message: String) {
        onError.invoke(errorCode, message)
    }
}

// ---------------------------------------------------------------------
// AudioStream — submitSynthesize. PCM chunks arrive 1..N times, then
// exactly one of onComplete / onError terminates.
// ---------------------------------------------------------------------

internal class AudioStreamCallbackBridge(
    private val onChunk:    (AudioChunk) -> Unit,
    private val onComplete: (Long) -> Unit,
    private val onError:    (Int, String) -> Unit,
) : IOIRAudioStreamCallback.Stub() {

    override fun onChunk(
        pcm: ByteArray,
        sampleRateHz: Int,
        channels: Int,
        encoding: Int,
        isLast: Boolean,
    ) {
        onChunk.invoke(AudioChunk(
            pcm          = pcm,
            sampleRateHz = sampleRateHz,
            channels     = channels,
            encoding     = encoding,
            isLast       = isLast,
        ))
    }

    override fun onComplete(totalMs: Long) {
        onComplete.invoke(totalMs)
    }

    override fun onError(errorCode: Int, message: String) {
        onError.invoke(errorCode, message)
    }
}

// ---------------------------------------------------------------------
// BoundingBoxes — submitDetect, submitOcr. One-shot.
//
// The AIDL shape is List<BoundingBox>, but the SDK's adapter uses the
// parallel-arrays form (xs, ys, widths, heights, labelsPerBox,
// labelsFlat, scoresFlat) — that's the shape the worker-side AIDL
// already has, kept identical to avoid leaking `android.oir.BoundingBox`
// into oir_sdk's public types. Convert here.
// ---------------------------------------------------------------------

internal class BboxCallbackBridge(
    private val onBoxes: (
        xs: IntArray,
        ys: IntArray,
        widths: IntArray,
        heights: IntArray,
        labelsPerBox: IntArray,
        labelsFlat: Array<String>,
        scoresFlat: FloatArray,
    ) -> Unit,
    private val onError: (Int, String) -> Unit,
) : IOIRBoundingBoxCallback.Stub() {

    override fun onBoundingBoxes(boxes: MutableList<BoundingBox>) {
        val n = boxes.size
        val xs      = IntArray(n)
        val ys      = IntArray(n)
        val widths  = IntArray(n)
        val heights = IntArray(n)
        val labelsPerBox = IntArray(n)
        val labelsFlatList = ArrayList<String>(n)
        val scoresFlatList = ArrayList<Float>(n)
        for (i in 0 until n) {
            val b = boxes[i]
            xs[i]      = b.x
            ys[i]      = b.y
            widths[i]  = b.width
            heights[i] = b.height
            // BoundingBox carries parallel labels[] + scores[] arrays,
            // ordered descending by score. Per-box count is the labels
            // array length; flatten both into the shared arrays.
            val labels = b.labels ?: emptyArray()
            val scores = b.scores ?: FloatArray(0)
            val lbls = if (labels.isEmpty() && scores.isEmpty()) 1 else labels.size
            labelsPerBox[i] = if (labels.isEmpty()) 1 else labels.size
            if (labels.isEmpty()) {
                labelsFlatList.add("")
                scoresFlatList.add(0f)
            } else {
                for (j in labels.indices) {
                    labelsFlatList.add(labels[j])
                    scoresFlatList.add(scores.getOrElse(j) { 0f })
                }
            }
        }
        onBoxes.invoke(
            xs, ys, widths, heights, labelsPerBox,
            labelsFlatList.toTypedArray(),
            FloatArray(scoresFlatList.size) { scoresFlatList[it] },
        )
    }

    override fun onError(errorCode: Int, message: String) {
        onError.invoke(errorCode, message)
    }
}

// ---------------------------------------------------------------------
// RealtimeBoolean — submitVad. Streaming on/off transitions until
// onComplete or onError.
// ---------------------------------------------------------------------

internal class RealtimeBooleanCallbackBridge(
    private val onState:    (VadState) -> Unit,
    private val onComplete: () -> Unit,
    private val onError:    (Int, String) -> Unit,
) : IOIRRealtimeBooleanCallback.Stub() {

    override fun onState(isTrue: Boolean, timestampMs: Long) {
        onState.invoke(VadState(isVoicePresent = isTrue, timestampMs = timestampMs))
    }

    override fun onComplete() { onComplete.invoke() }

    override fun onError(errorCode: Int, message: String) {
        onError.invoke(errorCode, message)
    }
}
