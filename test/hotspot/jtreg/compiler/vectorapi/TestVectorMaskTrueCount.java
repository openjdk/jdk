/*
 * Copyright (c) 2023, Arm Limited. All rights reserved.
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
import jdk.incubator.vector.*;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8307795
 * @key randomness
 * @library /test/lib /
 * @requires os.arch=="aarch64"
 * @summary AArch64: Optimize VectorMask.truecount() on Neon
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestVectorMaskTrueCount
 */

public class TestVectorMaskTrueCount {
    private static final VectorSpecies<Float> SPECIES_F = FloatVector.SPECIES_64;
    private static final VectorSpecies<Double> SPECIES_D = DoubleVector.SPECIES_128;
    private static final VectorSpecies<Integer> SPECIES_I = IntVector.SPECIES_128;
    private static final int LENGTH = 1024;
    private static final Random RD = new Random();
    private static boolean[] ba;
    private static boolean[] bb;

    static {
        ba = new boolean[LENGTH];
        bb = new boolean[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            ba[i] = RD.nextBoolean();
            bb[i] = RD.nextBoolean();
        }
    }

    static int maskAndTrueCount(boolean[] a, boolean[] b, int idx, int count) {
        int trueCount = 0;
        boolean[] c = new boolean[count];

        for (int i = idx; i < idx + count; i++) {
            c[i - idx] = a[i] & b[i];
        }

        for (int i = 0; i < c.length; i++) {
            trueCount += c[i] ? 1 : 0;
        }

        return trueCount;
    }

    static void assertArrayEqualsFloat(int[] r, boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i += SPECIES_F.length()) {
            Asserts.assertEquals(r[i], maskAndTrueCount(a, b, i, SPECIES_F.length()));
        }
    }

    static void assertArrayEqualsDouble(int[] r, boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i += SPECIES_D.length()) {
            Asserts.assertEquals(r[i], maskAndTrueCount(a, b, i, SPECIES_D.length()));
        }
    }

    static void assertArrayEqualsInteger(int[] r, boolean[] a, boolean[] b) {
        for (int i = 0; i < a.length; i += SPECIES_I.length()) {
            Asserts.assertEquals(r[i], maskAndTrueCount(a, b, i, SPECIES_I.length()));
        }
    }

    @Test
    @IR(counts = { IRNode.VSTOREMASK_TRUECOUNT, ">= 1" })
    public static void testFloat() {
        int[] r = new int[LENGTH];
        for (int i = 0; i < LENGTH; i += SPECIES_F.length()) {
            VectorMask<Float> ma = VectorMask.fromArray(SPECIES_F, ba, i);
            VectorMask<Float> mb = VectorMask.fromArray(SPECIES_F, bb, i);
            r[i] = ma.and(mb).trueCount();
        }

        assertArrayEqualsFloat(r, ba, bb);
    }

    @Test
    @IR(counts = { IRNode.VSTOREMASK_TRUECOUNT, ">= 1" })
    public static void testDouble() {
        int[] r = new int[LENGTH];
        for (int i = 0; i < LENGTH; i += SPECIES_D.length()) {
            VectorMask<Double> ma = VectorMask.fromArray(SPECIES_D, ba, i);
            VectorMask<Double> mb = VectorMask.fromArray(SPECIES_D, bb, i);
            r[i] = ma.and(mb).trueCount();
        }

        assertArrayEqualsDouble(r, ba, bb);
    }

    @Test
    @IR(counts = { IRNode.VSTOREMASK_TRUECOUNT, ">= 1" })
    public static void testInt() {
        int[] r = new int[LENGTH];
        for (int i = 0; i < LENGTH; i += SPECIES_I.length()) {
            VectorMask<Integer> ma = VectorMask.fromArray(SPECIES_I, ba, i);
            VectorMask<Integer> mb = VectorMask.fromArray(SPECIES_I, bb, i);
            r[i] = ma.and(mb).trueCount();
        }

        assertArrayEqualsInteger(r, ba, bb);
    }

    public static void main(String[] args) {
        TestFramework testFramework = new TestFramework();
        testFramework.setDefaultWarmup(10000)
                     .addFlags("--add-modules=jdk.incubator.vector")
                     .addFlags("-XX:UseSVE=0")
                     .start();
    }
}