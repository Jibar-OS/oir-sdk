/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import com.oir.errors.OirThrottledException
import kotlinx.coroutines.delay

/**
 * Run [block] and retry on [OirThrottledException] up to [retries] times,
 * delaying [OirThrottledException.retryAfterMs] between attempts. Used to
 * implement the `retryThrottle: Int = 0` parameter on public capability
 * methods — when retries=0 (the default) this is a single direct call and
 * any throttle exception propagates to the caller, preserving the legacy
 * "one attempt, app handles retry" behavior.
 *
 * Why retries on top of attempts: matches Android's idiom where opt-in
 * retry counts are "additional retries beyond the first try", so
 * `retryThrottle = 3` means up to 4 total attempts (1 initial + 3 retries).
 *
 * Only [OirThrottledException] triggers a retry. Other exceptions
 * (model errors, cancellation, security failures, etc.) propagate
 * immediately — silently retrying those would mask real problems.
 *
 * The wait between attempts comes from the runtime's `retry after Xms`
 * hint: the OIR rate limiter computes the precise time until the next
 * token is available for this UID and packs it into the throttle message;
 * the SDK's [ErrorMapping.parseRetryAfterMs] extracts it. So this delay
 * is "until the bucket actually has a token", not a guess.
 */
internal suspend fun <T> retryOnThrottle(retries: Int, block: suspend () -> T): T {
    require(retries >= 0) { "retries must be >= 0" }
    if (retries == 0) return block()
    var attempt = 0
    while (true) {
        try {
            return block()
        } catch (e: OirThrottledException) {
            if (attempt >= retries) throw e
            delay(e.retryAfterMs)
            attempt++
        }
    }
}
