/*
 * Copyright (C) 2026 The OpenIntelligenceRuntime Project
 * Licensed under the Apache License, Version 2.0
 */
package com.oir.testing

import com.oir.Oir
import com.oir.internal.OirImpl
import com.oir.internal.RunnableProbe
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * JUnit 4 rule that swaps [OpenIntelligence]'s internal implementation for an
 * [OirFake] while the test runs and restores it afterwards. Use
 * inside test classes:
 *
 * ```
 * class SummariserTest {
 *     @get:Rule val oir = OirTestRule()
 *
 *     @Test fun summarises_short_text() = runTest {
 *         oir.fake.text.whenComplete { p, _ -> TextCompletion("s", 0, 0) }
 *         assertEquals("s", Summariser().run("hello"))
 *         assertEquals(1, oir.fake.text.completeCalls.size)
 *     }
 * }
 * ```
 *
 * Tests that need to customise the fake across multiple test methods
 * should reach into [fake] directly — the rule exposes it for the
 * lifetime of the JVM run.
 *
 * The rule is safe to use on JUnit 4; for JUnit 5 compose an
 * equivalent extension around [fake] + [OpenIntelligence.swapImplementationForTest].
 */
public class OirTestRule : TestRule {

    public val fake: OirFake = OirFake()

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            install()
            try {
                base.evaluate()
            } finally {
                uninstall()
            }
        }
    }

    private fun install() {
        fake.reset()
        OpenIntelligence.swapImplementationForTest(
            OirImpl(
                text          = fake.text,
                audio         = fake.audio,
                vision        = fake.vision,
                runnableProbe = RunnableProbe { cap -> fake.capabilityStatus(cap) },
            ),
        )
    }

    private fun uninstall() {
        OpenIntelligence.resetForTest()
    }
}
