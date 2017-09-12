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

import static org.graalvm.compiler.core.common.util.CompilationAlarm.Options.CompilationExpirationPeriod;

import org.graalvm.compiler.core.common.RetryableBailoutException;
import org.graalvm.compiler.core.common.util.CompilationAlarm;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.Phase;
import org.junit.Test;

public class CooperativePhaseTest extends GraalCompilerTest {

    public static void snippet() {
        // dummy snippet
    }

    private static class CooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            CompilationAlarm compilationAlarm = CompilationAlarm.current();
            while (true) {
                sleep(200);
                if (compilationAlarm.hasExpired()) {
                    return;
                }
            }
        }

    }

    private static class UnCooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            CompilationAlarm compilationAlarm = CompilationAlarm.current();
            while (true) {
                sleep(200);
                if (compilationAlarm.hasExpired()) {
                    throw new RetryableBailoutException("Expiring...");
                }
            }
        }

    }

    private static class PartiallyCooperativePhase extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            CompilationAlarm compilationAlarm = CompilationAlarm.current();
            for (int i = 0; i < 10; i++) {
                sleep(200);
                if (compilationAlarm.hasExpired()) {
                    throw new RuntimeException("Phase must not exit in the timeout path");
                }
            }
        }
    }

    private static class CooperativePhaseWithoutAlarm extends Phase {

        @Override
        protected void run(StructuredGraph graph) {
            CompilationAlarm compilationAlarm = CompilationAlarm.current();
            if (compilationAlarm.hasExpired()) {
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
        initializeForTimeout();
        OptionValues initialOptions = getInitialOptions();
        OptionValues options = new OptionValues(initialOptions, CompilationExpirationPeriod, 1/* sec */);
        try (CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod(options)) {
            StructuredGraph g = parseEager("snippet", AllowAssumptions.NO, options);
            new CooperativePhase().apply(g);
        }
    }

    @Test(expected = RetryableBailoutException.class, timeout = 60_000)
    @SuppressWarnings("try")
    public void test02() {
        initializeForTimeout();
        OptionValues initialOptions = getInitialOptions();
        OptionValues options = new OptionValues(initialOptions, CompilationExpirationPeriod, 1/* sec */);
        try (CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod(options)) {
            StructuredGraph g = parseEager("snippet", AllowAssumptions.NO, options);
            new UnCooperativePhase().apply(g);
        }
    }

    @Test(timeout = 60_000)
    @SuppressWarnings("try")
    public void test03() {
        initializeForTimeout();
        // 0 disables alarm utility
        OptionValues initialOptions = getInitialOptions();
        OptionValues options = new OptionValues(initialOptions, CompilationExpirationPeriod, 0);
        try (CompilationAlarm c1 = CompilationAlarm.trackCompilationPeriod(options)) {
            StructuredGraph g = parseEager("snippet", AllowAssumptions.NO, options);
            new PartiallyCooperativePhase().apply(g);
        }
    }

    @Test(timeout = 60_000)
    @SuppressWarnings("try")
    public void test04() {
        initializeForTimeout();
        StructuredGraph g = parseEager("snippet", AllowAssumptions.NO);
        new CooperativePhaseWithoutAlarm().apply(g);
    }
}
