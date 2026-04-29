/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir

import com.oir.models.DetectedObject
import com.oir.models.EmbeddingVector
import com.oir.models.ImageDescription
import com.oir.models.TokenChunk
import kotlinx.coroutines.flow.Flow

/**
 * Vision-namespace capabilities exposed through [OpenIntelligence.vision].
 *
 * Requires `oir.permission.USE_VISION` (dangerous). Image parameters
 * are file paths (not [android.net.Uri] / [android.graphics.Bitmap])
 * because the worker process does not share the calling app's memory
 * — the worker decodes from disk in its own address space. Apps that
 * start from a Uri / Bitmap write to a temp file first.
 */
public interface VisionCapabilities {

    /**
     * Produce a natural-language description of the image at
     * [imagePath] via the VLM bound to `vision.describe` (SmolVLM /
     * LLaVA / Moondream, OEM-selected).
     *
     * [prompt] is optional — empty defaults to "describe this image"
     * at the worker. Useful to override for VQA-style calls
     * ("how many people are in this image?").
     */
    public suspend fun describe(
        imagePath: String,
        prompt: String = "",
        retryThrottle: Int = 0,
    ): ImageDescription

    /** Streaming variant of [describe] — tokens arrive as generated. */
    public fun describeStream(
        imagePath: String,
        prompt: String = "",
    ): Flow<TokenChunk>

    /**
     * Embed the image at [imagePath] to a pooled feature vector via
     * `vision.embed` (SigLIP default). Dim is model-dependent.
     */
    public suspend fun embed(imagePath: String, retryThrottle: Int = 0): EmbeddingVector

    /**
     * Run object detection over [imagePath]. Returns all kept boxes
     * after IoU-NMS with thresholds from `vision.detect.*` tuning
     * knobs (OEM-overridable).
     */
    public suspend fun detect(imagePath: String, retryThrottle: Int = 0): List<DetectedObject>

    /**
     * Run OCR over [imagePath]. Returns one [DetectedObject] per
     * text region where `label` is the recognized UTF-8 text.
     * v0.6 requires a det+rec+vocab triplet baked by the OEM; absent
     * it, [com.oir.errors.OirCapabilityUnavailableException] is
     * thrown.
     */
    public suspend fun ocr(imagePath: String, retryThrottle: Int = 0): List<DetectedObject>
}
