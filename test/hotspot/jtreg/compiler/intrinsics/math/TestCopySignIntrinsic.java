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

/**
 * @test
 * @bug 8349138
 * @key randomness
 * @summary Optimize Math.copySign API for Intel e-core targets
 * @library /test/lib /
 * @run driver compiler.intrinsics.math.TestCopySignIntrinsic
*/

package compiler.intrinsics.math;

import compiler.lib.ir_framework.Check;
import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;
import compiler.lib.ir_framework.Setup;
import compiler.lib.verify.*;
import java.util.stream.IntStream;
import java.util.Random;
import jdk.test.lib.Utils;
import compiler.lib.generators.Generator;
import static compiler.lib.generators.Generators.G;

public class TestCopySignIntrinsic {
    private static final Random rd = Utils.getRandomInstance();

    public static void main(String[] args) {
        new TestFramework(TestCopySignIntrinsic.class).run();
    }

    public final int SIZE = 1024;
    public float [] fmagnitude;
    public float [] fsign;
    public float [] afresult;
    public float [] efresult;

    public double [] dmagnitude;
    public double [] dsign;
    public double [] adresult;
    public double [] edresult;

    public TestCopySignIntrinsic() {
        fmagnitude = new float[SIZE];
        fsign = new float[SIZE];

        dmagnitude = new double[SIZE];
        dsign = new double[SIZE];

        afresult = new float[SIZE];
        efresult = new float[SIZE];

        adresult = new double[SIZE];
        edresult = new double[SIZE];

        Generator<Float> genFloat = G.floats();
        Generator<Double> genDouble = G.doubles();
        for (int i = 0; i < SIZE; i++) {
            fmagnitude[i] = genFloat.next();
            dmagnitude[i] = genFloat.next();
            fsign[i]      = genFloat.next();
            dsign[i]      = genFloat.next();
        }

        for (int i = 0; i < SIZE; i++) {
            efresult[i] = Math.copySign(fmagnitude[i], fsign[i]);
            edresult[i] = Math.copySign(dmagnitude[i], dsign[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.COPYSIGN_F, " >0 ", IRNode.COPYSIGN_VF, " >0 "}, applyIfCPUFeature = { "avx", "true"})
    public void testCopySignF() {
        for (int i = 0; i < SIZE; i++) {
            afresult[i] = Math.copySign(fmagnitude[i], fsign[i]);
        }
    }

    @Check(test = "testCopySignF")
    public void checkCopySignF() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(afresult[i], efresult[i]);
        }
    }

    @Test
    @IR(counts = {IRNode.COPYSIGN_D, " >0 ", IRNode.COPYSIGN_VD, " >0 "}, applyIfCPUFeature = { "avx", "true"})
    public void testCopySignD() {
        for (int i = 0; i < SIZE; i++) {
            adresult[i] = Math.copySign(dmagnitude[i], dsign[i]);
        }
    }

    @Check(test = "testCopySignD")
    public void checkCopySignD() {
        for (int i = 0; i < SIZE; i++) {
            Verify.checkEQ(adresult[i], edresult[i]);
        }
    }
}
