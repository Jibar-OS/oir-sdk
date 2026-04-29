# Changelog

User-visible and API-visible changes to `oir-sdk` — the Kotlin coroutine API on top of the OIR AIDL surface. Per-commit detail is in `git log`; this file is the "what shipped" view at release granularity.

Format loosely follows [Keep a Changelog](https://keepachangelog.com). Pre-migration history is in `git log` and the [JibarOS roadmap](https://github.com/Jibar-OS/JibarOS/blob/main/docs/ROADMAP.md).

---

## [Unreleased] — v0.7

### Throttle handling

- **Opt-in `retryThrottle: Int = 0` parameter on every suspend capability method.** When > 0, the SDK catches `OirThrottledException`, waits `retryAfterMs` (the runtime now embeds the real value in the throttle message — pre-this-fix the SDK silently fell back to a 1000ms guess), and retries up to N times. Default 0 keeps the legacy "one attempt, app handles retry" behavior. Flow methods unchanged; apps compose retry through standard kotlinx.coroutines retry operators. (`810ae9c`)
- New internal helper `RetryOnThrottle.kt` — `retryOnThrottle(retries, block)` with bounded attempts. Only `OirThrottledException` triggers a retry; other exceptions propagate immediately so silent retry can't mask real bugs. Pairs with the runtime fix in `oir-framework-addons@55ec7fe` that adds `RateLimiter.nextTokenWaitMs(uid)` and embeds the actual retry-after-ms in throttle messages.

  ```kotlin
  // Default — same as before, throttle propagates as exception:
  val r = OpenIntelligence.text.complete(prompt, options)

  // Opt in to retry-on-throttle, up to 3 retries:
  val r = OpenIntelligence.text.complete(prompt, options, retryThrottle = 3)
  ```

### Breaking API changes

- ⚠ **Top-level public class renamed**: `com.oir.Oir` → `com.oir.OpenIntelligence`. Apps using the SDK need to update imports + call sites. The new name better reflects the public branding (`OpenIntelligence.text`, `OpenIntelligence.audio`, `OpenIntelligence.vision` namespace facades). Java interop wrappers (`OirJavaText` / `OirJavaAudio` / `OirJavaVision`) keep their names. (`c51f194`, `e4b616d`, `87300e0`)

### Documentation

- README cleanup — dropped "Migration status" section now that the migration from the monorepo is complete. (`705c7fe`)
- Cross-refs updated to `Jibar-OS/JibarOS` (consolidated main repo). (`a34d204`, `d2a1368`)

### Initial migration

- v0.6.9 — SDK extracted from the original monorepo into its own `oir-sdk` repo. Marks the start of independent SDK release lifecycle. (`be7cb42`)

---

## Pre-migration

The SDK lived inside the OIR monorepo before v0.6.9. That history is in `git log` of the original `OpenIntelligenceRuntime` repo and summarized in the [JibarOS roadmap](https://github.com/Jibar-OS/JibarOS/blob/main/docs/ROADMAP.md) under the v0.4 / v0.5 / v0.6 sections.
