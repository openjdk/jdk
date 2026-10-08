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

package compiler.c2.irTests;

import compiler.lib.generators.*;
import compiler.lib.ir_framework.*;
import jdk.test.lib.Asserts;

/*
 * @test
 * @bug 8380446
 * @summary Test for effective constant shift counts with low 5 or 6 bits known.
 * @library /test/lib /
 * @run driver ${test.main.class}
 */

public class TestEffectiveConstantShiftCount {
    private static final Generator<Integer> INTS = Generators.G.ints();
    private static final Generator<Long> LONGS = Generators.G.longs();

    private static final RestrictableGenerator<Integer> INTS_32 = Generators.G.ints().restricted(1, 32);
    private static final RestrictableGenerator<Integer> INTS_64 = Generators.G.ints().restricted(1, 64);

    private static final int INT_LOW = INTS_32.next();
    private static final int LONG_LOW = INTS_64.next();
    private static final int RAND_I = INTS.next();
    private static final long RAND_L = LONGS.next();
    private static final int RAND_COUNT = INTS.next();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @ForceInline
    private static int intCount(int x) {
        return (x & ~31) | INT_LOW;
    }

    @ForceInline
    private static int longCount(int x) {
        return (x & ~63) | LONG_LOW;
    }

    // ---------------- shift test for int ----------------
    @Test
    @IR(failOn = IRNode.LSHIFT_I)
    public static boolean testIntLShift(int x, int count) {
        int shifted1 = x << intCount(count);
        int shifted2 = x << INT_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.RSHIFT_I)
    public static boolean testIntRShift(int x, int count) {
        int shifted1 = x >> intCount(count);
        int shifted2 = x >> INT_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.URSHIFT_I)
    public static boolean testIntURShift(int x, int count) {
        int shifted1 = x >>> intCount(count);
        int shifted2 = x >>> INT_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.LSHIFT_I)
    public static boolean testIntLShiftWithLoopOpt(int x, int count) {
        int i;
        for (i = 1; i < INT_LOW; i++);
        int mCount = (count & ~31) | i;
        int shifted1 = x << mCount;
        int shifted2 = x << INT_LOW;
        return shifted1 == shifted2;
    }

    // ---------------- shift test for long ----------------
    @Test
    @IR(failOn = IRNode.LSHIFT_L)
    public static boolean testLongLShift(long x, int count) {
        long shifted1 = x << longCount(count);
        long shifted2 = x << LONG_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.RSHIFT_L)
    public static boolean testLongRShift(long x, int count) {
        long shifted1 = x >> longCount(count);
        long shifted2 = x >> LONG_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.URSHIFT_L)
    public static boolean testLongURShift(long x, int count) {
        long shifted1 = x >>> longCount(count);
        long shifted2 = x >>> LONG_LOW;
        return shifted1 == shifted2;
    }

    @Test
    @IR(failOn = IRNode.LSHIFT_L)
    public static boolean testLongLShiftWithLoopOpt(long x, int count) {
        int i;
        for (i = 1; i < LONG_LOW; i++);
        int mCount = (count & ~63) | i;
        long shifted1 = x << mCount;
        long shifted2 = x << LONG_LOW;
        return shifted1 == shifted2;
    }

    @Run(test = {"testIntLShift",
                 "testIntURShift",
                 "testIntRShift",
                 "testIntLShiftWithLoopOpt",
                 "testLongLShift",
                 "testLongURShift",
                 "testLongRShift",
                 "testLongLShiftWithLoopOpt"})
    public static void runShift() {
        Asserts.assertTrue(testIntLShift(RAND_I, RAND_COUNT));
        Asserts.assertTrue(testIntURShift(RAND_I, RAND_COUNT));
        Asserts.assertTrue(testIntRShift(RAND_I, RAND_COUNT));
        Asserts.assertTrue(testIntLShiftWithLoopOpt(RAND_I, RAND_COUNT));
        Asserts.assertTrue(testLongLShift(RAND_L, RAND_COUNT));
        Asserts.assertTrue(testLongURShift(RAND_L, RAND_COUNT));
        Asserts.assertTrue(testLongRShift(RAND_L, RAND_COUNT));
        Asserts.assertTrue(testLongLShiftWithLoopOpt(RAND_L, RAND_COUNT));
    }
}
