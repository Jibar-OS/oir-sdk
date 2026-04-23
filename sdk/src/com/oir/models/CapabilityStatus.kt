/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.models

/**
 * Result of [com.oir.Oir.isCapabilityRunnable]. Apps use this to decide
 * whether to surface a capability to the user without paying the cost of a
 * failed submit. Maps 1:1 onto the return codes from the platform AIDL
 * `isCapabilityRunnable(String)` method.
 */
public enum class CapabilityStatus {
    /** Capability name not present in the platform registry. */
    UNKNOWN,

    /**
     * Capability is declared, a default model is set, and the model file
     * is readable by the platform. Submits will be served (modulo
     * transient throttling / memory pressure).
     */
    RUNNABLE,

    /**
     * Capability is declared but no default model is baked. OEMs have
     * chosen to ship the runtime support without a bundled model — apps
     * either prompt the user to install one or fall back to a different
     * capability.
     */
    NO_DEFAULT_MODEL,

    /**
     * A default model path is declared but the file is not present (or
     * not readable by the platform). Typically means a partial OEM image
     * or a deleted /product/etc/oir/ file. Reboot + reinstall usually
     * fixes it.
     */
    MODEL_MISSING;

    public companion object {
        /** Map the AIDL int code (0/1/2/3) onto the enum. Unknown → UNKNOWN. */
        internal fun fromCode(code: Int): CapabilityStatus = when (code) {
            1    -> RUNNABLE
            2    -> NO_DEFAULT_MODEL
            3    -> MODEL_MISSING
            else -> UNKNOWN
        }
    }
}
