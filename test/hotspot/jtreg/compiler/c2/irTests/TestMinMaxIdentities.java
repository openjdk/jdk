/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8341781
 * @summary Test identities of MinNodes and MaxNodes.
 * @key randomness
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestMinMaxIdentities
 */

public class TestMinMaxIdentities {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = { "intMinMin", "intMinMax", "intMaxMin", "intMaxMax",
                  "longMinMin", "longMinMax", "longMaxMin", "longMaxMax",
                  "floatMinMin", "floatMaxMax", "doubleMinMin", "doubleMaxMax",
                  "floatMinMax", "floatMaxMin", "doubleMinMax", "doubleMaxMin" })
    public void runMethod() {
        assertResult(10, 20, 10L, 20L, 10.f, 20.f, 10.0, 20.0);
        assertResult(20, 10, 20L, 10L, 20.f, 10.f, 20.0, 10.0);

        assertResult(RANDOM.nextInt(), RANDOM.nextInt(), RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextFloat(), RANDOM.nextFloat(), RANDOM.nextDouble(), RANDOM.nextDouble());
        assertResult(RANDOM.nextInt(), RANDOM.nextInt(), RANDOM.nextLong(), RANDOM.nextLong(), RANDOM.nextFloat(), RANDOM.nextFloat(), RANDOM.nextDouble(), RANDOM.nextDouble());

        assertResult(Integer.MAX_VALUE, Integer.MIN_VALUE, Long.MAX_VALUE, Long.MIN_VALUE, Float.POSITIVE_INFINITY, Float.NaN, Double.POSITIVE_INFINITY, Double.NaN);
        assertResult(Integer.MIN_VALUE, Integer.MAX_VALUE, Long.MIN_VALUE, Long.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Double.NaN, Double.POSITIVE_INFINITY);
    }

    @DontCompile
    public void assertResult(int iA, int iB, long lA, long lB, float fA, float fB, double dA, double dB) {
        Asserts.assertEQ(Math.min(iA, Math.min(iA, iB)), intMinMin(iA, iB));
        Asserts.assertEQ(Math.min(iA, Math.max(iA, iB)), intMinMax(iA, iB));
        Asserts.assertEQ(Math.max(iA, Math.min(iA, iB)), intMaxMin(iA, iB));
        Asserts.assertEQ(Math.max(iA, Math.max(iA, iB)), intMaxMax(iA, iB));

        Asserts.assertEQ(Math.min(lA, Math.min(lA, lB)), longMinMin(lA, lB));
        Asserts.assertEQ(Math.min(lA, Math.max(lA, lB)), longMinMax(lA, lB));
        Asserts.assertEQ(Math.max(lA, Math.min(lA, lB)), longMaxMin(lA, lB));
        Asserts.assertEQ(Math.max(lA, Math.max(lA, lB)), longMaxMax(lA, lB));

        Asserts.assertEQ(Math.min(fA, Math.min(fA, fB)), floatMinMin(fA, fB));
        Asserts.assertEQ(Math.max(fA, Math.max(fA, fB)), floatMaxMax(fA, fB));

        Asserts.assertEQ(Math.min(dA, Math.min(dA, dB)), doubleMinMin(dA, dB));
        Asserts.assertEQ(Math.max(dA, Math.max(dA, dB)), doubleMaxMax(dA, dB));

        // Due to NaN, these identities cannot be simplified.

        Asserts.assertEQ(Math.min(fA, Math.max(fA, fB)), floatMinMax(fA, fB));
        Asserts.assertEQ(Math.max(fA, Math.min(fA, fB)), floatMaxMin(fA, fB));
        Asserts.assertEQ(Math.min(dA, Math.max(dA, dB)), doubleMinMax(dA, dB));
        Asserts.assertEQ(Math.max(dA, Math.min(dA, dB)), doubleMaxMin(dA, dB));
    }

    // Integers

    @Test
    @IR(counts = { IRNode.MIN_I, "1" })
    public int intMinMin(int a, int b) {
        return Math.min(a, Math.min(a, b));
    }

    @Test
    @IR(failOn = { IRNode.MIN_I, IRNode.MAX_I })
    public int intMinMax(int a, int b) {
        return Math.min(a, Math.max(a, b));
    }

    @Test
    @IR(failOn = { IRNode.MIN_I, IRNode.MAX_I })
    public int intMaxMin(int a, int b) {
        return Math.max(a, Math.min(a, b));
    }

    @Test
    @IR(counts = { IRNode.MAX_I, "1" })
    public int intMaxMax(int a, int b) {
        return Math.max(a, Math.max(a, b));
    }

    // Longs

    // As Math.min/max(LL) is not intrinsified in the backend, it first needs to be transformed into CMoveL and then MinL/MaxL before
    // the identity can be matched. However, the outer min/max is not transformed into CMove because of the CMove cost model.
    // JDK-8307513 adds intrinsics for the methods such that MinL/MaxL replace the ternary operations,
    // and this enables identities to be matched.
    // Note that before JDK-8307513 MinL/MaxL nodes were already present before macro expansion.

    @Test
    @IR(applyIfPlatform = { "riscv64", "false" }, phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, counts = { IRNode.MIN_L, "1" })
    public long longMinMin(long a, long b) {
        return Math.min(a, Math.min(a, b));
    }

    @Test
    @IR(failOn = { IRNode.MIN_L, IRNode.MAX_L })
    public long longMinMax(long a, long b) {
        return Math.min(a, Math.max(a, b));
    }

    @Test
    @IR(failOn = { IRNode.MIN_L, IRNode.MAX_L })
    public long longMaxMin(long a, long b) {
        return Math.max(a, Math.min(a, b));
    }

    @Test
    @IR(applyIfPlatform = { "riscv64", "false" }, phase = { CompilePhase.BEFORE_MACRO_EXPANSION }, counts = { IRNode.MAX_L, "1" })
    public long longMaxMax(long a, long b) {
        return Math.max(a, Math.max(a, b));
    }

    // Floats

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_F, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_F, "1" })
    public float floatMinMin(float a, float b) {
        return Math.min(a, Math.min(a, b));
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MAX_F, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MAX_F, "1" })
    public float floatMaxMax(float a, float b) {
        return Math.max(a, Math.max(a, b));
    }

    // Doubles

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_D, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_D, "1" })
    public double doubleMinMin(double a, double b) {
        return Math.min(a, Math.min(a, b));
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MAX_D, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MAX_D, "1" })
    public double doubleMaxMax(double a, double b) {
        return Math.max(a, Math.max(a, b));
    }

    // Float and double identities that cannot be simplified due to NaN

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_F, "1", IRNode.MAX_F, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_F, "1", IRNode.MAX_F, "1" })
    public float floatMinMax(float a, float b) {
        return Math.min(a, Math.max(a, b));
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_F, "1", IRNode.MAX_F, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_F, "1", IRNode.MAX_F, "1" })
    public float floatMaxMin(float a, float b) {
        return Math.max(a, Math.min(a, b));
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_D, "1", IRNode.MAX_D, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_D, "1", IRNode.MAX_D, "1" })
    public double doubleMinMax(double a, double b) {
        return Math.min(a, Math.max(a, b));
    }

    @Test
    @IR(applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"}, counts = { IRNode.MIN_D, "1", IRNode.MAX_D, "1" })
    @IR(applyIfPlatform = { "riscv64", "true" }, counts = { IRNode.MIN_D, "1", IRNode.MAX_D, "1" })
    public double doubleMaxMin(double a, double b) {
        return Math.max(a, Math.min(a, b));
    }
}
