/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package compiler.intrinsics.float16;

import compiler.lib.ir_framework.*;
import jdk.incubator.vector.*;
import java.util.Random;
import jdk.test.lib.*;

/**
 * @test
 * @bug 8352585
 * @library /test/lib /
 * @summary Add special case handling for Float16.max/min x86 backend
 * @modules jdk.incubator.vector
 * @run driver compiler.intrinsics.float16.TestFloat16MaxMinSpecialValues
 */


public class TestFloat16MaxMinSpecialValues {
    public static Float16 POS_ZERO = Float16.valueOf(0.0f);
    public static Float16 NEG_ZERO = Float16.valueOf(-0.0f);
    public static Float16 SRC = Float16.valueOf(Float.MAX_VALUE);
    public static Random rd = Utils.getRandomInstance();

    public static Float16 genNaN() {
        // IEEE 754 Half Precision QNaN Format
        // S EEEEE MMMMMMMMMM
        // X 11111 1XXXXXXXXX
        short sign = (short)(rd.nextBoolean() ? 1 << 15 : 0);
        short significand = (short)rd.nextInt(512);
        return Float16.shortBitsToFloat16((short)(sign | 0x7E00 | significand));
    }

    public static boolean assertionCheck(Float16 actual, Float16 expected) {
        return !actual.equals(expected);
    }

    public Float16 RES;

    public static void main(String [] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 "}, applyIfCPUFeatureAnd = {"avx512_fp16", "true", "avx512bw", "true", "avx512vl", "true"})
    public Float16 testMaxNaNOperands(Float16 src1, Float16 src2) {
        return Float16.max(src1, src2);
    }

    @Run(test = "testMaxNaNOperands")
    public void launchMaxNaNOperands() {
        Float16 NAN = null;
        for (int i = 0; i < 100; i++) {
            NAN = genNaN();
            RES = testMaxNaNOperands(SRC, NAN);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = " + SRC.floatValue() + " input2 = NaN , expected = NaN, actual = " + RES.floatValue());
            }
            NAN = genNaN();
            RES = testMaxNaNOperands(NAN, SRC);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = NaN, input2 = " + SRC.floatValue() + ", expected = NaN, actual = " + RES.floatValue());
            }
            NAN = genNaN();
            RES = testMaxNaNOperands(NAN, NAN);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = NaN, input2 = NaN, expected = NaN, actual = " + RES.floatValue());
            }
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 "}, applyIfCPUFeatureAnd = {"avx512_fp16", "true", "avx512bw", "true", "avx512vl", "true"})
    public Float16 testMinNaNOperands(Float16 src1, Float16 src2) {
        return Float16.min(src1, src2);
    }

    @Run(test = "testMinNaNOperands")
    public void launchMinNaNOperands() {
        Float16 NAN = null;
        for (int i = 0; i < 100; i++) {
            NAN = genNaN();
            RES = testMinNaNOperands(SRC, NAN);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = " + SRC.floatValue() + " input2 = NaN, expected = NaN, actual = " + RES.floatValue());
            }
            NAN = genNaN();
            RES = testMinNaNOperands(NAN, SRC);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = NaN, input2 = " + SRC.floatValue() + ", expected = NaN, actual = " + RES.floatValue());
            }
            NAN = genNaN();
            RES = testMinNaNOperands(NAN, NAN);
            if (assertionCheck(RES, NAN)) {
                throw new AssertionError("input1 = NaN, input2 = NaN, expected = NaN, actual = " + RES.floatValue());
            }
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 "}, applyIfCPUFeatureAnd = {"avx512_fp16", "true", "avx512bw", "true", "avx512vl", "true"})
    public Float16 testMaxZeroOperands(Float16 src1, Float16 src2) {
        return Float16.max(src1, src2);
    }

    @Run(test = "testMaxZeroOperands")
    public void launchMaxZeroOperands() {
        RES = testMaxZeroOperands(POS_ZERO, NEG_ZERO);
        if (assertionCheck(RES, POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = -0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(NEG_ZERO, POS_ZERO);
        if (assertionCheck(RES, POS_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(POS_ZERO, POS_ZERO);
        if (assertionCheck(RES, POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(NEG_ZERO, NEG_ZERO);
        if (assertionCheck(RES, NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 "}, applyIfCPUFeatureAnd = {"avx512_fp16", "true", "avx512bw", "true", "avx512vl", "true"})
    public Float16 testMinZeroOperands(Float16 src1, Float16 src2) {
        return Float16.min(src1, src2);
    }

    @Run(test = "testMinZeroOperands")
    public void launchMinZeroOperands() {
        RES = testMinZeroOperands(POS_ZERO, NEG_ZERO);
        if (assertionCheck(RES, NEG_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(NEG_ZERO, POS_ZERO);
        if (assertionCheck(RES, NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = +0.0, expected = -0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(POS_ZERO, POS_ZERO);
        if (assertionCheck(RES, POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(NEG_ZERO, NEG_ZERO);
        if (assertionCheck(RES, NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }
    }
}
