/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.ir_framework.driver.TestVMException;
import compiler.lib.ir_framework.shared.TestRunException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8324641
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test different custom run tests.
 * @library /test/lib /testlibrary_tests /
 * @run driver ir_framework.tests.TestSetupTests
 */

public class TestSetupTests {
    public int iFld;

    public static void main(String[] args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        System.setOut(ps);

	// Positive tests in TestSetupTests class
        TestFramework.run();

        // Positive tests with expected exceptions
        try {
            TestFramework.run(TestSetupTestsWithExpectedExceptions.class);
            Asserts.fail("Should have thrown exception");
        } catch (TestVMException e) {
            System.setOut(oldOut);
            Asserts.assertTrue(e.getExceptionInfo().contains("testTooManyArgs"));
            Asserts.assertTrue(e.getExceptionInfo().contains("IllegalArgumentException: wrong number of arguments: 3 expected: 1"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testTooFewArgs"));
            Asserts.assertTrue(e.getExceptionInfo().contains("IllegalArgumentException: wrong number of arguments: 2 expected: 3"));

            Asserts.assertTrue(e.getExceptionInfo().contains("setupTestBadSetupArgsTooMany"));
            Asserts.assertTrue(e.getExceptionInfo().contains("wrong number of arguments: 0 expected: 2"));
            Asserts.assertTrue(e.getExceptionInfo().contains("setupTestBadSetupArgsWrongType"));
            Asserts.assertTrue(e.getExceptionInfo().contains("argument type mismatch"));

            Asserts.assertTrue(e.getExceptionInfo().contains("setupReturnIntArray"));
            Asserts.assertTrue(e.getExceptionInfo().contains("class [I cannot be cast to class [Ljava.lang.Object;"));
            Asserts.assertTrue(e.getExceptionInfo().contains("setupReturnInt"));
            Asserts.assertTrue(e.getExceptionInfo().contains("class java.lang.Integer cannot be cast to class [Ljava.lang.Object;"));

            Asserts.assertTrue(e.getExceptionInfo().contains("testSetupWrongArgumentType"));
            Asserts.assertTrue(e.getExceptionInfo().contains("argument type mismatch"));

            Asserts.assertTrue(e.getExceptionInfo().contains("testSetupNull"));
            Asserts.assertTrue(e.getExceptionInfo().contains("wrong number of arguments: 0 expected: 1"));
            Asserts.assertTrue(e.getExceptionInfo().contains("Arguments: <null>"));

            // Check number of total failures:
            Asserts.assertEQ(e.getExceptionInfo().split("argument type mismatch").length - 1, 2);
            Asserts.assertEQ(e.getExceptionInfo().split("There was an error while invoking setup").length - 1, 4);
            Asserts.assertEQ(e.getExceptionInfo().split("There was an error while invoking @Test").length - 1, 4);
            Asserts.assertTrue(e.getExceptionInfo().contains("Test Failures (8)"));
        }

// TODO make sure asserts from setup get out properly
//         // Negative test with run into TestRunException
//         System.setOut(ps);
//         try {
//             TestFramework.run(TestSetupTestsWithBadRunExceptions.class);
//             Asserts.fail("Should have thrown exception");
//         } catch (TestRunException e) {
//             System.setOut(oldOut);
// //            Asserts.assertTrue(e.getExceptionInfo().contains("Failed IR Rules (3)"));
//         }
    }

    // ----------- Bad Setup Return Type -------------------------
    // TODO investigate if the values are really right here, e.g. if fields are set
    // TODO try other bad return values
    @Setup
    public void setupVoid() {}

    @Test
    @Arguments(setup = "setupVoid")
    public void testSetupVoid() {}

    @Setup
    public Object[] setupEmpty() {
        return new Object[]{};
    }

    @Test
    @Arguments(setup = "setupEmpty")
    public void testSetupEmpty() {}

    // TODO
    // - SetupInfo
    // - Object only used once
    // - move the examples here, make examples more "real examples"


//    @Test
//    @IR(counts = {IRNode.STORE_I, "1"})
//    public void testGood1() {
//        iFld = 3;
//    }
//
//    @Check(test = "testGood1")
//    public void checkTestGood1(TestInfo info) {
//    }
//
//    @Test
//    @IR(failOn = IRNode.LOAD)
//    public int testGood2() {
//        iFld = 3;
//        return 3;
//    }
//
//    @Check(test = "testGood2")
//    public void sameName(int retValue) {
//        if (retValue != 3) {
//            throw new RuntimeException("must be 3 but was " + retValue);
//        }
//    }
//
//    @Test
//    @Arguments(values = Argument.NUMBER_42)
//    @IR(failOn = IRNode.LOAD)
//    @IR(counts = {IRNode.STORE_I, "0"})
//    public int testGood3(int x) {
//        return x;
//    }
//
//    @Check(test = "testGood3")
//    public void sameName(int retValue, TestInfo info) {
//        if (retValue != 42) {
//            throw new RuntimeException("must be 42");
//        }
//    }
}

class TestSetupTestsWithExpectedExceptions {
    // ----------------- wrong number of arguments ------------------
    @Setup
    public Object[] setupTooManyArgs() {
      return new Object[]{1, 2, 3};
    }

    @Test
    @Arguments(setup = "setupTooManyArgs")
    public void testTooManyArgs(int a) {}

    @Setup
    public Object[] setupTooFewArgs() {
      return new Object[]{1, 2};
    }

    @Test
    @Arguments(setup = "setupTooFewArgs")
    public void testTooFewArgs(int a, int b, int c) {}

    // ----------------- wrong arguments for setup ------------------
    @Setup
    public Object[] setupTestBadSetupArgsTooMany(SetupInfo setupInfo, int bad) {
      return new Object[]{1, 2};
    }

    @Test
    @Arguments(setup = "setupTestBadSetupArgsTooMany")
    public void testBadSetupArgsTooMany(int a, int b) {}

    @Setup
    public Object[] setupTestBadSetupArgsWrongType(int bad) {
      return new Object[]{1, 2};
    }

    @Test
    @Arguments(setup = "setupTestBadSetupArgsWrongType")
    public void testBadSetupArgsWrongType(int a, int b) {}

    // ----------------- setup wrong return type ------------------
    @Setup
    public int[] setupReturnIntArray() {
        return new int[]{1, 2, 3};
    }

    @Test
    @Arguments(setup = "setupReturnIntArray")
    public void testSetupReturnIntArray(int a, int b, int c) {}

    @Setup
    public int setupReturnInt(SetupInfo setupInfo) {
        return setupInfo.invocationCounter();
    }

    @Test
    @Arguments(setup = "setupReturnInt")
    public void testSetupReturnInt(int a) {}

    // ----------------- setup provides wrong argument types ------
    @Setup
    public Object[] setupWrongArgumentType(SetupInfo setupInfo) {
        return new Object[]{(int)1, (long)2};
    }

    @Test
    @Arguments(setup = "setupWrongArgumentType")
    public void testSetupWrongArgumentType(long a, int b) {}

    // ----------------- setup returns null ------
    @Setup
    public Object[] setupNull() {
        return null;
    }

    @Test
    @Arguments(setup = "setupNull")
    public void testSetupNull(Object x) {}
}

// class TestSetupTestsWithBadRunExceptions {
// //    public int iFld;
// //
// //    @Test
// //    @IR(counts = {IRNode.STORE_I, "2"})
// //    public void testBad1() {
// //        iFld = 3;
// //    }
// //
// //    @Check(test = "testBad1")
// //    public void checkTestBad1(TestInfo info) {
// //    }
// //
// //    @Test
// //    @IR(failOn = IRNode.STORE_I)
// //    public int testBad2() {
// //        iFld = 3;
// //        return 3;
// //    }
// //
// //    @Check(test = "testBad2")
// //    public void sameName(int retValue) {
// //        if (retValue != 3) {
// //            throw new RuntimeException("must be 3");
// //        }
// //    }
// //
// //    @Test
// //    @Arguments(values = Argument.NUMBER_42)
// //    @IR(failOn = IRNode.LOAD)
// //    @IR(counts = {IRNode.STORE_I, "1"})
// //    public int testBad4(int x) {
// //        return x;
// //    }
// //
// //    @Check(test = "testBad4")
// //    public void sameName(int retValue, TestInfo info) {
// //        if (retValue != 42) {
// //            throw new RuntimeException("must be 42");
// //        }
// //    }
// }

class BadCheckedTestException extends RuntimeException {
    BadCheckedTestException(String s) {
        super(s);
    }
}
