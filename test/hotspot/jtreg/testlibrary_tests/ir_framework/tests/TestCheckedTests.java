/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.driver.IRViolationException;
import compiler.lib.ir_framework.driver.TestVMException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8273410
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test different custom run tests.
 * @library /test/lib /testlibrary_tests /
 * @run driver ir_framework.tests.TestCheckedTests
 */

public class TestCheckedTests {
    public int iFld;

    public static void main(String[] args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        System.setOut(ps);

        TestFramework.run();
        try {
            TestFramework.run(BadIRAndRuntimeCheckedTests.class);
            Utils.shouldHaveThrownException(baos.toString());
        } catch (TestVMException e) {
            System.setOut(oldOut);
            Asserts.assertTrue(e.getExceptionInfo().contains("Test Failures (2)"));
            Asserts.assertTrue(e.getExceptionInfo().contains("checkTestBad3"));
            Asserts.assertTrue(e.getExceptionInfo().contains("checkTestBad5"));
            Asserts.assertTrue(e.getExceptionInfo().split("BadCheckedTestException").length == 3);
            Asserts.assertFalse(e.getExceptionInfo().contains("Failed IR Rules"));
        }

        System.setOut(ps);
        try {
            TestFramework.run(BadIRCheckedTests.class);
            Utils.shouldHaveThrownException(baos.toString());
        } catch (IRViolationException e) {
            System.setOut(oldOut);
            Asserts.assertTrue(e.getExceptionInfo().contains("Failed IR Rules (3)"));
        }
    }

    @Test
    @IR(counts = {IRNode.STORE_I, "1"})
    public void testGood1() {
        iFld = 3;
    }

    @Check(test = "testGood1")
    public void checkTestGood1(TestInfo info) {
    }

    @Test
    @IR(failOn = IRNode.LOAD)
    public int testGood2() {
        iFld = 3;
        return 3;
    }

    @Check(test = "testGood2")
    public void sameName(int retValue) {
        if (retValue != 3) {
            throw new RuntimeException("must be 3 but was " + retValue);
        }
    }

    @Test
    @Arguments(Argument.NUMBER_42)
    @IR(failOn = IRNode.LOAD)
    @IR(counts = {IRNode.STORE_I, "0"})
    public int testGood3(int x) {
        return x;
    }

    @Check(test = "testGood3")
    public void sameName(int retValue, TestInfo info) {
        if (retValue != 42) {
            throw new RuntimeException("must be 42");
        }
    }
}

class BadIRAndRuntimeCheckedTests {
    public int iFld;

    @Test
    @IR(counts = {IRNode.STORE_I, "2"})
    public void testBad1() {
        iFld = 3;
    }

    @Check(test = "testBad1")
    public void checkTestBad1(TestInfo info) {
    }

    @Test
    @IR(failOn = IRNode.STORE_I)
    public int testBad2() {
        iFld = 3;
        return 3;
    }

    @Check(test = "testBad2")
    public void sameName(int retValue) {
        if (retValue != 3) {
            throw new RuntimeException("must be 3");
        }
    }

    @Test
    @Arguments(Argument.NUMBER_42)
    public int testBad3(int x) {
        return x;
    }

    @Check(test = "testBad3")
    public void checkTestBad3(int retValue) {
        if (retValue == 42) {
            // Always
            throw new BadCheckedTestException("expected");
        }
    }

    @Test
    @Arguments(Argument.NUMBER_42)
    @IR(failOn = IRNode.LOAD)
    @IR(counts = {IRNode.STORE_I, "1"})
    public int testBad4(int x) {
        return x;
    }

    @Check(test = "testBad4")
    public void sameName(int retValue, TestInfo info) {
        if (retValue != 42) {
            throw new RuntimeException("must be 42");
        }
    }

    @Test
    @Arguments(Argument.NUMBER_42)
    public int testBad5(int x) {
        return x;
    }

    @Check(test = "testBad5")
    public void checkTestBad5(int retValue) {
        if (retValue == 42) {
            // Always
            throw new BadCheckedTestException("expected");
        }
    }
}

class BadIRCheckedTests {
    public int iFld;

    @Test
    @IR(counts = {IRNode.STORE_I, "2"})
    public void testBad1() {
        iFld = 3;
    }

    @Check(test = "testBad1")
    public void checkTestBad1(TestInfo info) {
    }

    @Test
    @IR(failOn = IRNode.STORE_I)
    public int testBad2() {
        iFld = 3;
        return 3;
    }

    @Check(test = "testBad2")
    public void sameName(int retValue) {
        if (retValue != 3) {
            throw new RuntimeException("must be 3");
        }
    }

    @Test
    @Arguments(Argument.NUMBER_42)
    @IR(failOn = IRNode.LOAD)
    @IR(counts = {IRNode.STORE_I, "1"})
    public int testBad4(int x) {
        return x;
    }

    @Check(test = "testBad4")
    public void sameName(int retValue, TestInfo info) {
        if (retValue != 42) {
            throw new RuntimeException("must be 42");
        }
    }
}

class BadCheckedTestException extends RuntimeException {
    BadCheckedTestException(String s) {
        super(s);
    }
}
