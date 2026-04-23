/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal.aosp

import android.content.Context
import android.os.IBinder
import android.os.ServiceManager
import android.oir.IOIRService
import android.util.Log
import com.oir.internal.OirBinderAdapter
import com.oir.internal.OirBinderAdapterProvider

/**
 * Entry point discovered by the SDK's ServiceLoader. Responsible for
 * two things:
 *
 *   1. [wrap] — given an already-acquired `IBinder`, wrap it with
 *      [AidlOirBinderAdapter] which translates every SDK adapter
 *      call into the corresponding [IOIRService] AIDL call.
 *
 *   2. [bindAndWrap] — look the binder up itself, typically for
 *      in-process use (system_server apps, privileged callers). On
 *      ordinary app processes, `ServiceManager.getService("oir")`
 *      returns non-null because the OIR service is published by
 *      system_server at boot; apps without the relevant manifest
 *      permission will still get the IBinder but subsequent calls
 *      surface [com.oir.errors.OirPermissionDeniedException].
 *
 * Registered via
 * `res/META-INF/services/com.oir.internal.OirBinderAdapterProvider`
 * — the SDK's [com.oir.internal.OirBinderAdapterFactory] does a
 * `ServiceLoader.load()` on that file to pick us up without an
 * explicit companion-module dependency declaration.
 */
public class OirBinderAdapterProviderImpl : OirBinderAdapterProvider {

    public override fun wrap(binder: IBinder): OirBinderAdapter {
        val service = IOIRService.Stub.asInterface(binder)
            ?: throw IllegalStateException(
                "binder returned by ServiceManager.getService(\"oir\") does not " +
                "implement IOIRService — platform mis-installed?",
            )
        return AidlOirBinderAdapter(service)
    }

    public override fun bindAndWrap(context: Context): OirBinderAdapter? {
        val binder: IBinder? = try {
            @Suppress("DiscouragedPrivateApi")
            ServiceManager.getService(SERVICE_NAME)
        } catch (t: Throwable) {
            Log.w(TAG, "ServiceManager.getService('$SERVICE_NAME') threw", t)
            null
        }
        if (binder == null) {
            Log.w(TAG, "OIR service '$SERVICE_NAME' not registered on this device — " +
                    "platform runtime may not be installed. App calls into Oir.* will " +
                    "throw OirWorkerUnavailableException.")
            return null
        }
        return wrap(binder)
    }

    private companion object {
        const val TAG          = "OirSdkAosp"
        const val SERVICE_NAME = "oir"
    }
}
