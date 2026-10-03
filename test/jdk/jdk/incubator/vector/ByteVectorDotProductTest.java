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

/*
 * @test
 * @library /test/lib
 * @bug 8377386
 * @key randomness
 * @summary Test signed and unsigned Vector API dot product operators
 * @modules jdk.incubator.vector
 * @run testng/othervm/timeout=300 -ea -esa -Xbatch -XX:-TieredCompilation ByteVectorDotProductTest
 */

import java.util.Arrays;
import java.util.Random;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.ShortVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.Vector;
import jdk.incubator.vector.VectorShape;
import jdk.incubator.vector.VectorSpecies;
import jdk.test.lib.Utils;
import org.testng.Assert;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

public class ByteVectorDotProductTest {

    private static final Random RANDOM = Utils.getRandomInstance();

    @FunctionalInterface interface Fill { void fill(byte[] a, byte[] b, int[] acc); }

    static int[] scalarDot(byte[] a, byte[] b, int[] acc) {
        int[] res = acc.clone();
        for (int i = 0; i < res.length; i++) {
            int byteIndex = i * 4;
            res[i] += a[byteIndex]     * b[byteIndex]     +
                      a[byteIndex + 1] * b[byteIndex + 1] +
                      a[byteIndex + 2] * b[byteIndex + 2] +
                      a[byteIndex + 3] * b[byteIndex + 3];
        }
        return res;
    }

    static int[] scalarDotUnsigned(byte[] a, byte[] b, int[] acc) {
        int[] res = acc.clone();
        for (int i = 0; i < res.length; i++) {
            int byteIndex = i * 4;
            res[i] += Byte.toUnsignedInt(a[byteIndex])     * Byte.toUnsignedInt(b[byteIndex])     +
                      Byte.toUnsignedInt(a[byteIndex + 1]) * Byte.toUnsignedInt(b[byteIndex + 1]) +
                      Byte.toUnsignedInt(a[byteIndex + 2]) * Byte.toUnsignedInt(b[byteIndex + 2]) +
                      Byte.toUnsignedInt(a[byteIndex + 3]) * Byte.toUnsignedInt(b[byteIndex + 3]);
        }
        return res;
    }

    @DataProvider(name = "vecProvider")
    public static Object[][] vecProvider() {
        Object[][] data = {
            {"random", (Fill) (a, b, acc) -> {
                for (int i = 0; i < a.length; i++) {
                    a[i] = (byte) RANDOM.nextInt();
                    b[i] = (byte) RANDOM.nextInt();
                }

                for (int i = 0; i < acc.length; i++) {
                    acc[i] = RANDOM.nextInt();
                }
            }},
            {"maxByMax", (Fill) (a, b, acc) -> {
                Arrays.fill(a, Byte.MAX_VALUE);
                Arrays.fill(b, Byte.MAX_VALUE);
                Arrays.fill(acc, Integer.MAX_VALUE);
            }},
            {"maxByMin", (Fill) (a, b, acc) -> {
                Arrays.fill(a, Byte.MAX_VALUE);
                Arrays.fill(b, Byte.MIN_VALUE);
                Arrays.fill(acc, Integer.MIN_VALUE);
            }},
        };

        return data;
    }

    @Test(dataProvider = "vecProvider")
    public void testDot(String name, Fill fill) {
        for (VectorShape shape : VectorShape.values()) {
            VectorSpecies<Byte> bs = VectorSpecies.of(byte.class, shape);
            VectorSpecies<Integer> is = VectorSpecies.of(int.class, shape);

            byte[] a = new byte[bs.length()];
            byte[] b = new byte[bs.length()];
            int[] acc = new int[is.length()];
            fill.fill(a, b, acc);

            ByteVector av = ByteVector.fromArray(bs, a, 0);
            ByteVector bv = ByteVector.fromArray(bs, b, 0);
            IntVector accv = IntVector.fromArray(is, acc, 0);

            Assert.assertEquals(av.dot(bv, accv).toArray(),
                                scalarDot(a, b, acc), name + " " + shape);
            Assert.assertEquals(av.dotUnsigned(bv, accv).toArray(),
                                scalarDotUnsigned(a, b, acc), name + " " + shape);
        }
    }

    @Test(dataProvider = "vecProvider")
    public void testDotException(String name, Fill fill) {
        VectorSpecies<Byte> bs = ByteVector.SPECIES_128;
        VectorSpecies<Integer> is = IntVector.SPECIES_128;

        byte[] a = new byte[bs.length()];
        byte[] b = new byte[bs.length()];
        int[] acc = new int[is.length()];
        fill.fill(a, b, acc);

        ByteVector av = ByteVector.fromArray(bs, a, 0);
        ByteVector bv = ByteVector.fromArray(bs, b, 0);
        IntVector accv = IntVector.fromArray(is, acc, 0);

        /* ---------- SIGNED ---------- */

        // mismatched operand species
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dot(ByteVector.zero(ByteVector.SPECIES_64), accv)
            ).getMessage().contains("Bad species"),
            "signed: " + name
        );

        // mismatched acc shape
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dot(bv, IntVector.zero(IntVector.SPECIES_64))
            ).getMessage().contains("Bad shape"),
            "signed: " + name
        );

        // mismatched acc element type
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dot(bv, (Vector<Integer>) (Vector<?>) ShortVector.zero(ShortVector.SPECIES_128))
            ).getMessage().contains("element type"),
            "signed: " + name
        );

        /* ---------- UNSIGNED ---------- */

        // mismatched operand species
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dotUnsigned(ByteVector.zero(ByteVector.SPECIES_64), accv)
            ).getMessage().contains("Bad species"),
            "unsigned: " + name
        );

        // mismatched acc shape
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dotUnsigned(bv, IntVector.zero(IntVector.SPECIES_64))
            ).getMessage().contains("Bad shape"),
            "unsigned: " + name
        );

        // mismatched acc element type
        Assert.assertTrue(
            Assert.expectThrows(
                IllegalArgumentException.class,
                () -> av.dotUnsigned(bv, (Vector<Integer>) (Vector<?>) ShortVector.zero(ShortVector.SPECIES_128))
            ).getMessage().contains("element type"),
            "unsigned: " + name
        );
    }
}
