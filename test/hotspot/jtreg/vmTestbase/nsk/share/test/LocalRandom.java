/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.share.test;

import java.util.concurrent.ThreadLocalRandom;
import nsk.share.TestFailure;

/**
 * Utility class which encapsulates all useful static methods.
 */
public class LocalRandom {
    private static int minPauseTime = 3000;
    private static int maxPauseTime = 5000;
    private static int maxRandomCount = 65536;

    /*
     * Return next random double number.
     *
     * @return random double
     */
    public static double random() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public static double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }

    public static byte nextByte() {
        return (byte) nextInt(Byte.MIN_VALUE, Byte.MAX_VALUE);
    }

    public static char nextChar() {
        return (char) nextInt(Character.MIN_VALUE, Character.MAX_VALUE);
    }

    public static short nextShort() {
        return (short) nextInt(Short.MIN_VALUE, Short.MAX_VALUE);
    }

    public static boolean nextBoolean() {
        return ThreadLocalRandom.current().nextBoolean();
    }

    public static void nextBytes(byte[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        byte hash = 0;
        byte b;
        for (int i = 0; i < arr.length - k; i += k) {
            b = nextByte();
            arr[i] = b;
            hash ^= b;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(byte[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        byte hash = 0;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != 0) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextShorts(short[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        short hash = 0;
        short s;
        for (int i = 0; i < arr.length - k; i += k) {
            s = nextShort();
            arr[i] = s;
            hash ^= s;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(short[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        short hash = 0;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != 0) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextChars(char[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        char hash = 0;
        char c;
        for (int i = 0; i < arr.length - k; i += k) {
            c = nextChar();
            arr[i] = c;
            hash ^= c;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(char[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        char hash = 0;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != 0) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextInts(int[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        int hash = 0;
        int in;
        for (int i = 0; i < arr.length - k; i += k) {
            in = nextInt();
            hash ^= in;
            arr[i] = in;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(int[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        int hash = 0;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != 0) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextBooleans(boolean[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        boolean hash = false;
        boolean b;
        for (int i = 0; i < arr.length - k; i += k) {
            b = nextBoolean();
            hash ^= b;
            arr[i] = b;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(boolean[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        boolean hash = false;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != false) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextLongs(long[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        long hash = 0;
        long l;
        for (int i = 0; i < arr.length - k; i += k) {
            l = nextLong();
            hash ^= l;
            arr[i] = l;
        }
        arr[arr.length - k] = hash;
    }

    public static void validate(long[] arr) {
        int k = Math.max(1, arr.length / maxRandomCount);
        long hash = 0;
        for (int i = 0; i < arr.length; i += k) {
            hash ^= arr[i];
        }
        if (hash != 0) {
            throw new TestFailure(
                    "Validation failure: " + arr.getClass() + " hash: " + hash);
        }
    }

    public static void nextFloats(float[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        for (int i = 0; i < arr.length - k; i += k) {
            arr[i] = nextFloat();
        }
    }

    public static void validate(float[] arr) {
    }

    public static void nextDoubles(double[] arr) {
        if (arr.length == 0) {
            return;
        }
        int k = Math.max(1, arr.length / maxRandomCount);
        for (int i = 0; i < arr.length - k; i += k) {
            arr[i] = nextDouble();
        }
    }

    public static void validate(double[] arr) {
    }

    public static int nextInt() {
        return ThreadLocalRandom.current().nextInt();
    }

    /**
     * Return next integer value from 0..n range.
     *
     * @param n maximum value
     * @return random integer
     */
    public static int nextInt(int n) {
        return ThreadLocalRandom.current().nextInt(n);
    }

    public static long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }

    /**
     * Return next random integer from min..max range.
     *
     * @param min minimum value
     * @param max maximum value
     * @return random integer
     */
    public static int nextInt(int min, int max) {
        return min + nextInt(max - min);
    }

    /**
     * Return next random float number.
     *
     * @return random double
     */
    public static float nextFloat() {
        return ThreadLocalRandom.current().nextFloat();
    }

    /**
     * Return random pause time.
     */
    public static long randomPauseTime() {
        return nextInt(minPauseTime, maxPauseTime);
    }

    /**
     * Set minimum pause time.
     *
     * @param minPauseTime minimum pause time
     */
    public static void setMinPauseTime(int minPauseTime) {
        LocalRandom.minPauseTime = minPauseTime;
    }

    /**
     * Set maximum pause time.
     *
     * @param maxPauseTime maximum pause time
     */
    public static void setMaxPauseTime(int maxPauseTime) {
        LocalRandom.maxPauseTime = maxPauseTime;
    }
}
