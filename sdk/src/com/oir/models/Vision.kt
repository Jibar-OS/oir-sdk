/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.models

/**
 * A single detected region with label and confidence.
 *
 * For vision.detect: [label] is the class name (COCO-80 by default or
 * per the OEM `<model>.classes.json` sidecar); [score] is the detection
 * confidence.
 *
 * For vision.ocr: [label] is the recognized UTF-8 text for the region;
 * [score] is the average CTC-decode confidence across timesteps.
 */
public data class DetectedObject(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val label: String,
    val score: Float,
)

/** Terminal result of a non-streaming vision.describe call. */
public data class ImageDescription(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
)

/** Result of vision.embed — a single pooled embedding vector. */
public data class EmbeddingVector(
    val vector: FloatArray,
    val dim: Int = vector.size,
) {
    override fun equals(other: Any?): Boolean = this === other ||
            (other is EmbeddingVector && vector.contentEquals(other.vector))
    override fun hashCode(): Int = vector.contentHashCode()
}
