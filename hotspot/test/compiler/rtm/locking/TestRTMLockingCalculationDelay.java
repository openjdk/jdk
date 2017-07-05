/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/**
 * @test
 * @bug 8031320
 * @summary Verify that RTMLockingCalculationDelay affect when
 *          abort ratio calculation is started.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestRTMLockingCalculationDelay
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestRTMLockingCalculationDelay
 */

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import rtm.*;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

/**
 * Test verifies that abort ratio calculation could be delayed using
 * RTMLockingCalculationDelay option.
 */
public class TestRTMLockingCalculationDelay extends CommandLineOptionTest {
    private static final boolean INFLATE_MONITOR = true;

    private TestRTMLockingCalculationDelay() {
        super(new AndPredicate(new SupportedCPU(), new SupportedVM()));
    }

    @Override
    protected void runTestCases() throws Throwable {
        // verify that calculation will be started immediately
        verifyLockingCalculationDelay(0, 0, true);

        // verify that calculation will not be started during
        // first 10 minutes, while test will be started immediately
        verifyLockingCalculationDelay(600000, 0, false);

        // verify that calculation will be started after a second
        verifyLockingCalculationDelay(1000, 1000, true);
    }

    private void verifyLockingCalculationDelay(long delay, long testDelay,
            boolean deoptExpected) throws Throwable {
        AbortProvoker provoker = AbortType.XABORT.provoker();
        String logFileName = String.format("rtm_delay_%d_%d.xml", delay,
                testDelay);

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(
                logFileName,
                provoker,
                "-XX:+UseRTMDeopt",
                CommandLineOptionTest.prepareNumericFlag(
                        "RTMLockingCalculationDelay", delay),
                "-XX:RTMAbortRatio=0",
                "-XX:RTMAbortThreshold=0",
                AbortProvoker.class.getName(),
                AbortType.XABORT.toString(),
                Boolean.toString(
                        TestRTMLockingCalculationDelay.INFLATE_MONITOR),
                Long.toString(AbortProvoker.DEFAULT_ITERATIONS),
                Long.toString(testDelay)
        );

        outputAnalyzer.shouldHaveExitValue(0);

        int deopts = RTMTestBase.firedRTMStateChangeTraps(logFileName);

        if (deoptExpected) {
            Asserts.assertGT(deopts, 0, "At least one deoptimization due to "
                    + "rtm_state_chage is expected");
        } else {
            Asserts.assertEQ(deopts, 0, "No deoptimizations due to "
                    + "rtm_state_chage are expected");
        }
    }

    public static void main(String args[]) throws Throwable {
        new TestRTMLockingCalculationDelay().test();
    }
}
