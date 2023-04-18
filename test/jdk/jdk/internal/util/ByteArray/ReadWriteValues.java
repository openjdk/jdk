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

/*
 * @test
 * @bug 8299576
 * @modules java.base/jdk.internal.util
 * @summary Verify that reads and writes of primitives are correct
 * @run junit ReadWriteValues
 */

import jdk.internal.util.ByteArray;
import org.junit.jupiter.api.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class ReadWriteValues {

    // Makes sure unaligned read/write can be made.
    private static final int OFFSET = 1;

    private static final byte[] BUFF = new byte[Long.BYTES + OFFSET];

    private static final int ITERATIONS = 1 << 10;

    @Test
    void testGetShort() {
        longs().forEach(l -> {
            short expected = (short) l;
            RefImpl.putShort(BUFF, OFFSET, expected);
            short actual = ByteArray.getShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutShort() {
        longs().forEach(l -> {
            short expected = (short) l;
            ByteArray.setShort(BUFF, OFFSET, expected);
            short actual = RefImpl.getShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testGetChar() {
        longs().forEach(l -> {
            char expected = (char) l;
            RefImpl.putChar(BUFF, OFFSET, expected);
            char actual = ByteArray.getChar(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutChar() {
        longs().forEach(l -> {
            char expected = (char) l;
            ByteArray.setChar(BUFF, OFFSET, expected);
            char actual = RefImpl.getChar(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testGetInt() {
        longs().forEach(l -> {
            int expected = (int) l;
            RefImpl.putInt(BUFF, OFFSET, expected);
            int actual = ByteArray.getInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutInt() {
        longs().forEach(l -> {
            int expected = (int) l;
            ByteArray.setInt(BUFF, OFFSET, expected);
            int actual = RefImpl.getInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testGetLong() {
        longs().forEach(expected -> {
            RefImpl.putLong(BUFF, OFFSET, expected);
            long actual = ByteArray.getLong(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutLong() {
        longs().forEach(expected -> {
            ByteArray.setLong(BUFF, OFFSET, expected);
            long actual = RefImpl.getLong(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testGetFloat() {
        floats().forEach(expected -> {
            RefImpl.putFloat(BUFF, OFFSET, expected);
            float actual = ByteArray.getFloat(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutFloat() {
        floats().forEach(expected -> {
            ByteArray.setFloat(BUFF, OFFSET, expected);
            float actual = RefImpl.getFloat(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testGetDouble() {
        doubles().forEach(expected -> {
            RefImpl.putDouble(BUFF, OFFSET, expected);
            double actual = ByteArray.getDouble(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutDouble() {
        doubles().forEach(expected -> {
            ByteArray.setDouble(BUFF, OFFSET, expected);
            double actual = RefImpl.getDouble(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @Test
    void testPutUnsignedShort() {
        longs().forEach(l -> {
            int expected = Short.toUnsignedInt((short) l);
            ByteArray.setUnsignedShort(BUFF, OFFSET, expected);
            int actual = Short.toUnsignedInt(RefImpl.getShort(BUFF, OFFSET));
            assertEquals(expected, actual);
        });
    }

    // Unusual cases

    @Test
    void testNullArray() {
        assertThrowsOriginal(NullPointerException.class, () -> ByteArray.getInt(null, OFFSET));
        assertThrowsOriginal(NullPointerException.class, () -> ByteArray.setInt(null, OFFSET, 1));
    }

    @Test
    void testNegArg() {
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ByteArray.getInt(BUFF, -1));
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ByteArray.setInt(BUFF, -1, 1));
    }

    @Test
    void testOutOfBounds() {
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ByteArray.getInt(BUFF, BUFF.length));
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ByteArray.setInt(BUFF, BUFF.length, 1));
    }

    static LongStream longs() {
        return ThreadLocalRandom.current().longs(ITERATIONS);
    }

    static DoubleStream doubles() {
        return DoubleStream.concat(
                ThreadLocalRandom.current().doubles(ITERATIONS),
                DoubleStream.of(Double.NaN,
                        Double.NEGATIVE_INFINITY,
                        Double.POSITIVE_INFINITY,
                        Double.MAX_VALUE,
                        Double.MIN_VALUE,
                        -0.0d
                        +0.0d)
        );
    }
    static Stream<Float> floats() {
        return Stream.concat(
                ThreadLocalRandom.current().doubles(ITERATIONS).mapToObj(d -> (float)d),
                Stream.of(Float.NaN,
                        Float.NEGATIVE_INFINITY,
                        Float.POSITIVE_INFINITY,
                        Float.MAX_VALUE,
                        Float.MIN_VALUE,
                        -0.0f
                        +0.0f)
        );
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }

    <X extends Exception> void assertThrowsOriginal(Class<X> type,
                                                    ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            if (type.isInstance(e)) {
                return;
            }
            if (type.isInstance(e.getCause())) {
                return;
            }
            throw new AssertionError(e);
        }

    }

    /**
    * Reference implementation from the old java.io.Bits implementation
    */
    private static final class RefImpl {
        private RefImpl() {}

        static char getChar(byte[] b, int off) {
            return (char) ((b[off + 1] & 0xFF) +
                    (b[off] << 8));
        }

        static short getShort(byte[] b, int off) {
            return (short) ((b[off + 1] & 0xFF) +
                    (b[off] << 8));
        }

        static int getInt(byte[] b, int off) {
            return ((b[off + 3] & 0xFF)) +
                    ((b[off + 2] & 0xFF) << 8) +
                    ((b[off + 1] & 0xFF) << 16) +
                    ((b[off]) << 24);
        }

        static float getFloat(byte[] b, int off) {
            return Float.intBitsToFloat(getInt(b, off));
        }

        static long getLong(byte[] b, int off) {
            return ((b[off + 7] & 0xFFL)) +
                    ((b[off + 6] & 0xFFL) << 8) +
                    ((b[off + 5] & 0xFFL) << 16) +
                    ((b[off + 4] & 0xFFL) << 24) +
                    ((b[off + 3] & 0xFFL) << 32) +
                    ((b[off + 2] & 0xFFL) << 40) +
                    ((b[off + 1] & 0xFFL) << 48) +
                    (((long) b[off]) << 56);
        }

        static double getDouble(byte[] b, int off) {
            return Double.longBitsToDouble(getLong(b, off));
        }

        /*
         * Methods for packing primitive values into byte arrays starting at given
         * offsets.
         */

        static void putChar(byte[] b, int off, char val) {
            b[off + 1] = (byte) (val);
            b[off] = (byte) (val >>> 8);
        }

        static void putShort(byte[] b, int off, short val) {
            b[off + 1] = (byte) (val);
            b[off] = (byte) (val >>> 8);
        }

        static void putInt(byte[] b, int off, int val) {
            b[off + 3] = (byte) (val);
            b[off + 2] = (byte) (val >>> 8);
            b[off + 1] = (byte) (val >>> 16);
            b[off] = (byte) (val >>> 24);
        }

        static void putFloat(byte[] b, int off, float val) {
            putInt(b, off, Float.floatToIntBits(val));
        }

        static void putLong(byte[] b, int off, long val) {
            b[off + 7] = (byte) (val);
            b[off + 6] = (byte) (val >>> 8);
            b[off + 5] = (byte) (val >>> 16);
            b[off + 4] = (byte) (val >>> 24);
            b[off + 3] = (byte) (val >>> 32);
            b[off + 2] = (byte) (val >>> 40);
            b[off + 1] = (byte) (val >>> 48);
            b[off] = (byte) (val >>> 56);
        }

        static void putDouble(byte[] b, int off, double val) {
            putLong(b, off, Double.doubleToLongBits(val));
        }
    }
}
