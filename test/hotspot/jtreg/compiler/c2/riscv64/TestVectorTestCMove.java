/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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

/**
 * @test
 * @bug 8395341 8391910
 * @summary Conditional moves whose condition is a VectorTest are matched.
 *
 * A diamond controlled by VectorMask.anyTrue()/allTrue() is collapsed into a
 * CMove by PhaseIdealLoop::conditional_move(), which only consults the type of
 * the merged value and never looks at the comparison feeding the If. RISC-V has
 * no flags register, so every cmove rule has to spell the comparison out in its
 * own match rule, and a missing one makes the matcher give up with
 * "bad AD file" -- an assert in a debug build, a silent and endlessly retried
 * bailout in a product build. Each test therefore requests CompLevel.C2 so that
 * a bailout fails the test either way.
 *
 * @library /test/lib /
 * @requires os.arch == "riscv64" & vm.compiler2.enabled
 * @requires vm.cpu.features ~= ".*rvv.*"
 * @modules jdk.incubator.vector
 * @run driver ${test.main.class}
 */

package compiler.c2.riscv64;

import compiler.lib.ir_framework.CompLevel;
import compiler.lib.ir_framework.CompilePhase;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Run;
import compiler.lib.ir_framework.Scenario;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;

public class TestVectorTestCMove {
    private static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_128;

    // Mask bit patterns, interpreted over the 16 lanes of SPECIES.
    private static final long ALL  = -1L; // every lane set   -> anyTrue, allTrue
    private static final long SOME = 1L;  // only lane 0 set  -> anyTrue
    private static final long NONE = 0L;  // no lane set      -> neither

    private static final Object FIRST = new Object();
    private static final Object SECOND = new Object();

