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

import java.util.Arrays;
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

    private static byte[] a = new byte[BYTE_LENGTH];
    private static byte[] b = new byte[BYTE_LENGTH];
    private static int[] acc = new int[INT_LENGTH];
    private static int[] r = new int[INT_LENGTH];

    private enum Data { RANDOM, MAX_BY_MAX, MAX_BY_MIN, UNSIGNED_MAX }

    private static void fill(Data data) {
        switch (data) {
            case RANDOM -> {
                for (int byteIndex = 0; byteIndex < BYTE_LENGTH; byteIndex++) {
                    a[byteIndex] = (byte) RD.nextInt();
                    b[byteIndex] = (byte) RD.nextInt();
                }
                for (int lane = 0; lane < INT_LENGTH; lane++) {
                    acc[lane] = RD.nextInt();
                }
            }
            case MAX_BY_MAX -> {
                Arrays.fill(a, Byte.MAX_VALUE);
                Arrays.fill(b, Byte.MAX_VALUE);
                Arrays.fill(acc, Integer.MAX_VALUE);
            }
            case MAX_BY_MIN -> {
                Arrays.fill(a, Byte.MAX_VALUE);
                Arrays.fill(b, Byte.MIN_VALUE);
                Arrays.fill(acc, Integer.MIN_VALUE);
            }
            case UNSIGNED_MAX -> {
                Arrays.fill(a, (byte) 0xFF);
                Arrays.fill(b, (byte) 0xFF);
                Arrays.fill(acc, Integer.MAX_VALUE);
            }
        }
    }

    private static int product(int byteIndex, boolean unsigned) {
        if (unsigned) {
            return Byte.toUnsignedInt(a[byteIndex]) *
                   Byte.toUnsignedInt(b[byteIndex]);
        }
        return a[byteIndex] * b[byteIndex];
    }

    private static void verify(boolean unsigned, Data data) {
        for (int lane = 0; lane < I_SPECIES.length(); lane++) {
            int expected = acc[lane];
            for (int offset = 0; offset < BYTE_LENGTH; offset += B_SPECIES.length()) {
                int byteIndex = offset + lane * 4;
                expected += product(byteIndex, unsigned)
                          + product(byteIndex + 1, unsigned)
                          + product(byteIndex + 2, unsigned)
                          + product(byteIndex + 3, unsigned);
            }
            Asserts.assertEquals(expected, r[lane], data + " lane " + lane);
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
        for (int offset = 0; offset < BYTE_LENGTH; offset += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, a, offset);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, b, offset);

            cv = av.dot(bv, cv);
        }
        cv.intoArray(r, 0);
    }

    @Run(test = "testDot")
    public static void testDot_runner() {
        for (Data data : Data.values()) {
            fill(data);
            testDot();
            verify(false, data);
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
        for (int offset = 0; offset < BYTE_LENGTH; offset += B_SPECIES.length()) {
            ByteVector av = ByteVector.fromArray(B_SPECIES, a, offset);
            ByteVector bv = ByteVector.fromArray(B_SPECIES, b, offset);

            cv = av.dotUnsigned(bv, cv);
        }
        cv.intoArray(r, 0);
    }

    @Run(test = "testDotUnsigned")
    public static void testDotUnsigned_runner() {
        for (Data data : Data.values()) {
            fill(data);
            testDotUnsigned();
            verify(true, data);
        }
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addFlags("--add-modules=jdk.incubator.vector");
        framework.addScenarios(new Scenario(0, "-XX:MaxVectorSize=8"),
                               new Scenario(1, "-XX:MaxVectorSize=16"),
                               new Scenario(2));
        framework.start();
    }
}
