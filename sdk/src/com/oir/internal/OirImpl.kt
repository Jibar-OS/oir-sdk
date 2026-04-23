/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import android.content.Context
import com.oir.AudioCapabilities
import com.oir.TextCapabilities
import com.oir.VisionCapabilities
import com.oir.models.CapabilityStatus

/**
 * Internal composite that owns the binder-client instance and exposes
 * the three namespace facades. Created once per process (by
 * [com.oir.OpenIntelligence.installContext] from [OirContextProvider]) and cached
 * as the `OpenIntelligence.impl` singleton.
 *
 * Two construction paths:
 *   1. `OirImpl(context)` — production: builds a real [OirServiceClient]
 *      and wires each facade through it.
 *   2. `OirImpl(text, audio, vision, probe)` — test: used by
 *      [com.oir.testing.OirTestRule] to inject the fake facades with
 *      no binder machinery.
 */
internal class OirImpl internal constructor(
    internal val text:   TextCapabilities,
    internal val audio:  AudioCapabilities,
    internal val vision: VisionCapabilities,
    private  val runnableProbe: RunnableProbe,
) {
    /** Production path. */
    constructor(context: Context) : this(
        OirServiceClient(context),
    )

    private constructor(client: OirServiceClient) : this(
        text   = TextCapabilitiesImpl(client),
        audio  = AudioCapabilitiesImpl(client),
        vision = VisionCapabilitiesImpl(client),
        runnableProbe = RunnableProbe(client::isCapabilityRunnable),
    )

    internal fun isCapabilityRunnable(capability: String): CapabilityStatus =
        runnableProbe.check(capability)
}

/** Single-method stub so tests don't need to mock the full service client. */
internal fun interface RunnableProbe {
    fun check(capability: String): CapabilityStatus
}
