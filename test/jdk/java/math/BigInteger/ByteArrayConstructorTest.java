/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.Accessor;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @test
 * @bug 8319174
 * @summary Exercises minimality of BigInteger.mag field (use -Dseed=X to set PRANDOM seed)
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build java.base/java.math.Accessor
 * @key randomness
 * @run junit/othervm -DmaxDurationMillis=3000 ByteArrayConstructorTest
 */
public class ByteArrayConstructorTest {

    private static final int DEFAULT_MAX_DURATION_MILLIS = 3_000;

    public static final int N = 1_024;

    private static int maxDurationMillis;
    private static final Random rnd = RandomFactory.getRandom();
    private volatile boolean stop = false;

    @BeforeAll
    static void setMaxDurationMillis() {
        maxDurationMillis = Math.max(maxDurationMillis(), 0);
    }

    @Test
    public void testNonNegative() throws InterruptedException {
        byte[] ba = nonNegativeBytes();
        doBigIntegers(ba, ba[0]);  // a mask to flip to 0 and back to ba[0]
    }

    @Test
    public void testNegative() throws InterruptedException {
        byte[] ba = negativeBytes();
        doBigIntegers(ba, (byte) ~ba[0]);  // a mask to flip to -1 and back to ba[0]
    }

    /*
     * Starts a thread th that keeps flipping the "sign" byte in the array ba
     * from the original value to 0 or -1 and back, depending on ba[0] being
     * non-negative or negative, resp.
     * (ba is "big endian", the least significant byte is the one with the
     * highest index.)
     *
     * In the meantime, the current thread keeps creating BigInteger instances
     * with ba and checks that the internal invariant holds, despite the
     * attempts by thread th to racily modify ba.
     * It does so at least as indicated by maxDurationMillis.
     *
     * Finally, this thread requests th to stop and joins with it, either
     * because maxDurationMillis has expired, or because of an invalid invariant.
     */
    private void doBigIntegers(byte[] ba, byte mask) throws InterruptedException {
        Thread th = new Thread(() -> {
            while (!stop) {
                ba[0] ^= mask;
            }
        });
        th.start();

        try {
            createBigIntegers(maxDurationMillis, ba);
        } finally {
            stop = true;
            th.join(1_000);
        }
    }

    private void createBigIntegers(int maxDurationMillis, byte[] ba) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxDurationMillis) {
            BigInteger bi = new BigInteger(ba);
            int[] mag = Accessor.mag(bi);
            assertTrue(mag.length == 0 || mag[0] != 0,
                    String.format("inconsistent BigInteger: mag.length=%d, mag[0]=%d",
                        mag.length, mag[0]));
        }
    }

    private byte[] nonNegativeBytes() {
        byte[] ba = new byte[1 + N];
        byte b0;
        while ((b0 = (byte) rnd.nextInt()) < 0);  // empty body
        rnd.nextBytes(ba);
        ba[0] = b0;
        /* Except for ba[0], fill most significant half with zeros. */
        for (int i = 1; i <= N / 2; ++i) {
            ba[i] = 0;
        }
        return ba;
    }

    private byte[] negativeBytes() {
        byte[] ba = new byte[1 + N];
        byte b0;
        while ((b0 = (byte) rnd.nextInt()) >= 0);  // empty body
        rnd.nextBytes(ba);
        ba[0] = b0;
        /* Except for ba[0], fill most significant half with -1 bytes. */
        for (int i = 1; i <= N / 2; ++i) {
            ba[i] = -1;
        }
        return ba;
    }

    private static int maxDurationMillis() {
        try {
            return Integer.parseInt(System.getProperty("maxDurationMillis",
                    Integer.toString(DEFAULT_MAX_DURATION_MILLIS)));
        } catch (NumberFormatException ignore) {
        }
        return DEFAULT_MAX_DURATION_MILLIS;
    }

}
