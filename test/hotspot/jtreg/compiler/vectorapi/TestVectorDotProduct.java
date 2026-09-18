/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorSpecies;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/**
 * @test
 * @bug 8377386
 * @key randomness
 * @library /test/lib /
 * @summary Test Vector API dot and dotUnsigned
 * @modules jdk.incubator.vector
 *
 * @run driver compiler.vectorapi.TestVectorDotProduct
 */

public class TestVectorDotProduct {

    private static final VectorSpecies<Byte> B_SPECIES = ByteVector.SPECIES_MAX;
    private static final VectorSpecies<Integer> I_SPECIES = IntVector.SPECIES_MAX;

    private static final int BYTE_LENGTH = 1024;
    private static final int INT_LENGTH = BYTE_LENGTH / 4;
    private static final Random RD = Utils.getRandomInstance();

    private static byte[] a;
    private static byte[] b;
    private static int[] acc;
    private static int[] r;

    static {
        a = new byte[BYTE_LENGTH];
        b = new byte[BYTE_LENGTH];
        acc = new int[INT_LENGTH];
        r = new int[INT_LENGTH];

        for (int i = 0; i < BYTE_LENGTH; i++) {
            a[i] = (byte) RD.nextInt();
            b[i] = (byte) RD.nextInt();
        }

        for (int i = 0; i < INT_LENGTH; i++) {
            acc[i] = RD.nextInt();
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"asimddp", "true"},
        applyIf = {"MaxVectorSize", "<= 16"},
        counts = {IRNode.DOT_V, "> 0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        applyIf = {"MaxVectorSize", "> 16"},
        counts = {IRNode.DOT_V, "> 0"})
    public static void testDot() {
        IntVector cv = IntVector.fromArray(I_SPECIES, acc, 0);
        for (int i = 0; i < BYTE_LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, b, i);

            cv = av.dot(bv, cv);
        }
        cv.intoArray(r, 0);
    }

    @Run(test = "testDot")
    public static void testDot_runner() {
        testDot();

        for (int i = 0; i < I_SPECIES.length(); i++) {
            int res = acc[i];
            for (int j = 0; j < BYTE_LENGTH; j += B_SPECIES.length()) {
                int b_i = j + i * 4;
                res += a[b_i]     * b[b_i] +
                       a[b_i + 1] * b[b_i + 1] +
                       a[b_i + 2] * b[b_i + 2] +
                       a[b_i + 3] * b[b_i + 3];
            }
            Asserts.assertEquals(res, r[i]);
        }
    }

    @Test
    @IR(applyIfCPUFeature = {"asimddp", "true"},
        applyIf = {"MaxVectorSize", "<= 16"},
        counts = {IRNode.UDOT_V, "> 0"})
    @IR(applyIfCPUFeature = {"sve", "true"},
        applyIf = {"MaxVectorSize", "> 16"},
        counts = {IRNode.UDOT_V, "> 0"})
    public static void testDotUnsigned() {
        IntVector cv = IntVector.fromArray(I_SPECIES, acc, 0);
        for (int i = 0; i < BYTE_LENGTH; i += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, a, i);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, b, i);

            cv = av.dotUnsigned(bv, cv);
        }
        cv.intoArray(r, 0);
    }

    @Run(test = "testDotUnsigned")
    public static void testDotUnsigned_runner() {
        testDotUnsigned();

        for (int i = 0; i < I_SPECIES.length(); i++) {
            int res = acc[i];
            for (int j = 0; j < BYTE_LENGTH; j += B_SPECIES.length()) {
                int b_i = j + i * 4;
                res += Byte.toUnsignedInt(a[b_i])     * Byte.toUnsignedInt(b[b_i]) +
                       Byte.toUnsignedInt(a[b_i + 1]) * Byte.toUnsignedInt(b[b_i + 1]) +
                       Byte.toUnsignedInt(a[b_i + 2]) * Byte.toUnsignedInt(b[b_i + 2]) +
                       Byte.toUnsignedInt(a[b_i + 3]) * Byte.toUnsignedInt(b[b_i + 3]);
            }
            Asserts.assertEquals(res, r[i]);
        }
    }

    public static void main(String[] args) {
        TestFramework.runWithFlags("--add-modules=jdk.incubator.vector");
    }
}