    private static final Object[] ARRAY_SOURCE = {FIRST, SECOND};
    private static final Object[] ARRAY_RESULT = new Object[1];

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector");
        framework.addScenarios(
            new Scenario(0, "-XX:+UseCompressedOops"),
            new Scenario(1, "-XX:-UseCompressedOops"));
        framework.start();
    }

    // ---------------- int: CMoveI ----------------

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"})
    static int anyTrueI(long bits, int a, int b) {
        return VectorMask.fromLong(SPECIES, bits).anyTrue() ? a : b;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"})
    static int allTrueI(long bits, int a, int b) {
        return VectorMask.fromLong(SPECIES, bits).allTrue() ? a : b;
    }

    // ---------------- long: CMoveL ----------------

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"})
    static long anyTrueL(long bits, long a, long b) {
        return VectorMask.fromLong(SPECIES, bits).anyTrue() ? a : b;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"})
    static long allTrueL(long bits, long a, long b) {
        return VectorMask.fromLong(SPECIES, bits).allTrue() ? a : b;
    }

    // ---------------- float: CMoveF ----------------

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"})
    static float anyTrueF(long bits, float a, float b) {
        return VectorMask.fromLong(SPECIES, bits).anyTrue() ? a : b;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"})
    static float allTrueF(long bits, float a, float b) {
        return VectorMask.fromLong(SPECIES, bits).allTrue() ? a : b;
    }

    // ---------------- double: CMoveD ----------------

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"})
    static double anyTrueD(long bits, double a, double b) {
        return VectorMask.fromLong(SPECIES, bits).anyTrue() ? a : b;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"})
    static double allTrueD(long bits, double a, double b) {
        return VectorMask.fromLong(SPECIES, bits).allTrue() ? a : b;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"UseCompressedOops", "false"})
    static Object anyTrueP(long bits, Object first, Object second) {
        return VectorMask.fromLong(SPECIES, bits).anyTrue() ? first : second;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"UseCompressedOops", "false"})
    static Object allTrueP(long bits, Object first, Object second) {
        return VectorMask.fromLong(SPECIES, bits).allTrue() ? first : second;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ANYTRUE, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"UseCompressedOops", "true"})
    static void anyTrueN(long bits) {
        Object first = ARRAY_SOURCE[0];
        Object second = ARRAY_SOURCE[1];
        ARRAY_RESULT[0] = VectorMask.fromLong(SPECIES, bits).anyTrue() ? first : second;
    }

    @Test(compLevel = CompLevel.C2)
    @IR(counts = {IRNode.RISCV_CMOVE_VTEST_ALLTRUE, ">0"},
        phase = CompilePhase.FINAL_CODE,
        applyIf = {"UseCompressedOops", "true"})
    static void allTrueN(long bits) {
        Object first = ARRAY_SOURCE[0];
        Object second = ARRAY_SOURCE[1];
        ARRAY_RESULT[0] = VectorMask.fromLong(SPECIES, bits).allTrue() ? first : second;
    }

    // ---------------- driver ----------------
    //
    // Every test sees all three mask patterns on each invocation. Keeping the
    // branch unpredictable matters: conditional_move() refuses to convert a
    // diamond whose If is almost always, or almost never, taken.

    @Run(test = "anyTrueI")
    public void runAnyTrueI() {
        Asserts.assertEQ(anyTrueI(ALL, 7, 9), 7);
        Asserts.assertEQ(anyTrueI(SOME, 7, 9), 7);
        Asserts.assertEQ(anyTrueI(NONE, 7, 9), 9);
    }

    @Run(test = "allTrueI")
    public void runAllTrueI() {
        Asserts.assertEQ(allTrueI(ALL, 7, 9), 7);
        Asserts.assertEQ(allTrueI(SOME, 7, 9), 9);
        Asserts.assertEQ(allTrueI(NONE, 7, 9), 9);
    }

    @Run(test = "anyTrueL")
    public void runAnyTrueL() {
        Asserts.assertEQ(anyTrueL(ALL, 7L, 9L), 7L);
        Asserts.assertEQ(anyTrueL(SOME, 7L, 9L), 7L);
        Asserts.assertEQ(anyTrueL(NONE, 7L, 9L), 9L);
    }

    @Run(test = "allTrueL")
    public void runAllTrueL() {
        Asserts.assertEQ(allTrueL(ALL, 7L, 9L), 7L);
        Asserts.assertEQ(allTrueL(SOME, 7L, 9L), 9L);
        Asserts.assertEQ(allTrueL(NONE, 7L, 9L), 9L);
    }

    @Run(test = "anyTrueF")
    public void runAnyTrueF() {
        Asserts.assertEQ(anyTrueF(ALL, 7f, 9f), 7f);
        Asserts.assertEQ(anyTrueF(SOME, 7f, 9f), 7f);
        Asserts.assertEQ(anyTrueF(NONE, 7f, 9f), 9f);
    }

    @Run(test = "allTrueF")
    public void runAllTrueF() {
        Asserts.assertEQ(allTrueF(ALL, 7f, 9f), 7f);
        Asserts.assertEQ(allTrueF(SOME, 7f, 9f), 9f);
        Asserts.assertEQ(allTrueF(NONE, 7f, 9f), 9f);
    }

    @Run(test = "anyTrueD")
    public void runAnyTrueD() {
        Asserts.assertEQ(anyTrueD(ALL, 7d, 9d), 7d);
        Asserts.assertEQ(anyTrueD(SOME, 7d, 9d), 7d);
        Asserts.assertEQ(anyTrueD(NONE, 7d, 9d), 9d);
    }

    @Run(test = "allTrueD")
    public void runAllTrueD() {
        Asserts.assertEQ(allTrueD(ALL, 7d, 9d), 7d);
        Asserts.assertEQ(allTrueD(SOME, 7d, 9d), 9d);
        Asserts.assertEQ(allTrueD(NONE, 7d, 9d), 9d);
    }

    @Run(test = "anyTrueP")
    public void runAnyTrueP() {
        Asserts.assertSame(anyTrueP(ALL, FIRST, SECOND), FIRST);
        Asserts.assertSame(anyTrueP(SOME, FIRST, SECOND), FIRST);
        Asserts.assertSame(anyTrueP(NONE, FIRST, SECOND), SECOND);
    }

    @Run(test = "allTrueP")
    public void runAllTrueP() {
        Asserts.assertSame(allTrueP(ALL, FIRST, SECOND), FIRST);
        Asserts.assertSame(allTrueP(SOME, FIRST, SECOND), SECOND);
        Asserts.assertSame(allTrueP(NONE, FIRST, SECOND), SECOND);
    }

    @Run(test = "anyTrueN")
    public void runAnyTrueN() {
        anyTrueN(ALL);
        Asserts.assertSame(ARRAY_RESULT[0], FIRST);
        anyTrueN(SOME);
        Asserts.assertSame(ARRAY_RESULT[0], FIRST);
        anyTrueN(NONE);
        Asserts.assertSame(ARRAY_RESULT[0], SECOND);
    }

    @Run(test = "allTrueN")
    public void runAllTrueN() {
        allTrueN(ALL);
        Asserts.assertSame(ARRAY_RESULT[0], FIRST);
        allTrueN(SOME);
        Asserts.assertSame(ARRAY_RESULT[0], SECOND);
        allTrueN(NONE);
        Asserts.assertSame(ARRAY_RESULT[0], SECOND);
    }
}
