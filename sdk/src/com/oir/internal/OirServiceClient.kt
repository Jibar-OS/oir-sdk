/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import android.content.Context
import com.oir.errors.OirWorkerUnavailableException
import com.oir.models.CapabilityStatus

/**
 * Thin wrapper around the raw `android.oir.IOIRService` binder. Owns
 * the service lookup so each namespace facade doesn't reimplement it.
 *
 * **Note on AIDL stubs**: the `android.oir.IOIRService` Java class is
 * produced by the AOSP build from our `.aidl` file and only exists on
 * the device class-path. Building this SDK off-tree against the
 * Android SDK jar does NOT produce those stubs — so the actual binder
 * calls below are expressed through [OirBinderAdapter], an
 * SDK-internal interface that the AOSP build provides a concrete
 * implementation for (via a companion module built in-tree). Apps
 * consuming the SDK in AOSP get the real adapter; off-tree unit tests
 * never exercise the adapter path because they inject
 * [com.oir.testing.OirFake] ahead of it.
 *
 * **Why no direct `ServiceManager.getService` path**: earlier revisions
 * tried `ServiceManager.getService("oir")` before delegating to the
 * companion adapter, to save one hop for privileged callers. That
 * reached for `@hide` platform API which (a) breaks the SDK's
 * compile-against-public-SDK promise that lets apps consume it
 * off-tree as an `.aar`, and (b) was redundant — the companion's
 * `OirBinderAdapterProviderImpl.bindAndWrap` calls ServiceManager
 * itself. One source of truth for the service lookup, contained to
 * the AOSP companion where platform access is legitimate.
 */
internal class OirServiceClient(
    private val context: Context,
    private val adapter: OirBinderAdapter = lookupAdapter(context),
) {
    fun isCapabilityRunnable(capability: String): CapabilityStatus =
        try {
            CapabilityStatus.fromCode(adapter.isCapabilityRunnable(capability))
        } catch (t: Throwable) {
            CapabilityStatus.UNKNOWN
        }

    internal fun adapter(): OirBinderAdapter = adapter

    companion object {
        /**
         * Delegates to [OirBinderAdapterFactory.fromContext], which walks
         * the `ServiceLoader` SPI to find the AOSP-companion adapter on
         * the classpath. On AOSP the companion's `bindAndWrap` in turn
         * calls `ServiceManager.getService("oir")` — so system components,
         * privileged apps, and ordinary apps all converge on the same
         * code path. When the companion module isn't on the classpath
         * (e.g. off-tree Gradle unit tests before `OirFake` is injected)
         * the SPI yields `null` and we surface
         * [OirWorkerUnavailableException] cleanly.
         */
        fun lookupAdapter(context: Context): OirBinderAdapter =
            OirBinderAdapterFactory.fromContext(context)
                ?: throw OirWorkerUnavailableException(
                    "OIR platform service not available on this device — check " +
                    "that the runtime is baked into the system image and that " +
                    "oir_sdk-aosp companion module is on the classpath.",
                )
    }
}
