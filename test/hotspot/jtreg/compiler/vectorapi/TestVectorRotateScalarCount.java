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

package compiler.vectorapi;

import compiler.lib.ir_framework.*;
import compiler.lib.verify.Verify;
import jdk.incubator.vector.*;

/*
 * @test
 * @bug 8351635
 * @summary Test missing pattern in vector rotate generation
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver compiler.vectorapi.TestVectorRotateScalarCount
 */
public class TestVectorRotateScalarCount {
    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector", "-XX:+IgnoreUnrecognizedVMOptions", "-XX:UseAVX=2");
    }

    public static long long_shift = 31L;
    public static int int_shift = 12;

    static final Object GOLD_PATTERN1a = pattern1a();
    static final Object GOLD_PATTERN1b = pattern1b();
    static final Object GOLD_PATTERN2 = pattern2();
    static final Object GOLD_PATTERN3 = pattern3();
    static final Object GOLD_PATTERN4 = pattern4();

    @Test
    @IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    public static Object pattern1a() {
        LongVector lv1 = LongVector.broadcast(LongVector.SPECIES_128, 1);
        long x = Long.divideUnsigned(long_shift, Long.MIN_VALUE);
        return lv1.lanewise(VectorOperators.ROL, x);
    }

    @Check(test = "pattern1a")
    public static void check_pattern1a(Object param) {
        Verify.checkEQ(GOLD_PATTERN1a, param);
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    public static Object pattern1b() {
        LongVector lv1 = LongVector.broadcast(LongVector.SPECIES_128, 1);
        long x = Long.min(32, Long.max(Long.reverse(long_shift), 0));
        return lv1.lanewise(VectorOperators.ROR, x);
    }

    @Check(test = "pattern1b")
    public static void check_pattern1b(Object param) {
        Verify.checkEQ(GOLD_PATTERN1b, param);
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    public static Object pattern2() {
        LongVector lv1 = LongVector.broadcast(LongVector.SPECIES_128, 1);
        return lv1.lanewise(VectorOperators.ROL, int_shift);
    }

    @Check(test = "pattern2")
    public static void check_pattern2(Object param) {
       Verify.checkEQ(GOLD_PATTERN2, param);
    }

    @Test
    @IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    public static Object pattern3() {
        LongVector lv1 = LongVector.broadcast(LongVector.SPECIES_128, 1);
        return lv1.lanewise(VectorOperators.ROL, lv1);
    }

    @Check(test = "pattern3")
    public static void check_pattern3(Object param) {
       Verify.checkEQ(GOLD_PATTERN3, param);
    }
    @Test
    @IR(counts = {IRNode.URSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.LSHIFT_VL, IRNode.VECTOR_SIZE_2, "1",
                  IRNode.OR_VL, IRNode.VECTOR_SIZE_2, "1"},
                  applyIfCPUFeatureAnd = {"avx2", "true", "avx512f", "false", "avx512vl", "false"})
    public static Object pattern4() {
        LongVector lv1 = LongVector.broadcast(LongVector.SPECIES_128, 1);
        return lv1.lanewise(VectorOperators.ROL, 15L);
    }

    @Check(test = "pattern4")
    public static void check_pattern4(Object param) {
       Verify.checkEQ(GOLD_PATTERN4, param);
    }
}
