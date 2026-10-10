/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package testlibrary_tests.ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.TestVMException;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import compiler.lib.ir_framework.shared.TestFormatException;
import jdk.internal.misc.Unsafe;
import jdk.test.lib.Asserts;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test different custom run tests.
 * @modules java.base/jdk.internal.misc:+open
 * @library /test/lib /testlibrary_tests /
 * @run driver ${test.main.class}
 */

public class TestSkip {
    public static void main(String[] args) {
        runGood();
        runMix();
        runViolation();
        runVMCrash();
        runIRVerification();
    }

    private static void runGood() {
        TestFramework.run(SkipBasics.class);
    }

    private static void runMix() {
        try {
            TestFramework.run(Mix.class);
            Asserts.fail("Should have thrown");
        } catch (TestVMException e) {
            Asserts.assertTrue(e.getExceptionInfo().contains("testFailNoSkip() no @Skip"));
        }

        TestFramework testFramework = new TestFramework(Mix.class);
        testFramework.addFlags("-DIgnoreSkip=true");
        try {
            testFramework.start();
            Asserts.fail("Should have thrown");
        } catch (TestVMException e) {
            Asserts.assertTrue(e.getExceptionInfo().contains("testFailNoSkip() no @Skip"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testFailWithSkip() with @Skip"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testWithSingleTest() with @Skip"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testWithMultipleTestsFail() with @Skip"));
        }
    }

    private static void runViolation() {
        runViolation(new TestFramework(Violation.class));
        runViolation(new TestFramework(Violation.class).addFlags("-DIgnoreSkip=true"));
    }

    private static void runViolation(TestFramework testFramework) {
        try {
            testFramework.start();
            Asserts.fail("Should throw");
        } catch (TestFormatException e) {
            String message = e.getMessage();
            Asserts.assertTrue(message.contains("Violations (2)"));
            Asserts.assertTrue(message.contains("@Run-methods with multiple tests can only either skip none or all " +
                                                "methods with @Skip"));
            Asserts.assertTrue(message.contains("run1()"));
            Asserts.assertTrue(message.contains("two()"));
            Asserts.assertTrue(message.contains("run2()"));
            Asserts.assertTrue(message.contains("missing"));
        }
    }

    private static void runVMCrash() {
        new TestFramework(VMCrash.class)
                .addFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED")
                .start();

        try {
            new TestFramework(VMCrash.class)
                    .addFlags("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-DIgnoreSkip=true")
                    .start();
            Asserts.fail("Should have thrown");
        } catch (TestVMException e) {
            // Expected.
            Asserts.assertTrue(e.getExceptionInfo().contains("A fatal error has been detected"));
        }
    }

    private static void runIRVerification() {
        TestFramework.run(IRVerification.class);

        try {
            new TestFramework(IRVerification.class)
                    .addFlags("-DIgnoreSkip=true")
                    .start();
            Asserts.fail("Should have thrown");
        } catch (IRViolationException e) {
            // Expected.
            String message = e.getExceptionInfo();
            Asserts.assertTrue(message.contains("Failed IR Rules (3) of Methods (3)"));
            Asserts.assertTrue(message.contains("Method \"testlibrary_tests.ir_framework.tests." +
                                                "TestSkip$IRVerification::testFailingIrCustomRunTest"));
            Asserts.assertTrue(message.contains("Method \"testlibrary_tests.ir_framework.tests." +
                                                "TestSkip$IRVerification::testFailingIrCheckedTest"));
            Asserts.assertTrue(message.contains("Method \"testlibrary_tests.ir_framework.tests." +
                                                "TestSkip$IRVerification::testFailingIrBaseTest"));
        }
    }

    static class SkipBasics {
        @Skip
        @Test
        public static void testSkip() {
            throw new RuntimeException("testSkip() with @Skip");
        }

        @Skip
        @Test
        public static void testWithSingleTest() {
            throw new RuntimeException("testSkipWithRun() with @Skip");
        }

        @Run(test = "testWithSingleTest")
        public static void runWithSingleTest() {
            try {
                testWithSingleTest();
            } catch (RuntimeException e) {
                throw new RuntimeException("From @Run", e);
            }
        }

        @Skip
        @Test
        public static void testWithMultipleTestsAllSkipFail1() {
            throw new RuntimeException("testWithMultipleTestsAllSkipFail1() with @Skip");
        }

        @Skip
        @Test
        public static void testWithMultipleTestsAllSkipFail2() {
            throw new RuntimeException("testWithMultipleTestsAllSkipFail2() with @Skip");
        }

        @Run(test = {"testWithMultipleTestsAllSkipFail1",
                     "testWithMultipleTestsAllSkipFail2"})
        public static void runWithMultipleTestsAllSkipFail() {
            testWithMultipleTestsAllSkipFail1();
            testWithMultipleTestsAllSkipFail2();
        }
    }

    static class IRVerification {
        static int iFld;

        @Skip
        @Test
        @IR(failOn = IRNode.STORE_I)
        public static void testFailingIrBaseTest() {
            iFld = 34;
        }

        @Skip
        @Test
        @IR(failOn = IRNode.STORE_I)
        public static void testFailingIrCheckedTest() {
            iFld = 34;
        }

        @Check(test = "testFailingIrCheckedTest")
        public static void checkTest() {}

        @Skip
        @Test
        @IR(failOn = IRNode.STORE_I)
        public static void testFailingIrCustomRunTest() {
            iFld = 34;
        }

        @Run(test = "testFailingIrCustomRunTest")
        public static void runCustomRunTest() {
            testFailingIrCustomRunTest();
        }
    }

    static class Mix {
        @Skip
        @Test
        public static void testFailWithSkip() {
            throw new RuntimeException("testFailWithSkip() with @Skip");
        }

        @Test
        public static void testFailNoSkip() {
            throw new RuntimeException("testFailNoSkip() no @Skip");
        }

        @Test
        public static void testSuccessNoSkip() {
        }

        @Test
        @Skip
        public static void testSuccessWithSkip() {
        }

        @Skip
        @Test
        public static void testWithSingleTest() {
            throw new RuntimeException("testWithSingleTest() with @Skip");
        }

        @Run(test = "testWithSingleTest")
        public static void runWithSingleTest() {
            testWithSingleTest();
        }

        // If one is skipped,
        @Skip
        @Test
        public static void testWithMultipleTestsFail() {
            throw new RuntimeException("testWithMultipleTestsFail() with @Skip");
        }

        @Skip
        @Test
        public static void testWithMultipleTestsSuccess() {
        }

        @Run(test = {"testWithMultipleTestsFail",
                     "testWithMultipleTestsSuccess"})
        public static void runWithMultipleTestsOneFail() {
            testWithMultipleTestsFail();
            testWithMultipleTestsSuccess();
        }
    }

    static class Violation {
        Violation() {
            throw new RuntimeException("should not be constructed");
        }

        @Test
        @Skip
        void one() {}

        // Forbidden, should also be reported with -DIgnoreSkip=true
        @Test
        void two() {}

        @Run(test = {"one", "two"})
        void run1() {
            one();
            two();
        }

        @Test
        @Skip
        void skipped1() {}

        @Test
        @Skip
        void skipped2() {}

        @Run(test = {"skipped1", "skipped2", "missing"})
        void run2() {
            one();
            two();
        }
    }

    static class VMCrash {
        @Test
        @Skip
        public void testCrashVM() {
            Unsafe unsafe = Unsafe.getUnsafe();
            unsafe.putAddress(0, 0); // Crash
        }
    }
}
