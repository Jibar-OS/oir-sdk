# oir-sdk — Kotlin SDK for apps + AOSP companion

Client-side SDK that apps depend on to call OIR capabilities.

## Layout

```
sdk/               — pure Kotlin SDK. Can target any JDK/Android project.
                     Published (eventually) as an AAR / Maven artifact.
                     No AOSP dependency; talks to OIR through the companion.

aosp-companion/    — platform-signed Android library that supplies the actual
                     binder adapter. Static-linked into privileged / platform
                     apps on a JibarOS device. Exists because the binder
                     interface uses @hide AOSP classes, which `sdk_version`
                     apps can't reference.
```

## Quick example

```kotlin
import com.oir.Oir
import com.oir.models.CompletionOptions

suspend fun quickComplete(): String {
    val out = StringBuilder()
    Oir.text.completeStream(
        "Summarize in one sentence: …",
        CompletionOptions(maxTokens = 128, temperature = 0.7f),
    ).collect { chunk ->
        out.append(chunk.text)
    }
    return out.toString()
}
```

The SDK surfaces five top-level namespaces mirroring capability shapes:

| Namespace | Example APIs |
|---|---|
| `Oir.text` | `complete`, `completeStream`, `translate`, `embed`, `classify`, `rerank` |
| `Oir.audio` | `transcribeStream`, `synthesizeStream`, `vadStream` |
| `Oir.vision` | `describe`, `describeStream`, `detect`, `embed`, `ocr` |

## Permissions

Apps declaring `<uses-permission android:name="oir.permission.USE_*" />` must be installed as platform-signed or otherwise trusted by the JibarOS install. See [`oir-framework-addons`](https://github.com/jibar-os/oir-framework-addons) for how the permissions are declared at the platform tier.

## Building

### SDK alone (pure Kotlin)

```bash
cd sdk
./gradlew build
```

### AOSP companion (needs an AOSP tree)

As part of a JibarOS tree — `m -j8 oir_sdk_aosp`.

## See also

- [`oir-demo`](https://github.com/jibar-os/oir-demo) — a reference app using this SDK
- [`github.com/jibar-os/docs`](https://github.com/jibar-os/docs) — capability surface + permission model

## Migration status

🚧 Code migration in progress.
