/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.errors.OirCancelledException
import com.oir.errors.OirCapabilityUnavailableException
import com.oir.errors.OirException
import com.oir.errors.OirInternalException
import com.oir.errors.OirInvalidInputException
import com.oir.errors.OirModelErrorException
import com.oir.errors.OirModelIncompatibleException
import com.oir.errors.OirPermissionDeniedException
import com.oir.errors.OirThrottledException
import com.oir.errors.OirTimeoutException
import com.oir.errors.OirWorkerUnavailableException
import com.oir.models.OirPermission

/**
 * Maps worker-side integer error codes (from `OIRError.java` on the
 * platform) onto the typed [OirException] hierarchy. Single source of
 * truth — if a new code is added to the runtime, this is the only
 * SDK place that needs to learn about it.
 *
 *   | code | constant                            | Kotlin type                          |
 *   |------|-------------------------------------|--------------------------------------|
 *   | 1    | MODEL_ERROR                         | OirModelErrorException               |
 *   | 2    | CANCELLED                           | OirCancelledException                |
 *   | 3    | INVALID_INPUT                       | OirInvalidInputException             |
 *   | 4    | INSUFFICIENT_MEMORY                 | OirModelErrorException (folded)      |
 *   | 5    | TIMEOUT                             | OirTimeoutException                  |
 *   | 6    | WORKER_UNAVAILABLE                  | OirWorkerUnavailableException        |
 *   | 7    | PERMISSION_DENIED                   | OirPermissionDeniedException         |
 *   | 8    | CAPABILITY_UNAVAILABLE_NO_MEMORY    | OirModelErrorException (budget)      |
 *   | 9    | CAPABILITY_UNAVAILABLE_NO_MODEL     | OirCapabilityUnavailableException    |
 *   | 10   | CAPABILITY_THROTTLED                | OirThrottledException                |
 *   | 11   | MODEL_INCOMPATIBLE                  | OirModelIncompatibleException        |
 */
internal object ErrorMapping {

    fun toException(
        code: Int,
        message: String,
        capability: String = "",
        timeoutMs: Long = 0L,
    ): OirException = when (code) {
        1    -> OirModelErrorException("model error: $message")
        2    -> OirCancelledException(message.ifBlank { "request cancelled" })
        3    -> OirInvalidInputException(message)
        4    -> OirModelErrorException("insufficient memory: $message")
        5    -> OirTimeoutException(
            capability = capability.ifBlank { "unknown" },
            timeoutMs  = timeoutMs,
        )
        6    -> OirWorkerUnavailableException(message.ifBlank { "worker unavailable" })
        7    -> {
            // Try to extract which permission was denied from the message;
            // fall back to USE_TEXT (the most lenient) if we can't parse.
            val perm = OirPermission.values()
                .firstOrNull { it.androidName in message }
                ?: OirPermission.USE_TEXT
            OirPermissionDeniedException(perm, message)
        }
        8    -> OirModelErrorException("capability unavailable (no memory): $message")
        9    -> OirCapabilityUnavailableException(
            capability = capability.ifBlank { "unknown" },
            reason     = message,
        )
        10   -> OirThrottledException(
            capability   = capability.ifBlank { "unknown" },
            retryAfterMs = parseRetryAfterMs(message),
        )
        11   -> parseModelIncompatible(
            capability = capability.ifBlank { "unknown" },
            message    = message,
        )
        else -> OirInternalException("unknown OIR error code $code: $message")
    }

    /**
     * Parse a retry-after hint out of the throttle message. The worker
     * typically includes "... retry after XXms" but older builds may
     * not — return a conservative default of 1000ms when absent.
     */
    private fun parseRetryAfterMs(message: String): Long {
        val match = Regex("""(\d+)\s*ms""").find(message)
        return match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 1000L
    }

    /**
     * The runtime produces MODEL_INCOMPATIBLE messages in the form
     * "expected <foo> got <bar>" — we split these into the two
     * fields on [OirModelIncompatibleException] for easier OEM
     * debugging.
     */
    private fun parseModelIncompatible(
        capability: String,
        message: String,
    ): OirModelIncompatibleException {
        val (expected, actual) = message
            .substringAfter("expected", "")
            .split("got", limit = 2)
            .map { it.trim().trimEnd(',', '.') }
            .let { parts ->
                when {
                    parts.size == 2 -> parts[0] to parts[1]
                    else            -> "?" to message
                }
            }
        return OirModelIncompatibleException(
            capability = capability,
            expected   = expected,
            actual     = actual,
        )
    }
}
