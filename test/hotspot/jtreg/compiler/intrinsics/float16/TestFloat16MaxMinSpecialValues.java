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

/**
 * @test
 * @bug 8352585
 * @library /test/lib /
 * @summary Add special case handling for Float16.max/min x86 backend
 * @requires (os.simpleArch == "x64" & vm.cpu.features ~= ".*avx512_fp16.*" & vm.cpu.features ~= ".*avx512bw.*" & vm.cpu.features ~= ".*avx512vl.*")
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.intrinsics.float16.TestFloat16MaxMinSpecialValues
 */


public class TestFloat16MaxMinSpecialValues {
    public static Float16 POS_ZERO = Float16.valueOf(0.0f);
    public static Float16 NEG_ZERO = Float16.valueOf(-0.0f);
    public static Float16 SRC = Float16.valueOf(Float.MAX_VALUE);

    public Float16 RES;

    public static void main(String [] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 "})
    public Float16 testMaxNaNOperands(Float16 src1, Float16 src2) {
        return Float16.max(src1, src2);
    }

    @Run(test = "testMaxNaNOperands")
    public void launchMaxNaNOperands() {
        RES = testMaxNaNOperands(SRC, Float16.NaN);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = " + SRC.floatValue() + " input2 = NaN , expected = NaN, actual = " + RES.floatValue());
        }
        RES = testMaxNaNOperands(Float16.NaN, SRC);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = NaN, input2 = " + SRC.floatValue() + ", expected = NaN, actual = " + RES.floatValue());
        }
        RES = testMaxNaNOperands(Float16.NaN, Float16.NaN);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = NaN, input2 = NaN, expected = NaN, actual = " + RES.floatValue());
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 "})
    public Float16 testMinNaNOperands(Float16 src1, Float16 src2) {
        return Float16.min(src1, src2);
    }

    @Run(test = "testMinNaNOperands")
    public void launchMinNaNOperands() {
        RES = testMinNaNOperands(SRC, Float16.NaN);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = " + SRC.floatValue() + " input2 = NaN, expected = NaN, actual = " + RES.floatValue());
        }
        RES = testMinNaNOperands(Float16.NaN, SRC);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = NaN, input2 = " + SRC.floatValue() + ", expected = NaN, actual = " + RES.floatValue());
        }
        RES = testMinNaNOperands(Float16.NaN, Float16.NaN);
        if (!RES.equals(Float16.NaN)) {
            throw new AssertionError("input1 = NaN, input2 = NaN, expected = NaN, actual = " + RES.floatValue());
        }
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 "})
    public Float16 testMaxZeroOperands(Float16 src1, Float16 src2) {
        return Float16.max(src1, src2);
    }

    @Run(test = "testMaxZeroOperands")
    public void launchMaxZeroOperands() {
        RES = testMaxZeroOperands(POS_ZERO, NEG_ZERO);
        if (!RES.equals(POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = -0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(NEG_ZERO, POS_ZERO);
        if (!RES.equals(POS_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(POS_ZERO, POS_ZERO);
        if (!RES.equals(POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }
        RES = testMaxZeroOperands(NEG_ZERO, NEG_ZERO);
        if (!RES.equals(NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 "})
    public Float16 testMinZeroOperands(Float16 src1, Float16 src2) {
        return Float16.min(src1, src2);
    }

    @Run(test = "testMinZeroOperands")
    public void launchMinZeroOperands() {
        RES = testMinZeroOperands(POS_ZERO, NEG_ZERO);
        if (!RES.equals(NEG_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(NEG_ZERO, POS_ZERO);
        if (!RES.equals(NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = +0.0, expected = -0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(POS_ZERO, POS_ZERO);
        if (!RES.equals(POS_ZERO)) {
            throw new AssertionError("input1 = +0.0, input2 = +0.0, expected = +0.0, actual = " + RES.floatValue());
        }

        RES = testMinZeroOperands(NEG_ZERO, NEG_ZERO);
        if (!RES.equals(NEG_ZERO)) {
            throw new AssertionError("input1 = -0.0, input2 = -0.0, expected = -0.0, actual = " + RES.floatValue());
        }
    }
}
