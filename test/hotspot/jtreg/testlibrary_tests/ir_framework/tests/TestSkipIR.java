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

package ir_framework.tests;

import compiler.lib.ir_framework.*;
import compiler.lib.ir_framework.driver.irmatching.IRViolationException;
import jdk.test.lib.Asserts;

import java.util.regex.Pattern;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test skipping IR rules with IR skip attribute
 * @library /test/lib /testlibrary_tests /
 * @run driver ${test.main.class}
 */

public class TestSkipIR {
    public static void main(String[] args) {
        runGood();
        runIgnoreSkipIR();
        runIgnoreSkipAndSkipIR();
        runNotCompiledTest();
        runNotCompilable();
    }

    private static void runGood() {
        TestFramework.run();
        TestFramework.runWithFlags("-DIgnoreSkip=true"); // IR rules still skipped
        TestFramework.run(NotCompiledSkippedIR.class); // IR rules still skipped
    }

    private static void runIgnoreSkipIR() {
        try {
            TestFramework.runWithFlags("-DIgnoreSkipIR=true");
            Asserts.fail("should fail IR matching");
        } catch (IRViolationException e) {
            String message = e.getExceptionInfo();
            checkHeader(message, 4, 3);
            checkTestMethods(message, "testNoSkip");
            assertIrRuleCount(message, 1, 3);
            assertIrRuleCount(message, 3, 1);
        }
    }

    private static void runIgnoreSkipAndSkipIR() {
        try {
            TestFramework.runWithFlags("-DIgnoreSkip=true", "-DIgnoreSkipIR=true");
            Asserts.fail("should fail IR matching");
        } catch (IRViolationException e) {
            String message = e.getExceptionInfo();
            checkHeader(message, 8, 6);
            checkTestMethods(message, "testNoSkip");
            checkTestMethods(message, "testYesSkip");
            assertIrRuleCount(message, 1, 6);
            assertIrRuleCount(message, 3, 2);
        }
    }

    private static void runNotCompiledTest() {
        try {
            new TestFramework(NotCompiledSkippedIR.class)
                    .addFlags("-DIgnoreSkipIR=true")
                    .start();
            Asserts.fail("should fail IR matching");
        } catch (IRViolationException e) {
            String message = e.getExceptionInfo();
            checkHeader(message, 1, 1);
            Asserts.assertTrue(message.contains("test\" - [Failed IR rules: 1]"));
            Asserts.assertTrue(message.contains("Method was not compiled. Did you specify a @Run method in STANDALONE mode"));
        }
    }

    private static void runNotCompilable() {
        new TestFramework(NotCompilableSkippedIR.class)
                .addFlags("-XX:CompileCommand=compileonly,*NotCompilableSkippedIR::test,0111")
                .start();
        new TestFramework(NotCompilableSkippedIR.class)
                .addFlags("-DIgnoreSkipIR=true",
                          "-XX:CompileCommand=compileonly,*NotCompilableSkippedIR::test,0111")
                .start();
    }

    private static void checkHeader(String message, int irRuleCount, int methodCount) {
        Asserts.assertTrue(message.contains("Failed IR Rules (" + irRuleCount + ") of Methods (" + methodCount + ")"));

    }

    private static void checkTestMethods(String message, String methodPrefix) {
        Asserts.assertTrue(message.contains(methodPrefix + "1\" - [Failed IR rules: 1]"));
        Asserts.assertTrue(message.contains(methodPrefix + "2\" - [Failed IR rules: 1]"));
        Asserts.assertTrue(message.contains(methodPrefix + "3\" - [Failed IR rules: 2]"));
    }

    private static void assertIrRuleCount(String message, int ruleIndex, int expectedCount) {
        Asserts.assertEQ(expectedCount, message.split(Pattern.quote("[Marked as skip] @IR rule " + ruleIndex), -1).length - 1);
    }

    @Test
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    public static void testNoSkip1() {}

    @Test
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    public static void testNoSkip2() {}

    @Test
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    @IR(skip = true,
        counts = {IRNode.CALL, "1"}) // normally fails
    public static void testNoSkip3() {}

    @Test
    @Skip
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    public static void testYesSkip1() {}

    @Test
    @Skip
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    public static void testYesSkip2() {}

    @Test
    @Skip
    @IR(skip = true,
        counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    @IR(skip = true,
        counts = {IRNode.CALL, "1"}) // normally fails
    public static void testYesSkip3() {}
}

class NotCompiledSkippedIR {
    static int iFld;

    @Test
    @IR(skip = true,
        failOn = IRNode.STORE_I)
    public static void test() {
        iFld = 34;
    }

    @Run(test = "test", mode = RunMode.STANDALONE)
    public static void run() {
        test(); // Only invoke once, not enough to trigger C2 compilation
    }
}

class NotCompilableSkippedIR {
    static int iFld;

    @Test(allowNotCompilable = true) // We disable compilation for C2 for this method
    @IR(skip = true,
        failOn = IRNode.STORE_I)
    public static void test() {
        iFld = 34;
    }
}
