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
 * @summary Verify that RTMTotalCountIncrRate option affects
 *          RTM locking statistics.
 * @library /testlibrary /testlibrary/whitebox /compiler/testlibrary
 * @build TestRTMTotalCountIncrRate
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+WhiteBoxAPI TestRTMTotalCountIncrRate
 */

import java.util.List;

import com.oracle.java.testlibrary.*;
import com.oracle.java.testlibrary.cli.CommandLineOptionTest;
import com.oracle.java.testlibrary.cli.predicate.AndPredicate;
import rtm.*;
import rtm.predicate.SupportedCPU;
import rtm.predicate.SupportedVM;

/**
 * Test verifies that with RTMTotalCountIncrRate=1 RTM locking statistics
 * contains precise information abort attempted locks and that with other values
 * statistics contains information abort non-zero locking attempts.
 * Since assert done for RTMTotalCountIncrRate=1 is pretty strict, test uses
 * -XX:RTMRetryCount=0 to avoid issue with retriable aborts. For more details on
 * that issue see {@link TestUseRTMAfterLockInflation}.
 */
public class TestRTMTotalCountIncrRate extends CommandLineOptionTest {
    private TestRTMTotalCountIncrRate() {
        super(new AndPredicate(new SupportedCPU(), new SupportedVM()));
    }

    @Override
    protected void runTestCases() throws Throwable {
        verifyLocksCount(1, false);
        verifyLocksCount(64, false);
        verifyLocksCount(128, false);
        verifyLocksCount(1, true);
        verifyLocksCount(64, true);
        verifyLocksCount(128, true);
    }

    private void verifyLocksCount(int incrRate, boolean useStackLock)
            throws Throwable{
        CompilableTest test = new Test();

        OutputAnalyzer outputAnalyzer = RTMTestBase.executeRTMTest(
                test,
                CommandLineOptionTest.prepareBooleanFlag("UseRTMForStackLocks",
                        useStackLock),
                CommandLineOptionTest.prepareNumericFlag(
                        "RTMTotalCountIncrRate", incrRate),
                "-XX:RTMRetryCount=0",
                "-XX:+PrintPreciseRTMLockingStatistics",
                Test.class.getName(),
                Boolean.toString(!useStackLock)
        );

        outputAnalyzer.shouldHaveExitValue(0);

        List<RTMLockingStatistics> statistics = RTMLockingStatistics.fromString(
                test.getMethodWithLockName(), outputAnalyzer.getOutput());

        Asserts.assertEQ(statistics.size(), 1, "VM output should contain "
                + "exactly one RTM locking statistics entry for method "
                + test.getMethodWithLockName());

        RTMLockingStatistics lock = statistics.get(0);
        if (incrRate == 1) {
            Asserts.assertEQ(lock.getTotalLocks(), Test.TOTAL_ITERATIONS,
                    "Total locks should be exactly the same as amount of "
                    + "iterations.");
        } else {
            Asserts.assertGT(lock.getTotalLocks(), 0L, "RTM statistics "
                    + "should contain information for at least on lock.");
        }
    }

    public static class Test implements CompilableTest {
        private static final long TOTAL_ITERATIONS = 10000L;
        private final Object monitor = new Object();
        // Following field have to be static in order to avoid escape analysis.
        @SuppressWarnings("UnsuedDeclaration")
        private static int field = 0;

        @Override
        public String getMethodWithLockName() {
            return this.getClass().getName() + "::lock";
        }

        @Override
        public String[] getMethodsToCompileNames() {
            return new String[] {
                getMethodWithLockName()
            };
        }

        public void lock() {
            synchronized(monitor) {
                Test.field++;
            }
        }

        /**
         * Usage:
         * Test &lt;inflate monitor&gt;
         */
        public static void main(String args[]) throws Throwable {
            Asserts.assertGTE(args.length, 1, "One argument required.");
            Test test = new Test();

            if (Boolean.valueOf(args[0])) {
                AbortProvoker.inflateMonitor(test.monitor);
            }
            for (long i = 0L; i < Test.TOTAL_ITERATIONS; i++) {
                test.lock();
            }
        }
    }

    public static void main(String args[]) throws Throwable {
        new TestRTMTotalCountIncrRate().test();
    }
}
