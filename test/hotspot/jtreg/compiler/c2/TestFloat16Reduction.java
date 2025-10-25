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

package compiler.c2;

/*
 * @test
 * @bug 8370409
 * @summary Incorrect computation in Float16 reduction loop
 *
 * @library /test/lib /
 * @run main/othervm -XX:-TieredCompilation
 *                   compiler.c2.TestFloat16Reduction
 */

import compiler.lib.ir_framework.*;
import compiler.lib.verify.*;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;
import jdk.test.lib.Utils;
import java.util.Arrays;

public class TestFloat16Reduction {

    public short [] arr;
    private static Generator<Short> genHF = G.uniformFloat16s();

    public long GOLDEN_ADD;
    public long GOLDEN_SUB;
    public long GOLDEN_MUL;
    public long GOLDEN_DIV;
    public long GOLDEN_MAX;
    public long GOLDEN_MIN;

    @Test
    @IR(counts = {IRNode.ADD_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.ADD_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long ADDReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) + Float.float16ToFloat(arr[i]));
        }
        return (long)res;
    }

    @Check(test="ADDReduceLong")
    void checkADDReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_ADD);
    }

    @Test
    @IR(counts = {IRNode.SUB_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.SUB_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long SUBReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) - Float.float16ToFloat(arr[i]));
        }
        return (long)res;
    }

    @Check(test="SUBReduceLong")
    void checkSUBReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_SUB);
    }

    @Test
    @IR(counts = {IRNode.MUL_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MUL_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long MULReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) * Float.float16ToFloat(arr[i]));
        }
        return (long)res;
    }

    @Check(test="MULReduceLong")
    void checkMULReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_MUL);
    }

    @Test
    @IR(counts = {IRNode.DIV_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.DIV_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long DIVReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Float.float16ToFloat(res) / Float.float16ToFloat(arr[i]));
        }
        return (long)res;
    }

    @Check(test="DIVReduceLong")
    void checkDIVReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_DIV);
    }

    @Test
    @IR(counts = {IRNode.MAX_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MAX_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long MAXReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Math.max(Float.float16ToFloat(res), Float.float16ToFloat(arr[i])));
        }
        return (long)res;
    }

    @Check(test="MAXReduceLong")
    void checkMAXReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_MAX);
    }

    @Test
    @IR(counts = {IRNode.MIN_HF, " >0 "}, applyIfCPUFeature = {"avx512_fp16", "true"})
    @IR(counts = {IRNode.MIN_HF, " >0 "}, applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    long MINReduceLong() {
        short res = 0;
        for (int i = 0; i < arr.length; i++) {
            res = Float.floatToFloat16(Math.min(Float.float16ToFloat(res), Float.float16ToFloat(arr[i])));
        }
        return (long)res;
    }

    @Check(test = "MINReduceLong")
    void checkMINReduceLong(long actual) {
        Verify.checkEQ(actual, GOLDEN_MIN);
    }

    public TestFloat16Reduction() {
        arr = new short[32];
        G.fill(genHF, arr);

        GOLDEN_ADD = ADDReduceLong();
        GOLDEN_SUB = SUBReduceLong();
        GOLDEN_MUL = MULReduceLong();
        GOLDEN_DIV = DIVReduceLong();
        GOLDEN_MAX = MAXReduceLong();
        GOLDEN_MIN = MINReduceLong();
    }

    public static void main(String [] args) {
        TestFramework.run(TestFloat16Reduction.class);
    }
}
