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
import jdk.test.lib.Asserts;

import java.util.regex.Pattern;

/*
 * @test
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler1.enabled & vm.compiler2.enabled & vm.flagless
 * @summary Test different custom run tests.
 * @library /test/lib /testlibrary_tests /
 * @run driver ${test.main.class}
 * @run main/othervm -DIgnoreSkipIR=true ${test.main.class} IgnoreSkipIR
 */

public class TestSkip {
    public static void main(String[] args) {
        boolean isSkipIR = args.length == 1;
        try {
            TestFramework.run();
            Asserts.assertFalse(isSkipIR);
        } catch (IRViolationException e) {
            String message = e.getExceptionInfo();
            Asserts.assertTrue(message.contains("testSkipIR1\" - [Failed IR rules: 1]"));
            Asserts.assertTrue(message.contains("testSkipIR2\" - [Failed IR rules: 1]"));
            Asserts.assertTrue(message.contains("testSkipIR3\" - [Failed IR rules: 2]"));
            assertIrRuleCount(message, 1, 3);
            assertIrRuleCount(message, 3, 1);
        }

        try {
            TestFramework.runWithFlags("-DIgnoreSkip=true");
            Asserts.fail("should not reach");
        } catch (TestVMException e) {
            String message = e.getExceptionInfo();
            Asserts.assertTrue(message.contains("testSkip() should not be executed"), "should find");
            Asserts.assertTrue(message.contains("testSkipWithIR() should not be executed"), "should find");
            Asserts.assertTrue(message.contains("testSkipWithRunMultipleTests1() should not be executed"), "should find");
        }
    }

    private static void assertIrRuleCount(String message, int ruleIndex, int expectedCount) {
        Asserts.assertEQ(expectedCount,  message.split(Pattern.quote("IR rule " + ruleIndex), -1).length - 1);
    }

    @Test
    @Skip
    public static void testSkip() {
        throw new RuntimeException("testSkip() should not be executed");
    }

    @Test
    @Skip
    public static void testSkipWithRun() {
        throw new RuntimeException("testSkipWithRun() should not be executed");
    }

    @Run(test = "testSkipWithRun")
    public static void runTestSkipWithRun() {
        testSkipWithRun();
    }

    @Test
    @Skip
    public static void testSkipWithRunMultipleTests1() {
        throw new RuntimeException("testSkipWithRunMultipleTests1() should not be executed");
    }

    @Test
    @Skip
    public static void testSkipWithRunMultipleTests2() {
        throw new RuntimeException("testSkipWithRunMultipleTests2() should not be executed");
    }

    @Run(test = {"testSkipWithRunMultipleTests1",
                 "testSkipWithRunMultipleTests2"})
    public static void runTestSkipWithRunMultipleTests1() {
        testSkipWithRunMultipleTests1();
        testSkipWithRunMultipleTests2();
    }

    @Test
    @Skip
    @IR(counts = {IRNode.CALL, "2"}) // normally fails
    public static void testSkipWithIR() {
        throw new RuntimeException("testSkipWithIR() should not be executed");
    }

    @Test
    @SkipIR(1)
    @IR(counts = {IRNode.CALL, "2"}) // normally fails
    public static void testSkipIR1() {}

    @Test
    @SkipIR(1)
    @IR(counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    public static void testSkipIR2() {}

    @Test
    @SkipIR({1, 3})
    @IR(counts = {IRNode.CALL, "2"}) // normally fails
    @IR(failOn = IRNode.CALL)
    @IR(counts = {IRNode.CALL, "1"}) // normally fails
    public static void testSkipIR3() {}
}
