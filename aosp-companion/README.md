# oir_sdk_aosp — AOSP companion for the oir_sdk Kotlin library

This module is the glue that lets the public `oir_sdk` library
(shipped as `.aar` for off-tree app consumption) actually reach the
OIR platform binder inside an AOSP build.

## Why it's a separate module

`oir_sdk` is written to compile against the Android SDK jar alone —
no AIDL-generated stubs, no hidden-API references. That keeps the
library usable in any Android Studio project without pulling AOSP
into the consumer's classpath.

But someone has to turn an `IBinder` into a typed `IOIRService`
proxy. That someone is `AidlOirBinderAdapter`, which lives here and
imports `android.oir.IOIRService.Stub` directly. On AOSP, this
module compiles against `framework-minus-apex` (which contains
the AIDL-generated stubs) and registers itself with oir_sdk's
`ServiceLoader` via `META-INF/services`.

## How it wires up

```
┌─────────────────────────────────────────────────────────┐
│ App code                                                │
│   Oir.text.complete("hi")                               │
└──────────────────────────┬──────────────────────────────┘
                           │ SDK façade (Oir → TextCapabilitiesImpl)
                           ▼
┌─────────────────────────────────────────────────────────┐
│ oir_sdk (library)                                       │
│   OirServiceClient.lookupAdapter(context)               │
│     → OirBinderAdapterFactory.fromContext(context)      │
│     → ServiceLoader.load(OirBinderAdapterProvider)      │
│     → [this module] OirBinderAdapterProviderImpl        │
└──────────────────────────┬──────────────────────────────┘
                           │ OirBinderAdapter SPI
                           ▼
┌─────────────────────────────────────────────────────────┐
│ oir_sdk_aosp (this module)                              │
│   AidlOirBinderAdapter(service: IOIRService)            │
│     ├─ isCapabilityRunnable → service.isCapabilityRunnable
│     ├─ submitTokenStream    → service.submit (+ TokenCallbackBridge)
│     ├─ submitVector         → service.submitEmbedText   (+ VectorCallbackBridge)
│     ├─ submitRerank         → service.submitRerank      (+ VectorCallbackBridge)
│     ├─ submitAudioStream    → service.submitSynthesize  (+ AudioStreamCallbackBridge)
│     ├─ submitBoundingBoxes  → service.submitDetect      (+ BboxCallbackBridge)
│     ├─ submitVadStates      → service.submitVad         (+ RealtimeBooleanCallbackBridge)
│     └─ cancel               → service.cancel
└──────────────────────────┬──────────────────────────────┘
                           │ AIDL binder
                           ▼
┌─────────────────────────────────────────────────────────┐
│ OIRService (system_server, v0.6)                        │
└─────────────────────────────────────────────────────────┘
```

The ServiceLoader hop is the critical decoupling: off-tree builds of
`oir_sdk` don't have this module on the classpath, so
`ServiceLoader.firstOrNull()` returns null; every call into the
facade surfaces `OirWorkerUnavailableException` cleanly. On AOSP,
this module is on the classpath and things Just Work.

## Files

| File | Purpose |
|---|---|
| `Android.bp` | java_library declaring dep on `oir_sdk` + `framework-minus-apex` |
| `res/META-INF/services/com.oir.internal.OirBinderAdapterProvider` | ServiceLoader registration — one line, FQCN of the provider |
| `src/com/oir/internal/aosp/OirBinderAdapterProviderImpl.kt` | SPI entry point; does `ServiceManager.getService("oir")` + `IOIRService.Stub.asInterface` |
| `src/com/oir/internal/aosp/AidlOirBinderAdapter.kt` | 9 SDK adapter methods → AIDL calls, each wrapped in a try/RemoteException |
| `src/com/oir/internal/aosp/CallbackBridges.kt` | 5 `IOIRxxxCallback.Stub()` subclasses translating AIDL → Kotlin lambdas |

## Callback shape translation

The SDK's `OirBinderAdapter` uses callback lambdas so the core
library doesn't need to know anything about `android.oir.*` AIDL
types. All the type shimming happens in `CallbackBridges.kt`:

- `IOIRTokenCallback.Stub.onComplete(Bundle)` → `onComplete(Long)` by
  pulling `bundle.getLong("totalMs", 0)`.
- `IOIRAudioStreamCallback.Stub.onChunk(pcm, sampleRateHz, channels,
  encoding, isLast)` → builds a `com.oir.models.AudioChunk`.
- `IOIRBoundingBoxCallback.Stub.onBoundingBoxes(List<BoundingBox>)`
  → flattens to parallel arrays (`xs, ys, widths, heights,
  labelsPerBox, labelsFlat, scoresFlat`) so the SDK doesn't import
  the AIDL parcelable.
- `IOIRRealtimeBooleanCallback.Stub.onState(isTrue, timestampMs)` →
  `com.oir.models.VadState`.

## Build notes

`java_library`, `system_current`, `min_sdk_version 31`. Not an
apex module — ships as part of the base system jars, linked into
any app that depends on `oir_sdk` via Soong's static_libs chain.

`kotlincflags: ["-Xjvm-default=all"]` for Kotlin interface default
method support (`OirBinderAdapterProvider`'s default methods need
this to compile cleanly as JVM-default interface methods).

## Validating on cvd

Not yet. Requires:
- `oir_sdk` added to the Soong build graph as a java_library (currently
  lives off-tree as a Gradle module; importing needs a small wrapper
  Android.bp).
- A trivial app APK linking `oir_sdk_aosp` that calls `Oir.text.complete("hi")`
  and observes a token.

Both land in the v0.7 sample-assistant slice.
