/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.models

/**
 * The three OIR runtime permissions an app can request / be denied.
 *
 * Maps onto the `<uses-permission>` entries the OIR platform declares:
 *
 *   | Enum                          | Android name                  | Level     |
 *   |-------------------------------|-------------------------------|-----------|
 *   | USE_TEXT                      | oir.permission.USE_TEXT       | normal    |
 *   | USE_AUDIO                     | oir.permission.USE_AUDIO      | dangerous |
 *   | USE_VISION                    | oir.permission.USE_VISION     | dangerous |
 *
 * Per-capability required permission is declared in `capabilities.xml`
 * on the device; the SDK does not duplicate that mapping. When a submit
 * is refused, the [androidName] is surfaced on
 * [com.oir.errors.OirPermissionDeniedException] so apps can request
 * exactly the right one.
 */
public enum class OirPermission(public val androidName: String) {
    USE_TEXT  ("oir.permission.USE_TEXT"),
    USE_AUDIO ("oir.permission.USE_AUDIO"),
    USE_VISION("oir.permission.USE_VISION");

    public companion object {
        /** Reverse lookup for error mapping. Unknown names yield null. */
        public fun fromAndroidName(name: String): OirPermission? =
            values().firstOrNull { it.androidName == name }
    }
}
