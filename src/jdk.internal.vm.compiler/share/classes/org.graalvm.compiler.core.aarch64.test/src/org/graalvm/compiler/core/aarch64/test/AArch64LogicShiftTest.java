/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018, Arm Limited and affiliates. All rights reserved.
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



package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp;
import org.junit.Test;

import java.util.function.Predicate;

public class AArch64LogicShiftTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof AArch64ArithmeticOp.BinaryShiftOp);

    /**
     * logicShift match rule test for instruction "and" with int type.
     */
    public static int andShiftInt(int input0, int input1) {
        int value = input0 & (input1 << 5);
        value += input0 & (input1 >> 5);
        value += input0 & (input1 >>> 5);
        return value;
    }

    @Test
    public void testAndShiftInt() {
        test("andShiftInt", 123, 425);
        checkLIR("andShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "and" with long type.
     */
    public static long andShiftLong(long input0, long input1) {
        long value = input0 & (input1 << 5);
        value += input0 & (input1 >> 5);
        value += input0 & (input1 >>> 5);
        return value;
    }

    @Test
    public void testAndShiftLong() {
        test("andShiftLong", 1234567L, 123L);
        checkLIR("andShiftLong", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "orr" with int type.
     */
    public static int orrShiftInt(int input0, int input1) {
        int value = input0 | (input1 << 5);
        value += input0 | (input1 >> 5);
        value += input0 | (input1 >>> 5);
        return value;
    }

    @Test
    public void testOrrShiftInt() {
        test("orrShiftInt", 123, 425);
        checkLIR("orrShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "orr" with long type.
     */
    public static long orrShiftLong(long input0, long input1) {
        long value = input0 | (input1 << 5);
        value += input0 | (input1 >> 5);
        value += input0 | (input1 >>> 5);
        return value;
    }

    @Test
    public void testOrrShiftLong() {
        test("orrShiftLong", 1234567L, 123L);
        checkLIR("orrShiftLong", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "eor" with int type.
     */
    public static int eorShiftInt(int input0, int input1) {
        int value = input0 ^ (input1 << 5);
        value += input0 ^ (input1 >> 5);
        value += input0 ^ (input1 >>> 5);
        return value;
    }

    @Test
    public void testEorShiftInt() {
        test("eorShiftInt", 123, 425);
        checkLIR("eorShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "eor" with long type.
     */
    public static long eorShiftLong(long input0, long input1) {
        long value = input0 ^ (input1 << 5);
        value += input0 ^ (input1 >> 5);
        value += input0 ^ (input1 >>> 5);
        return value;
    }

    @Test
    public void testEorShiftLong() {
        test("eorShiftLong", 1234567L, 123L);
        checkLIR("eorShiftLong", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "bic" with int type.
     */
    public static int bicShiftInt(int input0, int input1) {
        int value = input0 & ~(input1 << 5);
        value += input0 & ~(input1 >> 5);
        value += input0 & ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testBicShiftInt() {
        test("bicShiftInt", 123, 425);
        checkLIR("bicShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "bic" with long type.
     */
    public static long bicShiftLong(long input0, long input1) {
        long value = input0 & ~(input1 << 5);
        value += input0 & ~(input1 >> 5);
        value += input0 & ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testBicShiftLong() {
        test("bicShiftLong", 1234567L, 123L);
        checkLIR("bicShiftLong", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "orn" with int type.
     */
    public static int ornShiftInt(int input0, int input1) {
        int value = input0 | ~(input1 << 5);
        value += input0 | ~(input1 >> 5);
        value += input0 | ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testOrnShiftInt() {
        test("ornShiftInt", 123, 425);
        checkLIR("ornShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "orn" with long type.
     */
    public static long ornShiftLong(long input0, long input1) {
        long value = input0 | ~(input1 << 5);
        value += input0 | ~(input1 >> 5);
        value += input0 | ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testOrnShiftLong() {
        test("ornShiftLong", 1234567L, 123L);
        checkLIR("ornShiftLong", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "eon" with int type.
     */
    public static int eonShiftInt(int input0, int input1) {
        int value = input0 ^ ~(input1 << 5);
        value += input0 ^ ~(input1 >> 5);
        value += input0 ^ ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testEonShiftInt() {
        test("eonShiftInt", 123, 425);
        checkLIR("eonShiftInt", predicate, 3);
    }

    /**
     * logicShift match rule test for instruction "eon" with long type.
     */
    public static long eonShiftLong(long input0, long input1) {
        long value = input0 ^ ~(input1 << 5);
        value += input0 ^ ~(input1 >> 5);
        value += input0 ^ ~(input1 >>> 5);
        return value;
    }

    @Test
    public void testEonShiftLong() {
        test("eonShiftLong", 1234567L, 123L);
        checkLIR("eonShiftLong", predicate, 3);
    }
}
