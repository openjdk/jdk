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
            Asserts.assertTrue(e.getExceptionInfo().contains("Test Failures (2)"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testTooManyArgs"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testTooFewArgs"));
            Asserts.assertTrue(e.getExceptionInfo().split("There was an error while invoking @Test").length == 3);
        }

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
    public void setupTestGood1() {}
 
    @Test
    @Arguments(setup = "setupTestGood1")
    public void testGood1() {}

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
