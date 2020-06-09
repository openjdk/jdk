/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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

public class AArch64MergeExtendWithAddSubTest extends AArch64MatchRuleTest {

    private static final Predicate<LIRInstruction> PRED_EXTEND_ADD_SHIFT = op -> (op instanceof AArch64ArithmeticOp.ExtendedAddSubShiftOp && op.name().equals("ADD"));
    private static final Predicate<LIRInstruction> PRED_EXTEND_SUB_SHIFT = op -> (op instanceof AArch64ArithmeticOp.ExtendedAddSubShiftOp && op.name().equals("SUB"));

    private static final Long[] LONG_VALUES = {-1L, 0L, 0x1234567812345678L, 0xFFFFFFFFL, 0x12L, 0x1234L, Long.MIN_VALUE, Long.MAX_VALUE};
    private static final Integer[] INT_VALUES = {-1, 0, 0x1234, 0x12345678, Integer.MIN_VALUE, Integer.MAX_VALUE};

    private <T> void predicateExist(String[] testCases, T[] values, Predicate<LIRInstruction> predicate) {
        for (String t : testCases) {
            for (T value : values) {
                test(t, value, value);
                checkLIR(t, predicate, 1);
            }
        }
    }

    public long addI2LShift(long x, long y) {
        int z = (int) y;
        return x + (((long) z) << 3);
    }

    public long addB2LShift(long x, long y) {
        byte z = (byte) y;
        return x + (((long) z) << 2);
    }

    public long addC2LShift(long x, long y) {
        char z = (char) y;
        return x + (((long) z) << 1);
    }

    public long addS2LShift(long x, long y) {
        short z = (short) y;
        return x + (((long) z) << 4);
    }

    public long subI2LShift(long x, long y) {
        int z = (int) y;
        return x - (((long) z) << 1);
    }

    public long subB2LShift(long x, long y) {
        byte z = (byte) y;
        return x - (((long) z) << 2);
    }

    public long subC2LShift(long x, long y) {
        char z = (char) y;
        return x - (((long) z) << 3);
    }

    public long subS2LShift(long x, long y) {
        short z = (short) y;
        return x - (((long) z) << 4);
    }

    public long addI2L(long x, long y) {
        int z = (int) y;
        return x + z;
    }

    public long addB2L(long x, long y) {
        byte z = (byte) y;
        return x + z;
    }

    public long addC2L(long x, long y) {
        char z = (char) y;
        return x + z;
    }

    public long addS2L(long x, long y) {
        short z = (short) y;
        return x + z;
    }

    public int addB2S(int x, int y) {
        short a = (short) x;
        byte b = (byte) y;
        return a + b;
    }

    public int addB2SShift(int x, int y) {
        short a = (short) x;
        byte b = (byte) y;
        return a + (b << 2);
    }

    public int addB2I(int x, int y) {
        byte z = (byte) y;
        return x + z;
    }

    public int addB2IShift(int x, int y) {
        byte z = (byte) y;
        return x + (z << 3);
    }

    public int addS2I(int x, int y) {
        short z = (short) y;
        return x + z;
    }

    public int addS2IShift(int x, int y) {
        short z = (short) y;
        return x + (z << 2);
    }

    public int addC2I(int x, int y) {
        char z = (char) y;
        return x + z;
    }

    public int addC2IShift(int x, int y) {
        char z = (char) y;
        return x + (z << 1);
    }

    @Test
    public void mergeSignExtendIntoAdd() {
        predicateExist(new String[]{"addB2S", "addB2I", "addS2I", "addC2I"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addB2L", "addC2L", "addI2L", "addS2L"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    @Test
    public void mergeSignExtendShiftIntoAdd() {
        predicateExist(new String[]{"addB2SShift", "addB2IShift", "addS2IShift", "addC2IShift"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addB2LShift", "addC2LShift", "addI2LShift", "addS2LShift"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public long subI2L(long x, long y) {
        int z = (int) y;
        return x - z;
    }

    public long subB2L(long x, long y) {
        byte z = (byte) y;
        return x - z;
    }

    public long subC2L(long x, long y) {
        char z = (char) y;
        return x - z;
    }

    public long subS2L(long x, long y) {
        short z = (short) y;
        return x - z;
    }

    public int subB2S(int x, int y) {
        short a = (short) x;
        byte b = (byte) y;
        return a - b;
    }

    public int subB2SShift(int x, int y) {
        short a = (short) x;
        byte b = (byte) y;
        return a - (b << 2);
    }

    public int subB2I(int x, int y) {
        byte z = (byte) y;
        return x - z;
    }

    public int subB2IShift(int x, int y) {
        byte z = (byte) y;
        return x - (z << 3);
    }

    public int subS2I(int x, int y) {
        short z = (short) y;
        return x - z;
    }

    public int subS2IShift(int x, int y) {
        short z = (short) y;
        return x - (z << 2);
    }

    public int subC2I(int x, int y) {
        char z = (char) y;
        return x - z;
    }

    public int subC2IShift(int x, int y) {
        char z = (char) y;
        return x - (z << 1);
    }

    @Test
    public void mergeSignExtendShiftIntoSub() {
        predicateExist(new String[]{"subB2SShift", "subB2IShift", "subS2IShift", "subC2IShift"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subB2LShift", "subC2LShift", "subI2LShift", "subS2LShift"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    @Test
    public void mergeSignExtendIntoSub() {
        predicateExist(new String[]{"subB2S", "subB2I", "subS2I", "subC2I"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subB2L", "subC2L", "subI2L", "subS2L"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }
}
