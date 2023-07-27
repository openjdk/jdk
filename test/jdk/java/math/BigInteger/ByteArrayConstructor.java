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

import java.math.Accessor;
import java.math.BigInteger;
import java.util.Random;

/**
 * @test
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @key randomness
 * @summary Exercises minimality of BigInteger.mag field
 * @build java.base/java.math.Accessor
 * @run main ByteArrayConstructor
 */
public class ByteArrayConstructor {

    private static final int DEFAULT_MAX_DURATION_MILLIS = 3_000;

    private final int maxDurationMillis;
    private final Random r;
    private volatile boolean stop;

    private ByteArrayConstructor(String[] args) {
        this.maxDurationMillis = maxDurationMillis(args);
        this.r = RandomFactory.getRandom();
        this.stop = false;
    }

    public static void main(String[] args) throws InterruptedException {
        ByteArrayConstructor instance = new ByteArrayConstructor(args);
        instance.nonNegative();
        instance.negative();
    }

    private void nonNegative() throws InterruptedException {
        byte[] ba = nonNegativeBytes();
        doBigIntegers(ba, ba[0]);
    }

    private void negative() throws InterruptedException {
        byte[] ba = negativeBytes();
        doBigIntegers(ba, (byte) ~ba[0]);
    }

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
            int[] mag = Accessor.mag(new BigInteger(ba));
            if (mag.length > 0 && mag[0] == 0) {
                throw new IllegalStateException(
                        String.format("inconsistent BigInteger: mag.length=%d, mag[0]=%d",
                                mag.length, mag[0]));
            }
        }
    }

    private byte[] nonNegativeBytes() {
        byte[] ba = new byte[1 + 1_024];
        byte b0;
        while ((b0 = (byte) r.nextInt()) < 0);  // empty body
        r.nextBytes(ba);
        ba[0] = b0;
        /* Except for ba[0], fill most significant half with zeros. */
        for (int i = 1; i <= 512; ++i) {
            ba[i] = 0;
        }
        return ba;
    }

    private byte[] negativeBytes() {
        byte[] ba = new byte[1 + 1_024];
        byte b0;
        while ((b0 = (byte) r.nextInt()) >= 0);  // empty body
        r.nextBytes(ba);
        ba[0] = b0;
        /* Except for ba[0], fill most significant half with -1 bytes. */
        for (int i = 1; i <= 512; ++i) {
            ba[i] = -1;
        }
        return ba;
    }

    private static int maxDurationMillis(String[] args) {
        try {
            if (args.length > 0) {
                return Integer.parseInt(args[0]);
            }
        } catch (NumberFormatException ignore) {
        }
        return DEFAULT_MAX_DURATION_MILLIS;
    }

}
