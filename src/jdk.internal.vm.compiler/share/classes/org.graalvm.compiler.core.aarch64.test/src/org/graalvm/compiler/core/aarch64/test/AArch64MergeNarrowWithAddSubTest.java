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

public class AArch64MergeNarrowWithAddSubTest extends AArch64MatchRuleTest {

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

    public int addIB(int x, int y) {
        return x + (y & 0xff);
    }

    public int addIH(int x, int y) {
        return x + (y & 0xffff);
    }

    public long addLB(long x, long y) {
        return x + (y & 0xff);
    }

    public long addLH(long x, long y) {
        return x + (y & 0xffff);
    }

    public long addLW(long x, long y) {
        return x + (y & 0xffffffffL);
    }

    @Test
    public void mergeDowncastIntoAdd() {
        predicateExist(new String[]{"addIB", "addIH"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLB", "addLH", "addLW"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int subIB(int x, int y) {
        return x - (y & 0xff);
    }

    public int subIH(int x, int y) {
        return x - (y & 0xffff);
    }

    public long subLB(long x, long y) {
        return x - (y & 0xff);
    }

    public long subLH(long x, long y) {
        return x - (y & 0xffff);
    }

    public long subLW(long x, long y) {
        return x - (y & 0xffffffffL);
    }

    @Test
    public void mergeDowncastIntoSub() {
        predicateExist(new String[]{"subIB", "subIH"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLB", "subLH", "subLW"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    public int addIBShift(int x, int y) {
        return x + ((y & 0xff) << 3);
    }

    public int addIHShift(int x, int y) {
        return x + ((y & 0xffff) << 3);
    }

    public long addLBShift(long x, long y) {
        return x + ((y & 0xffL) << 3);
    }

    public long addLHShift(long x, long y) {
        return x + ((y & 0xffffL) << 3);
    }

    public long addLWShift(long x, long y) {
        return x + ((y & 0xffffffffL) << 3);
    }

    @Test
    public void mergeShiftDowncastIntoAdd() {
        predicateExist(new String[]{"addIBShift", "addIHShift"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLBShift", "addLHShift", "addLWShift"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int subIBShift(int x, int y) {
        return x - ((y & 0xff) << 3);
    }

    public int subIHShift(int x, int y) {
        return x - ((y & 0xffff) << 3);
    }

    public long subLBShift(long x, long y) {
        return x - ((y & 0xffL) << 3);
    }

    public long subLHShift(long x, long y) {
        return x - ((y & 0xffffL) << 3);
    }

    public long subLWShift(long x, long y) {
        return x - ((y & 0xffffffffL) << 3);
    }

    @Test
    public void mergeShiftDowncastIntoSub() {
        predicateExist(new String[]{"subIBShift", "subIHShift"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLBShift", "subLHShift", "subLWShift"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    public int addIntExtractShort(int x, int y) {
        return x + ((y << 16) >> 16);
    }

    public int addIntExtractByte(int x, int y) {
        return x + ((y << 24) >> 24);
    }

    public long addLongExtractInt(long x, long y) {
        return x + ((y << 32) >> 32);
    }

    public long addLongExtractShort(long x, long y) {
        return x + ((y << 48) >> 48);
    }

    public long addLongExtractByte(long x, long y) {
        return x + ((y << 56) >> 56);
    }

    @Test
    public void addSignExtractTest() {
        predicateExist(new String[]{"addIntExtractShort", "addIntExtractByte"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLongExtractInt", "addLongExtractShort", "addLongExtractByte"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int addIntExtractShortByShift(int x, int y) {
        return x + (((y << 16) >> 16) << 3);
    }

    public int addIntExtractByteByShift(int x, int y) {
        return x + (((y << 24) >> 24) << 3);
    }

    public long addLongExtractIntByShift(long x, long y) {
        return x + (((y << 32) >> 32) << 3);
    }

    public long addLongExtractShortByShift(long x, long y) {
        return x + (((y << 48) >> 48) << 3);
    }

    public long addLongExtractByteByShift(long x, long y) {
        return x + (((y << 56) >> 56) << 3);
    }

    @Test
    public void addExtractByShiftTest() {
        predicateExist(new String[]{"addIntExtractShortByShift", "addIntExtractByteByShift"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLongExtractIntByShift", "addLongExtractShortByShift", "addLongExtractByteByShift"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int addIntUnsignedExtractByte(int x, int y) {
        return x + ((y << 24) >>> 24);
    }

    public long addLongUnsignedExtractByte(long x, long y) {
        return x + ((y << 56) >>> 56);
    }

    @Test
    public void addUnsignedExtractTest() {
        predicateExist(new String[]{"addIntUnsignedExtractByte"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLongUnsignedExtractByte"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int addIntUnsignedExtractByteByShift(int x, int y) {
        return x + (((y << 24) >>> 24) << 2);
    }

    public long addLongUnsignedExtractByteByShift(long x, long y) {
        return x + (((y << 56) >>> 56) << 1);
    }

    @Test
    public void addUnsignedExtractByShiftTest() {
        predicateExist(new String[]{"addIntUnsignedExtractByteByShift"}, INT_VALUES, PRED_EXTEND_ADD_SHIFT);
        predicateExist(new String[]{"addLongUnsignedExtractByteByShift"}, LONG_VALUES, PRED_EXTEND_ADD_SHIFT);
    }

    public int subIntExtractShort(int x, int y) {
        return x - ((y << 16) >> 16);
    }

    public int subIntExtractByte(int x, int y) {
        return x - ((y << 24) >> 24);
    }

    public long subLongExtractInt(long x, long y) {
        return x - ((y << 32) >> 32);
    }

    public long subLongExtractShort(long x, long y) {
        return x - ((y << 48) >> 48);
    }

    public long subLongExtractByte(long x, long y) {
        return x - ((y << 56) >> 56);
    }

    @Test
    public void subExtractTest() {
        predicateExist(new String[]{"subIntExtractShort", "subIntExtractByte"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLongExtractInt", "subLongExtractShort", "subLongExtractByte"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    public int subIntExtractShortByShift(int x, int y) {
        return x - (((y << 16) >> 16) << 3);
    }

    public int subIntExtractByteByShift(int x, int y) {
        return x - (((y << 24) >> 24) << 3);
    }

    public long subLongExtractIntByShift(long x, long y) {
        return x - (((y << 32) >> 32) << 3);
    }

    public long subLongExtractShortByShift(long x, long y) {
        return x - (((y << 48) >> 48) << 3);
    }

    public long subLongExtractByteByShift(long x, long y) {
        return x - (((y << 56) >> 56) << 3);
    }

    @Test
    public void subExtractByShiftTest() {
        predicateExist(new String[]{"subIntExtractShortByShift", "subIntExtractByteByShift"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLongExtractIntByShift", "subLongExtractShortByShift", "subLongExtractByteByShift"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    public int subIntUnsignedExtractByte(int x, int y) {
        return x - ((y << 24) >>> 24);
    }

    public long subLongUnsignedExtractByte(long x, long y) {
        return x - ((y << 56) >>> 56);
    }

    @Test
    public void subUnsignedExtractTest() {
        predicateExist(new String[]{"subIntUnsignedExtractByte"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLongUnsignedExtractByte"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }

    public int subIntUnsignedExtractByteByShift(int x, int y) {
        return x - (((y << 24) >>> 24) << 1);
    }

    public long subLongUnsignedExtractByteByShift(long x, long y) {
        return x - (((y << 56) >>> 56) << 2);
    }

    @Test
    public void subUnsignedExtractByShiftTest() {
        predicateExist(new String[]{"subIntUnsignedExtractByteByShift"}, INT_VALUES, PRED_EXTEND_SUB_SHIFT);
        predicateExist(new String[]{"subLongUnsignedExtractByteByShift"}, LONG_VALUES, PRED_EXTEND_SUB_SHIFT);
    }
}
