/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import java.util.Random;

/*
 * @test
 * @bug 8324641
 * @requires vm.debug == true & vm.compMode != "Xint" & vm.compiler2.enabled & vm.flagless
 * @summary Test different custom run tests.
 * @library /test/lib /testlibrary_tests /
 * @run driver ir_framework.tests.TestSetupTests
 */

public class TestSetupTests {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream oldOut = System.out;
        System.setOut(ps);

        // Positive tests in TestSetupTests class
        TestFramework.run();

        // Positive tests in TestSetupTestsWithFields class
        TestFramework.run(TestSetupTestsWithFields.class);

        // Positive tests in TestSetupTestsSetupInfo class
        TestFramework.run(TestSetupTestsSetupInfo.class);

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

            Asserts.assertTrue(e.getExceptionInfo().contains("testTooManyArgs2"));
            Asserts.assertTrue(e.getExceptionInfo().contains("IllegalArgumentException: wrong number of arguments: 3 expected: 0"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testTooFewArgs2"));
            Asserts.assertTrue(e.getExceptionInfo().contains("IllegalArgumentException: wrong number of arguments: 0 expected: 3"));

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

            Asserts.assertTrue(e.getExceptionInfo().contains("setupThrowInSetup"));
            Asserts.assertTrue(e.getExceptionInfo().contains("BadCheckedTestException: expected setup"));
            Asserts.assertTrue(e.getExceptionInfo().contains("testThrowInTest"));
            Asserts.assertTrue(e.getExceptionInfo().contains("BadCheckedTestException: expected test"));
            Asserts.assertTrue(e.getExceptionInfo().contains("checkThrowInCheck"));
            Asserts.assertTrue(e.getExceptionInfo().contains("BadCheckedTestException: expected check"));

            // Check number of total failures:
            Asserts.assertEQ(e.getExceptionInfo().split("argument type mismatch").length - 1, 2);
            Asserts.assertEQ(e.getExceptionInfo().split("There was an error while invoking setup").length - 1, 5);
            Asserts.assertEQ(e.getExceptionInfo().split("There was an error while invoking @Test").length - 1, 7);
            Asserts.assertEQ(e.getExceptionInfo().split("There was an error while invoking @Check").length - 1, 1);
            Asserts.assertEQ(e.getExceptionInfo().split("BadCheckedTestException").length - 1, 3);
            Asserts.assertTrue(e.getExceptionInfo().contains("Test Failures (13)"));
        }
    }

    // ---------- Setup Nothing ---------------
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

    // ---------- Setup Arrays ---------------
    @Setup
    static Object[] setupArrayII(SetupInfo info) {
        int[] a = new int[1_000];
        int[] b = new int[1_000];
        int x = info.invocationCounter();
        for (int i = 0; i < a.length; i++) { a[i] = x + i; }
        for (int i = 0; i < a.length; i++) { b[i] = x - i; }
        return new Object[]{a, b};
    }

    @Test
    @Arguments(setup = "setupArrayII")
    static void testSetupArrayII(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            int y = a[i] - b[i];
            if (y != 2 * i) {
                throw new RuntimeException("bad values for i=" + i + " a[i]=" + a[i] + " b[i]=" + b[i]);
            }
        }
    }

    // ---------- Setup "linked" random values ---------------
    @Setup
    static Object[] setupLinkedII() {
        int r = RANDOM.nextInt();
        return new Object[]{ r, r + 42};
    }

    @Test
    @Arguments(setup = "setupLinkedII")
    static int testSetupLinkedII(int a, int b) {
        return b - a;
    }

    @Check(test = "testSetupLinkedII")
    static void checkSetupLinkedII(int res) {
        if (res != 42) { throw new RuntimeException("wrong result " + res); }
    }
}

class TestSetupTestsWithFields {
    int iFld1, iFld2, iFld3;

    @Setup
    Object[] setupTest1(SetupInfo info) {
        iFld1 = info.invocationCounter() + 1;
        iFld2 = info.invocationCounter() + 2;
        iFld3 = info.invocationCounter() + 3;
        return new Object[]{info.invocationCounter()}; // -> argument x in test
    }

    @Test
    @Arguments(setup = "setupTest1")
    int test1(int x) {
        if (iFld1 != x + 1) { throw new RuntimeException("iFld1 wrong value: " + iFld1 + " != " + (x + 1)); }
        if (iFld2 != x + 2) { throw new RuntimeException("iFld2 wrong value: " + iFld2 + " != " + (x + 2)); }
        if (iFld3 != x + 3) { throw new RuntimeException("iFld3 wrong value: " + iFld3 + " != " + (x + 3)); }
        iFld1++;
        iFld2++;
        iFld3++;
        return x + 5; // -> argument y in check
    }

    @Check(test = "test1")
    void checkTest1(int y) {
        if (iFld1 != y - 3) { throw new RuntimeException("iFld1 wrong value: " + iFld1 + " != " + (y - 3)); }
        if (iFld2 != y - 2) { throw new RuntimeException("iFld2 wrong value: " + iFld2 + " != " + (y - 2)); }
        if (iFld3 != y - 1) { throw new RuntimeException("iFld3 wrong value: " + iFld3 + " != " + (y - 1)); }
    }
}

class TestSetupTestsSetupInfo {
    static int lastCnt = -1;

    @Setup
    Object[] setupTest1(SetupInfo info) {
        int cnt = info.invocationCounter();
        // Check that we increment every time
        if (cnt - 1 != lastCnt) {
            throw new RuntimeException("SetupInfo invocationCounter does not increment correctly: " +
                                       cnt + ", vs last: " + lastCnt);
        }
        lastCnt = cnt;
        return new Object[]{1, 2};
    }

    @Test
    @Arguments(setup = "setupTest1")
    void test1(int a, int b) {}
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

    @Setup
    public Object[] setupTooManyArgs2() {
      return new Object[]{1, 2, 3};
    }

    @Test
    @Arguments(setup = "setupTooManyArgs2")
    public void testTooManyArgs2() {}

    @Setup
    public Object[] setupTooFewArgs2() {
      return new Object[]{};
    }

    @Test
    @Arguments(setup = "setupTooFewArgs2")
    public void testTooFewArgs2(int a, int b, int c) {}

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

    // ----------------- Throw in Setup -----------
    @Setup
    public Object[] setupThrowInSetup() {
        throw new BadCheckedTestException("expected setup");
    }

    @Test
    @Arguments(setup = "setupThrowInSetup")
    public void testThrowInSetup() {
        throw new RuntimeException("should have thrown in setup");
    }

    // ----------------- Throw in Test  -----------
    @Setup
    public Object[] setupThrowInTest(SetupInfo info) {
        return new Object[]{ info.invocationCounter() };
    }

    @Test
    @Arguments(setup = "setupThrowInTest")
    public int testThrowInTest(int x) {
        throw new BadCheckedTestException("expected test");
    }

    @Check(test = "testThrowInTest")
    public void checkThrowInTest(int x) {
        throw new RuntimeException("should have thrown in test");
    }

    // ----------------- Throw in Check -----------
    @Setup
    public Object[] setupThrowInCheck(SetupInfo info) {
        return new Object[]{ info.invocationCounter() };
    }

    @Test
    @Arguments(setup = "setupThrowInCheck")
    public int testThrowInCheck(int x) {
        return x + 1;
    }

    @Check(test = "testThrowInCheck")
    public void checkThrowInCheck(int x) {
        throw new BadCheckedTestException("expected check");
    }


    static class BadCheckedTestException extends RuntimeException {
        BadCheckedTestException(String s) {
            super(s);
        }
    }
}
