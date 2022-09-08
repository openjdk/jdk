/*
 * Copyright (c) 2022, Arm Limited. All rights reserved.
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

import java.util.Random;

import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8292587
 * @key randomness
 * @library /test/lib /
 * @requires os.arch=="aarch64"
 * @summary AArch64: Support SVE fabd instruction
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.VectorAbsDiffTest
 */

public class VectorAbsDiffTest {
    private static final VectorSpecies<Double> D_SPECIES = DoubleVector.SPECIES_MAX;
    private static final VectorSpecies<Float> F_SPECIES = FloatVector.SPECIES_MAX;

    private static int LENGTH = 1024;
    private static final Random RD = Utils.getRandomInstance();

    private static float[] fa;
    private static float[] fb;
    private static float[] fr;
    private static double[] da;
    private static double[] db;
    private static double[] dr;
    private static boolean[] m;

    static {
        fa = new float[LENGTH];
        fb = new float[LENGTH];
        fr = new float[LENGTH];
        da = new double[LENGTH];
        db = new double[LENGTH];
        dr = new double[LENGTH];
        m = new boolean[LENGTH];

        for (int i = 0; i < LENGTH; i++) {
            fa[i] = RD.nextFloat((float) 25.0);
            fb[i] = RD.nextFloat((float) 25.0);
            da[i] = RD.nextDouble(25.0);
            db[i] = RD.nextDouble(25.0);
            m[i] = RD.nextBoolean();
        }
    }

    @Test
    @IR(counts = {"vfabd", "> 0"})
    public static void testFloatAbsDiff() {
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            av.sub(bv).lanewise(VectorOperators.ABS).intoArray(fr, i);
        }
    }

    @Run(test = "testFloatAbsDiff")
    public static void testFloatAbsDiff_runner() {
        testFloatAbsDiff();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Math.abs(fa[i] - fb[i]), fr[i]);
        }
    }

    @Test
    @IR(counts = {"vfabd_masked", "> 0"}, applyIf = {"UseSVE", "> 0"})
    public static void testFloatAbsDiffMasked() {
        for (int i = 0; i < LENGTH; i += F_SPECIES.length()) {
            FloatVector av = FloatVector.fromArray(F_SPECIES, fa, i);
            FloatVector bv = FloatVector.fromArray(F_SPECIES, fb, i);
            VectorMask<Float> mask = VectorMask.fromArray(F_SPECIES, m, i);
            av.lanewise(VectorOperators.SUB, bv, mask).lanewise(VectorOperators.ABS, mask).intoArray(fr, i);
        }
    }

    @Run(test = "testFloatAbsDiffMasked")
    public static void testFloatAbsDiffMasked_runner() {
        testFloatAbsDiffMasked();
        for (int i = 0; i < LENGTH; i++) {
            if (m[i]) {
                Asserts.assertEquals(Math.abs(fa[i] - fb[i]), fr[i]);
            } else {
                Asserts.assertEquals(fa[i], fr[i]);
            }
        }
    }

    @Test
    @IR(counts = {"vfabd", "> 0"})
    public static void testDoubleAbsDiff() {
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            av.sub(bv).lanewise(VectorOperators.ABS).intoArray(dr, i);
        }
    }

    @Run(test = "testDoubleAbsDiff")
    public static void testDoubleAbsDiff_runner() {
        testDoubleAbsDiff();
        for (int i = 0; i < LENGTH; i++) {
            Asserts.assertEquals(Math.abs(da[i] - db[i]), dr[i]);
        }
    }

    @Test
    @IR(counts = {"vfabd_masked", "> 0"}, applyIf = {"UseSVE", "> 0"})
    public static void testDoubleAbsDiffMasked() {
        for (int i = 0; i < LENGTH; i += D_SPECIES.length()) {
            DoubleVector av = DoubleVector.fromArray(D_SPECIES, da, i);
            DoubleVector bv = DoubleVector.fromArray(D_SPECIES, db, i);
            VectorMask<Double> mask = VectorMask.fromArray(D_SPECIES, m, i);
            av.lanewise(VectorOperators.SUB, bv, mask).lanewise(VectorOperators.ABS, mask).intoArray(dr, i);
        }
    }

    @Run(test = "testDoubleAbsDiffMasked")
    public static void testDoubleAbsDiffMasked_runner() {
        testDoubleAbsDiffMasked();
        for (int i = 0; i < LENGTH; i++) {
            if (m[i]) {
                Asserts.assertEquals(Math.abs(da[i] - db[i]), dr[i]);
            } else {
                Asserts.assertEquals(da[i], dr[i]);
            }
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
