/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.core.test;

import org.junit.Test;

import org.graalvm.compiler.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValue;
import org.graalvm.compiler.options.OptionValue.OverrideScope;
import org.graalvm.compiler.phases.Phase;

public class CooperativePhaseTest extends GraalCompilerTest {

    public static void snippet() {
        // dummy snippet
    }

    private static class CooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            while (true) {
                sleep(200);
                if (CompilationAlarm.hasExpired()) {
                    return;
                }
            }
        }

    }

    private static class UnCooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            while (true) {
                sleep(200);
                if (CompilationAlarm.hasExpired()) {
                    throw new RetryableBailoutException("Expiring...");
                }
            }
        }

    }

    private static class ParlyCooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            for (int i = 0; i < 10; i++) {
                sleep(200);
                if (CompilationAlarm.hasExpired()) {
                    throw new RuntimeException("Phase must not exit in the timeout path");
                }
            }
        }
    }

    private static class CooperativePhaseWithoutAlarm extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            if (CompilationAlarm.hasExpired()) {
                throw new RuntimeException("Phase must not exit in the timeout path");
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            GraalError.shouldNotReachHere(e.getCause());
        }
    }

    @Test(timeout = 60_000)
    @SuppressWarnings("try")
    public void test01() {
        StructuredGraph g = parseEager("snippet", AllowAssumptions.NO);
        try (OverrideScope o = OptionValue.override(CompilationAlarm.Options.CompilationExpirationPeriod, 1/* sec */);
                        CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod()) {
            new CooperativePhase().apply(g);
        }
    }

    @Test(expected = RetryableBailoutException.class, timeout = 60_000)
    @SuppressWarnings("try")
    public void test02() {
        StructuredGraph g = parseEager("snippet", AllowAssumptions.NO);
        try (OverrideScope o = OptionValue.override(CompilationAlarm.Options.CompilationExpirationPeriod, 1/* sec */);
                        CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod()) {
            new UnCooperativePhase().apply(g);
        }
    }

    @Test(timeout = 60_000)
    @SuppressWarnings("try")
    public void test03() {
        StructuredGraph g = parseEager("snippet", AllowAssumptions.NO);
        // 0 disables alarm utility
        try (OverrideScope o = OptionValue.override(CompilationAlarm.Options.CompilationExpirationPeriod, 0);
                        CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod()) {
            new ParlyCooperativePhase().apply(g);
        }
    }

    @Test(timeout = 60_000)
    @SuppressWarnings("try")
    public void test04() {
        StructuredGraph g = parseEager("snippet", AllowAssumptions.NO);
        new CooperativePhaseWithoutAlarm().apply(g);
    }
}
