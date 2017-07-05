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
 * @summary Verify that RTMLockingThreshold affects rtm state transition
 *          ProfileRTM => UseRTM.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestRTMLockingThreshold
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestRTMLockingThreshold
 */

import java.util.List;
import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import rtm.*;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;
import sun.misc.Unsafe;

/**
 * Test verifies that RTMLockingThreshold option actually affects how soon
 * method will be deoptimized on low abort ratio.
 */
public class TestRTMLockingThreshold extends CommandLineOptionTest {
    private TestRTMLockingThreshold() {
        super(new AndPredicate(new SupportedVM(), new SupportedCPU()));
    }

    /**
     * We use non-zero abort threshold to avoid abort related to
     * interrupts, VMM calls, etc. during first lock attempt.
     *
     */
    private static final int ABORT_THRESHOLD = 10;

    @Override
    protected void runTestCases() throws Throwable {
        verifyLockingThreshold(0, false);
        verifyLockingThreshold(100, false);
        verifyLockingThreshold(1000, false);

        verifyLockingThreshold(0, true);
        verifyLockingThreshold(100, true);
        verifyLockingThreshold(1000, true);
    }

    private void verifyLockingThreshold(int lockingThreshold,
            boolean useStackLock) throws Throwable {
        CompilableTest test = new Test();

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(
                test,
                "-XX:CompileThreshold=1",
                CommandLineOptionTest.prepareBooleanFlag("UseRTMForStackLocks",
                        useStackLock),
                "-XX:+UseRTMDeopt",
                "-XX:RTMTotalCountIncrRate=1",
                "-XX:RTMRetryCount=0",
                CommandLineOptionTest.prepareNumericFlag("RTMAbortThreshold",
                        TestRTMLockingThreshold.ABORT_THRESHOLD),
                CommandLineOptionTest.prepareNumericFlag("RTMLockingThreshold",
                        lockingThreshold),
                "-XX:RTMAbortRatio=100",
                "-XX:+PrintPreciseRTMLockingStatistics",
                Test.class.getName(),
                Boolean.toString(!useStackLock),
                Integer.toString(lockingThreshold)
        );

        outputAnalyzer.shouldHaveExitValue(0);

        List<RTMLockingStatistics> statistics = RTMLockingStatistics.fromString(
                test.getMethodWithLockName(), outputAnalyzer.getOutput());

        Asserts.assertEQ(statistics.size(), 2, "VM output should contain two "
                + "RTM locking statistics entries.");

        /**
         * We force abort on each odd iteration, so if RTMLockingThreshold==0,
         * then we have to make 1 call without abort to avoid rtm state
         * transition to NoRTM (otherwise actual abort ratio will be 100%),
         * and after that make 1 call with abort to force deoptimization.
         * This leads us to two locks for threshold 0.
         * For other threshold values we have to make RTMLockingThreshold + 1
         * locks if locking threshold is even, or + 0 if odd.
         */
        long expectedValue = lockingThreshold +
                (lockingThreshold == 0L ? 2L : lockingThreshold % 2L);

        RTMLockingStatistics statBeforeDeopt = null;
        for (RTMLockingStatistics s : statistics) {
            if (s.getTotalLocks() == expectedValue) {
                Asserts.assertNull(statBeforeDeopt,
                        "Only one statistics entry should contain aborts");
                statBeforeDeopt = s;
            }
        }

        Asserts.assertNotNull(statBeforeDeopt, "There should be exactly one "
                + "statistics entry corresponding to ProfileRTM state.");
    }

    public static class Test implements CompilableTest {
        // Following field have to be static in order to avoid escape analysis.
        @SuppressWarnings("UnsuedDeclaration")
        private static int field = 0;
        private static final int TOTAL_ITERATIONS = 10000;
        private static final Unsafe UNSAFE = Utils.getUnsafe();
        private final Object monitor = new Object();


        @Override
        public String getMethodWithLockName() {
            return this.getClass().getName() + "::lock";
        }

        @Override
        public String[] getMethodsToCompileNames() {
            return new String[] {
                getMethodWithLockName(),
                sun.misc.Unsafe.class.getName() + "::addressSize"
            };
        }

        public void lock(boolean abort) {
            synchronized(monitor) {
                if (abort) {
                    Test.field += Test.UNSAFE.addressSize();
                }
            }
        }

        /**
         * Usage:
         * Test &lt;inflate monitor&gt;
         */
        public static void main(String args[]) throws Throwable {
            Asserts.assertGTE(args.length, 1, "One argument required.");
            Test t = new Test();

            if (Boolean.valueOf(args[0])) {
                AbortProvoker.inflateMonitor(t.monitor);
            }
            for (int i = 0; i < Test.TOTAL_ITERATIONS; i++) {
                t.lock(i % 2 == 1);
            }
        }
    }

    public static void main(String args[]) throws Throwable {
        new TestRTMLockingThreshold().test();
    }
}
