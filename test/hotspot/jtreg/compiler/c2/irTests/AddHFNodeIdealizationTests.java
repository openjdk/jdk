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
import compiler.lib.verify.Verify;
import jdk.incubator.vector.Float16;
import static jdk.incubator.vector.Float16.valueOf;

/*
 * @test
 * @bug 8388873
 * @summary Test that AddHFNode is not reassociated.
 * @modules jdk.incubator.vector
 * @library /test/lib /
 * @run driver ${test.main.class}
 */
public class AddHFNodeIdealizationTests {

    private static final Float16 SMALL = valueOf(3.0e-4f);
    private static final Float16 HALF  = valueOf(0.5f);
    private static final Float16 ONE   = valueOf(1.0f);

    private Float16 a1    = valueOf(1.0f);
    private Float16 a1024 = valueOf(1024.0f);
    private Float16 a2048 = valueOf(2048.0f);
    private Float16 b     = valueOf(1.0f);
    private Float16 bHalf = valueOf(0.5f);

    private Float16 dst1;
    private Float16 dst2;
    private Float16 dst3;
    private Float16 dst4;
    private Float16 dst5;

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }

    // Same semantics as Float16.add, but force-inlined so the ConvF2HF idealization
    // can pattern-match ConvF2HF(AddF(ConvHF2F(x), ConvHF2F(y))) into AddHF.
    @ForceInline
    private static Float16 add(Float16 x, Float16 y) {
        return valueOf(x.floatValue() + y.floatValue());
    }

    @DontCompile
    private static Float16 addHF(Float16 x, Float16 y) {
        // Single, rounded Float16 addition: (x + y).
        return valueOf(x.floatValue() + y.floatValue());
    }

    @DontCompile
    private static Float16 goldLeft(Float16 x, Float16 y, Float16 z) {
        // ((x + y) + z)
        return addHF(addHF(x, y), z);
    }

    @DontCompile
    private static Float16 goldRight(Float16 x, Float16 y, Float16 z) {
        // (x + (y + z))
        return addHF(x, addHF(y, z));
    }

    @DontCompile
    private static Float16 goldChain(Float16 x, Float16 c1, Float16 c2, Float16 c3) {
        // (((x + c1) + c2) + c3)
        return addHF(addHF(addHF(x, c1), c2), c3);
    }

    // Pattern 1: "(x + c1) + c2" -> "x + (c1 + c2)" (combine constants).

    @Test
    @IR(counts = {IRNode.ADD_HF, "2"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, "2"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void test1() {
        dst1 = add(add(a1, SMALL), SMALL);
    }

    @Check(test = "test1")
    public void check1() {
        Verify.checkEQ(dst1, goldLeft(a1, SMALL, SMALL));
    }

    // Pattern 1 with a large operand, where the rounding difference is
    // large and easy to observe: (1024 + 0.5) + 0.5.

    @Test
    @IR(counts = {IRNode.ADD_HF, "2"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, "2"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void test2() {
        dst2 = add(add(a1024, HALF), HALF);
    }

    @Check(test = "test2")
    public void check2() {
        Verify.checkEQ(dst2, goldLeft(a1024, HALF, HALF));
    }

    // Pattern 1, longer chain: "((x + c1) + c2) + c3". Without the fix the
    // three constants collapse into one, leaving a single AddHF; with the
    // fix all three additions survive.

    @Test
    @IR(counts = {IRNode.ADD_HF, "3"},
        applyIfCPUFeatureOr = {"avx512_fp16", "true", "zfh", "true"})
    @IR(counts = {IRNode.ADD_HF, "3"},
        applyIfCPUFeatureAnd = {"fphp", "true", "asimdhp", "true"})
    public void test3() {
        dst3 = add(add(add(a2048, ONE), ONE), ONE);
    }

    @Check(test = "test3")
    public void check3() {
        Verify.checkEQ(dst3, goldChain(a2048, ONE, ONE, ONE));
    }

    // Pattern 2: "(x + c) + y" -> "(x + y) + c" (push constant down).
    // The node count stays at two, so only the numeric result exposes the bug.

    @Test
    public void test4() {
        dst4 = add(add(a1024, HALF), b);
    }

    @Check(test = "test4")
    public void check4() {
        Verify.checkEQ(dst4, goldLeft(a1024, HALF, b));
    }

    // Pattern 3: "x + (y + c)" -> "(x + y) + c" (push constant down).

    @Test
    public void test5() {
        dst5 = add(a1024, add(bHalf, HALF));
    }

    @Check(test = "test5")
    public void check5() {
        Verify.checkEQ(dst5, goldRight(a1024, bHalf, HALF));
    }
}
