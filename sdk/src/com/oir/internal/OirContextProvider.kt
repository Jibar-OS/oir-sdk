/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.internal

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.oir.Oir

/**
 * Zero-data [ContentProvider] that fires during app start-up. The only
 * purpose of this class is to call [Oir.installContext] before any
 * other app code touches [Oir]. Android instantiates providers before
 * `Application.onCreate()` runs, which guarantees the SDK is ready by
 * the time the app's first activity/service boots.
 *
 * Apps that prefer explicit initialisation can set
 * `tools:node="remove"` on this provider in their merged manifest and
 * call `Oir.installContext(context)` themselves in
 * `Application.onCreate()`.
 *
 * Why a [ContentProvider] and not
 * [androidx.startup.AppInitializer]? Because the hard constraint is
 * **no AndroidX** — we replicate the small piece we need instead of
 * taking the AndroidX dependency. The entire replica is this file
 * (~40 LOC of actual logic).
 */
public class OirContextProvider : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context
            ?: return false   // extremely unusual; nothing we can do
        Oir.installContext(ctx.applicationContext ?: ctx)
        return true
    }

    // All ContentProvider methods are inert — we never publish data.
    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(
        uri: Uri, selection: String?, selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0
}
