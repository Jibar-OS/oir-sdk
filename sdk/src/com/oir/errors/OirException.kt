/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.errors

import com.oir.models.OirPermission

/**
 * Root of the OIR SDK exception hierarchy.
 *
 * Every failure the runtime surfaces is mapped to a concrete subclass so that
 * Kotlin callers can catch by type rather than switch on integer codes. A
 * mapping table from worker-side [OIRError] integer codes is maintained in
 * [com.oir.internal.ErrorMapping] — if the runtime gains a new error code it
 * is added there; apps that don't handle the new subclass fall through to
 * [OirException] and can still catch by the root type.
 *
 * Subclasses are all `sealed` so an exhaustive `when` over caught exceptions
 * compiles warning-free in Kotlin 1.9+.
 */
public sealed class OirException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * The capability is declared by the platform but is not currently runnable
 * on this device — typically because the default model is not baked in, or
 * because a required sidecar file (tokeniser, phoneme map, OCR rec vocab)
 * is missing. Apps that encounter this should either feature-detect via
 * [com.oir.OpenIntelligence.isCapabilityRunnable] before submitting or surface an OEM
 * install prompt to the user.
 *
 * Corresponds to worker error code `CAPABILITY_UNAVAILABLE_NO_MODEL = 9`.
 */
public class OirCapabilityUnavailableException internal constructor(
    public val capability: String,
    public val reason: String,
) : OirException("capability unavailable: $capability — $reason")

/**
 * Caller lacks the Android permission required for the capability. The
 * [permission] field reports which specific one is missing so apps can
 * request it via the platform permission APIs.
 *
 * Corresponds to worker error code `PERMISSION_DENIED = 7`.
 */
public class OirPermissionDeniedException internal constructor(
    public val permission: OirPermission,
    detail: String,
) : OirException("permission denied: ${permission.androidName} — $detail")

/**
 * The OEM-supplied model's input / output shape does not match the
 * capability's expected contract. Detected at model-load time so apps get
 * a clean failure rather than a SIGSEGV mid-inference. [expected] and
 * [actual] describe the tensor shapes as reported by the ONNX session;
 * useful for OEM debugging.
 *
 * Corresponds to worker error code `MODEL_INCOMPATIBLE = 11`.
 */
public class OirModelIncompatibleException internal constructor(
    public val capability: String,
    public val expected: String,
    public val actual: String,
) : OirException("model incompatible for $capability — expected $expected, got $actual")

/**
 * Model load or inference failed for reasons other than shape (corrupt
 * GGUF / ONNX, backend crash mid-inference, insufficient memory during
 * KV cache allocation). Check [cause] for the underlying framework
 * exception if present.
 *
 * Corresponds to worker error codes `MODEL_ERROR = 1` and
 * `INSUFFICIENT_MEMORY = 4`.
 */
public class OirModelErrorException internal constructor(
    message: String,
    cause: Throwable? = null,
) : OirException(message, cause)

/**
 * Caller exceeded its per-UID rate limit. [retryAfterMs] is a conservative
 * hint — the platform bucket refills at
 * `rate_limit_per_minute / 60000` tokens per millisecond up to
 * `rate_limit_burst`. OEMs tune the defaults via oir_config.xml.
 *
 * Corresponds to worker error code `CAPABILITY_THROTTLED = 10`.
 */
public class OirThrottledException internal constructor(
    public val capability: String,
    public val retryAfterMs: Long,
) : OirException("throttled: $capability — retry after ${retryAfterMs}ms")

/**
 * The request was cancelled by the caller or by the platform (worker
 * died, app backgrounded past grace period). This extends
 * [OirException] rather than [kotlinx.coroutines.CancellationException]
 * so the catch-by-type pattern works uniformly; internal coroutine
 * cancellation still propagates through cooperative cancellation.
 *
 * Corresponds to worker error code `CANCELLED = 2`.
 */
public class OirCancelledException internal constructor(
    detail: String = "request cancelled",
) : OirException(detail)

/**
 * The request exceeded the configured inference timeout
 * (`inference_timeout_seconds` in oir_config.xml, default 120s). The
 * capability's in-flight lease was released; subsequent submits are
 * accepted normally.
 *
 * Corresponds to worker error code `TIMEOUT = 5`.
 */
public class OirTimeoutException internal constructor(
    public val capability: String,
    public val timeoutMs: Long,
) : OirException("timeout: $capability after ${timeoutMs}ms")

/**
 * The SDK rejected the call before it reached the binder because a
 * parameter was null, empty, out of range, or otherwise malformed. These
 * are *programmer errors* — an app that catches this and retries without
 * fixing the input will see the same failure.
 *
 * Corresponds to worker error code `INVALID_INPUT = 3`.
 */
public class OirInvalidInputException internal constructor(
    public val detail: String,
) : OirException("invalid input: $detail")

/**
 * The worker process (`oird`) is unavailable — typically because it
 * crashed and init has not yet respawned it, or because the OIR
 * platform service is not installed on this device. The SDK's internal
 * retry loop (runs for up to 2 seconds by default) exhausted without
 * re-establishing the binder connection.
 *
 * Corresponds to worker error code `WORKER_UNAVAILABLE = 6`.
 */
public class OirWorkerUnavailableException internal constructor(
    detail: String = "oir worker not attached",
) : OirException(detail)

/**
 * Catch-all for SDK-internal consistency failures that should never
 * happen but do: callback invoked after completion, unexpected Parcel
 * shape, binder transaction that returned a code not in the mapping
 * table. Apps should treat this as a bug report prompt rather than a
 * retryable error.
 */
public class OirInternalException internal constructor(
    message: String,
    cause: Throwable? = null,
) : OirException(message, cause)
