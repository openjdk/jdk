/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
 * Copyright (c) 2025, Rivos Inc. All rights reserved.
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
import java.util.Random;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @bug 8308340
 * @key randomness
 * @summary Test fma match rule after C2 optimizer.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestIRFma
 */

public class TestIRFma {

    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"test1", "test2", "test3",
                 "test4", "test5", "test6",
                 "test7", "test8", "test9",
                 "test10", "test11", "test12",
                 "test13", "test14"})
    public void runMethod() {
        float fa = RANDOM.nextFloat();
        float fb = RANDOM.nextFloat();
        float fc = RANDOM.nextFloat();
        assertResult(fa, fb, fc);

        double da = RANDOM.nextDouble();
        double db = RANDOM.nextDouble();
        double dc = RANDOM.nextDouble();
        assertResult(da, db, dc);
    }

    @DontCompile
    public void assertResult(float a, float b, float c) {
        Asserts.assertEquals(Math.fma(-a, -b, c)  , test1(a, b, c));
        Asserts.assertEquals(Math.fma(-a, b, c)   , test3(a, b, c));
        Asserts.assertEquals(Math.fma(a, -b, c)   , test5(a, b, c));
        Asserts.assertEquals(Math.fma(-a, b, -c)  , test7(a, b, c));
        Asserts.assertEquals(Math.fma(a, -b, -c)  , test9(a, b, c));
        Asserts.assertEquals(Math.fma(a, b, -c)   , test11(a, b, c));
        Asserts.assertEquals(Math.fma(-a, -b, -c) , test13(a, b, c));
    }

    @DontCompile
    public void assertResult(double a, double b, double c) {
        Asserts.assertEquals(Math.fma(-a, -b, c)  , test2(a, b, c));
        Asserts.assertEquals(Math.fma(-a, b, c)   , test4(a, b, c));
        Asserts.assertEquals(Math.fma(a, -b, c)   , test6(a, b, c));
        Asserts.assertEquals(Math.fma(-a, b, -c)  , test8(a, b, c));
        Asserts.assertEquals(Math.fma(a, -b, -c)  , test10(a, b, c));
        Asserts.assertEquals(Math.fma(a, b, -c)   , test12(a, b, c));
        Asserts.assertEquals(Math.fma(-a, -b, -c) , test14(a, b, c));
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test1(float a, float b, float c) {
        return Math.fma(-a, -b, c);
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test2(double a, double b, double c) {
        return Math.fma(-a, -b, c);
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test3(float a, float b, float c) {
        return Math.fma(-a, b, c);
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test4(double a, double b, double c) {
        return Math.fma(-a, b, c);
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test5(float a, float b, float c) {
        return Math.fma(a, -b, c);
    }

    @Test
    @IR(counts = {IRNode.FMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test6(double a, double b, double c) {
        return Math.fma(a, -b, c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test7(float a, float b, float c) {
        return Math.fma(-a, b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test8(double a, double b, double c) {
        return Math.fma(-a, b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test9(float a, float b, float c) {
        return Math.fma(a, -b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test10(double a, double b, double c) {
        return Math.fma(a, -b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test11(float a, float b, float c) {
        return Math.fma(a, b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMSUB, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test12(double a, double b, double c) {
        return Math.fma(a, b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_F, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static float test13(float a, float b, float c) {
        return Math.fma(-a, -b, -c);
    }

    @Test
    @IR(counts = {IRNode.FNMADD, "> 0"},
        applyIfCPUFeature = {"asimd", "true"})
    @IR(counts = {IRNode.FMA_D, "= 1"},
        applyIfPlatform = {"riscv64", "true"}, applyIf = {"UseFMA", "true"})
    static double test14(double a, double b, double c) {
        return Math.fma(-a, -b, -c);
    }

 }
