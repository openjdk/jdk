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
    private static int count = 0;

    public static void main(String[] args) {
        TestFramework.run();
    }

    private static int intCount(int x) {
        return x & ~31;
    }

    private static int longCount(int x) {
        return x & ~63;
    }

    // ---------------- shift test for int ----------------
    @Test
    @IR(failOn = {IRNode.LSHIFT_I},  phase = CompilePhase.AFTER_PARSING)
    public static int testIntLShift(int x, int count) {
        return x << intCount(count);
    }

    @Test
    @IR(failOn = {IRNode.RSHIFT_I},  phase = CompilePhase.AFTER_PARSING)
    public static int testIntRShift(int x, int count) {
        return x >> intCount(count);
    }

    @Test
    @IR(failOn = {IRNode.URSHIFT_I}, phase = CompilePhase.AFTER_PARSING)
    public static int testIntURShift(int x, int count) {
        return x >>> intCount(count);
    }

    // ---------------- shift test for long ----------------
    @Test
    @IR(failOn = {IRNode.LSHIFT_L}, phase = CompilePhase.AFTER_PARSING)
    public static long testLongLShift(long x, int count) {
        return x << longCount(count);
    }

    @Test
    @IR(failOn = {IRNode.RSHIFT_L}, phase = CompilePhase.AFTER_PARSING)
    public static long testLongRShift(long x, int count) {
        return x >> longCount(count);
    }

    @Test
    @IR(failOn = {IRNode.URSHIFT_L}, phase = CompilePhase.AFTER_PARSING)
    public static long testLongURShift(long x, int count) {
        return x >>> longCount(count);
    }

    @Run(test = {"testIntLShift",
                 "testIntURShift",
                 "testIntRShift",
                 "testLongLShift",
                 "testLongURShift",
                 "testLongRShift"})
    public static void runShift() {
        count++;
        int i = 0x12345678;
        Asserts.assertEQ(testIntLShift(i, count), i);
        Asserts.assertEQ(testIntURShift(i, count), i);
        Asserts.assertEQ(testIntRShift(i, count), i);

        long l = 0xFEDCBA9876543210L;
        Asserts.assertEQ(testLongLShift(l, count), l);
        Asserts.assertEQ(testLongURShift(l, count), l);
        Asserts.assertEQ(testLongRShift(l, count), l);
    }
}
