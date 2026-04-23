/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.java

import com.oir.Oir
import com.oir.errors.OirException
import com.oir.models.CompletionOptions
import com.oir.models.ScoreVector
import com.oir.models.TextCompletion
import com.oir.models.TokenChunk
import com.oir.models.TranslationOptions
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Java-facing entry point for [Oir.text]. Mirrors every method on
 * [com.oir.TextCapabilities] but returns [CompletableFuture] instead
 * of `suspend`, and takes callback triples instead of `Flow`.
 *
 * Usage from Java:
 *
 * ```
 * CompletableFuture<TextCompletion> f =
 *     OirJavaText.complete("Summarize this: ...");
 * f.thenAccept(c -> System.out.println(c.getText()));
 *
 * AutoCloseable stream = OirJavaText.completeStream(
 *     "count to 5",
 *     chunk      -> append(chunk.getText()),
 *     ()         -> done(),
 *     throwable  -> showError(throwable)
 * );
 * // Later: stream.close() to cancel
 * ```
 *
 * **Thread model:** the returned [CompletableFuture] completes on
 * [Dispatchers.Default] — which is a background thread pool. Java
 * callers that need to hop to UI should chain via
 * `.thenAcceptAsync(ui::render, handler)` with their own handler.
 *
 * **Cancellation:** closing the [AutoCloseable] for a stream method
 * cancels the underlying coroutine which in turn cancels the worker
 * request. For the [CompletableFuture] methods, call
 * `cf.cancel(true)` — the SDK observes this and cancels the request.
 *
 * **Why an `object` and not top-level `fun`s**: two of the `OirJava*`
 * facades would otherwise clash at the Kotlin package level —
 * `fun embed(String)` exists in both [OirJavaText] and [OirJavaVision],
 * and `@file:JvmName` only renames the Java-side class, not the Kotlin
 * package symbol. Wrapping each facade in a named `object` with
 * `@JvmStatic` members gives Java callers the same call shape
 * (`OirJavaText.complete(...)`) without the collision.
 */
public object OirJavaText {

    private val javaScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Submit a text.complete request; Java sees a [CompletableFuture]. */
    @JvmStatic
    public fun complete(prompt: String): CompletableFuture<TextCompletion> =
        complete(prompt, CompletionOptions())

    @JvmStatic
    public fun complete(
        prompt: String,
        options: CompletionOptions,
    ): CompletableFuture<TextCompletion> {
        val cf = CompletableFuture<TextCompletion>()
        val job: Job = javaScope.launch {
            try {
                cf.complete(Oir.text.complete(prompt, options))
            } catch (oe: OirException) {
                cf.completeExceptionally(oe)
            } catch (t: Throwable) {
                cf.completeExceptionally(t)
            }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    /**
     * Streaming variant. Returns an [AutoCloseable] — closing it cancels
     * the inflight request. [onChunk] is invoked on [Dispatchers.Default];
     * Java callers that need UI dispatch should marshal inside the lambda.
     */
    @JvmStatic
    public fun completeStream(
        prompt: String,
        onChunk:    java.util.function.Consumer<TokenChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable = completeStream(prompt, CompletionOptions(), onChunk, onComplete, onError)

    @JvmStatic
    public fun completeStream(
        prompt: String,
        options: CompletionOptions,
        onChunk:    java.util.function.Consumer<TokenChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job: Job = javaScope.launch {
            Oir.text.completeStream(prompt, options)
                .onEach { onChunk.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { /* consumed in onEach */ }
        }
        return AutoCloseable { job.cancel() }
    }

    @JvmStatic
    public fun embed(text: String): CompletableFuture<FloatArray> {
        val cf = CompletableFuture<FloatArray>()
        val job = javaScope.launch {
            try { cf.complete(Oir.text.embed(text)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun classify(text: String): CompletableFuture<ScoreVector> {
        val cf = CompletableFuture<ScoreVector>()
        val job = javaScope.launch {
            try { cf.complete(Oir.text.classify(text)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun rerank(
        query: String,
        candidates: List<String>,
    ): CompletableFuture<ScoreVector> {
        val cf = CompletableFuture<ScoreVector>()
        val job = javaScope.launch {
            try { cf.complete(Oir.text.rerank(query, candidates)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun translate(
        text: String,
        options: TranslationOptions,
    ): CompletableFuture<TextCompletion> {
        val cf = CompletableFuture<TextCompletion>()
        val job = javaScope.launch {
            try { cf.complete(Oir.text.translate(text, options)) }
            catch (t: Throwable) { cf.completeExceptionally(t) }
        }
        cf.whenComplete { _, _ -> if (cf.isCancelled) job.cancel() }
        return cf
    }

    @JvmStatic
    public fun translateStream(
        text: String,
        options: TranslationOptions,
        onChunk:    java.util.function.Consumer<TokenChunk>,
        onComplete: Runnable,
        onError:    java.util.function.Consumer<Throwable>,
    ): AutoCloseable {
        val job = javaScope.launch {
            Oir.text.translateStream(text, options)
                .onEach { onChunk.accept(it) }
                .catch { onError.accept(it) }
                .onCompletion { cause -> if (cause == null) onComplete.run() }
                .collect { }
        }
        return AutoCloseable { job.cancel() }
    }

    /**
     * Shuts down the shared scope used by this facade. Only meaningful in
     * process-level teardown (e.g. instrumentation tests); production apps
     * never call this — the supervisor lives as long as the process.
     */
    @JvmStatic
    public fun shutdown() { javaScope.cancel() }
}
