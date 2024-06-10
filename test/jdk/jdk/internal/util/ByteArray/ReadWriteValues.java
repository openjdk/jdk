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
 * @bug 8299576 8310843
 * @modules java.base/jdk.internal.util
 * @summary Verify that reads and writes of primitives are correct
 * @run junit ReadWriteValues
 */

import jdk.internal.util.ByteArray;
import jdk.internal.util.ByteArrayLittleEndian;
import org.junit.jupiter.api.*;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import static org.junit.jupiter.api.Assertions.*;

final class ReadWriteValues {

    // Makes sure unaligned read/write can be made.
    private static final int OFFSET = 1;

    private static final byte[] BUFF = new byte[Long.BYTES + OFFSET];

    private static final int ITERATIONS = 1 << 10;

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetShort(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            short expected = (short) l;
            ref.setShort(BUFF, OFFSET, expected);
            short actual = ba.getShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetShort(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            short expected = (short) l;
            ba.setShort(BUFF, OFFSET, expected);
            short actual = ref.getShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetChar(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            char expected = (char) l;
            ref.setChar(BUFF, OFFSET, expected);
            char actual = ba.getChar(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetChar(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            char expected = (char) l;
            ba.setChar(BUFF, OFFSET, expected);
            char actual = ref.getChar(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetInt(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            int expected = (int) l;
            ref.setInt(BUFF, OFFSET, expected);
            int actual = ba.getInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetInt(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            int expected = (int) l;
            ba.setInt(BUFF, OFFSET, expected);
            int actual = ref.getInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetLong(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(expected -> {
            ref.setLong(BUFF, OFFSET, expected);
            long actual = ba.getLong(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetLong(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(expected -> {
            ba.setLong(BUFF, OFFSET, expected);
            long actual = ref.getLong(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetFloat(ByteArrayImpl ref, ByteArrayImpl ba) {
        floats().forEach(expected -> {
            ref.setFloat(BUFF, OFFSET, expected);
            float actual = ba.getFloat(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetFloat(ByteArrayImpl ref, ByteArrayImpl ba) {
        floats().forEach(expected -> {
            ba.setFloat(BUFF, OFFSET, expected);
            float actual = ref.getFloat(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetDouble(ByteArrayImpl ref, ByteArrayImpl ba) {
        doubles().forEach(expected -> {
            ref.setDouble(BUFF, OFFSET, expected);
            double actual = ba.getDouble(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetDouble(ByteArrayImpl ref, ByteArrayImpl ba) {
        doubles().forEach(expected -> {
            ba.setDouble(BUFF, OFFSET, expected);
            double actual = ref.getDouble(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetUnsignedShort(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            int expected = Short.toUnsignedInt((short) l);
            ref.setUnsignedShort(BUFF, OFFSET, expected);
            int actual = ba.getUnsignedShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetUnsignedShort(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            int expected = Short.toUnsignedInt((short) l);
            ba.setUnsignedShort(BUFF, OFFSET, expected);
            int actual = ref.getUnsignedShort(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testGetUnsignedInt(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            long expected = Integer.toUnsignedLong((int) l);
            ref.setUnsignedInt(BUFF, OFFSET, expected);
            long actual = ba.getUnsignedInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    @ParameterizedTest
    @MethodSource("byteArrayImplWithRef")
    void testSetUnsignedInt(ByteArrayImpl ref, ByteArrayImpl ba) {
        longs().forEach(l -> {
            long expected = Integer.toUnsignedLong((int) l);
            ba.setUnsignedInt(BUFF, OFFSET, expected);
            long actual = ref.getUnsignedInt(BUFF, OFFSET);
            assertEquals(expected, actual);
        });
    }

    // Unusual cases

    @ParameterizedTest
    @MethodSource("byteArrayImpl")
    void testNullArray(ByteArrayImpl ba) {
        assertThrowsOriginal(NullPointerException.class, () -> ba.getInt(null, OFFSET));
        assertThrowsOriginal(NullPointerException.class, () -> ba.setInt(null, OFFSET, 1));
    }

    @ParameterizedTest
    @MethodSource("byteArrayImpl")
    void testNegArg(ByteArrayImpl ba) {
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ba.getInt(BUFF, -1));
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ba.setInt(BUFF, -1, 1));
    }

    @ParameterizedTest
    @MethodSource("byteArrayImpl")
    void testOutOfBounds(ByteArrayImpl ba) {
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ba.getInt(BUFF, BUFF.length));
        assertThrowsOriginal(IndexOutOfBoundsException.class, () -> ba.setInt(BUFF, BUFF.length, 1));
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

    static Stream<ByteArrayImpl> byteArrayImpl() {
        return Stream.of(UNSAFE, UNSAFE_LE);
    }

    static Stream<Arguments> byteArrayImplWithRef() {
        return Stream.of(
                Arguments.of(REF, UNSAFE),
                Arguments.of(REF_LE, UNSAFE_LE));
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

    private interface ByteArrayImpl {
        char getChar(byte[] b, int off);

        short getShort(byte[] b, int off);

        default int getUnsignedShort(byte[] b, int off) {
            return Short.toUnsignedInt(getShort(b, off));
        }

        int getInt(byte[] b, int off);

        default long getUnsignedInt(byte[] b, int off) {
            return Integer.toUnsignedLong(getInt(b, off));
        }

        long getLong(byte[] b, int off);

        default float getFloat(byte[] b, int off) {
            return Float.intBitsToFloat(getInt(b, off));
        }

        default double getDouble(byte[] b, int off) {
            return Double.longBitsToDouble(getLong(b, off));
        }

        /*
         * Methods for packing primitive values into byte arrays starting at given
         * offsets.
         */

        void setChar(byte[] b, int off, char val);

        void setShort(byte[] b, int off, short val);

        default void setUnsignedShort(byte[] b, int off, int val) {
            setShort(b, off, (short) val);
        }

        void setInt(byte[] b, int off, int val);

        default void setUnsignedInt(byte[] b, int off, long val) {
            setInt(b, off, (int) val);
        }

        void setLong(byte[] b, int off, long val);

        default void setFloat(byte[] b, int off, float val) {
            setInt(b, off, Float.floatToIntBits(val));
        }

        default void setDouble(byte[] b, int off, double val) {
            setLong(b, off, Double.doubleToLongBits(val));
        }
    }

    /**
    * Reference implementation from the old java.io.Bits implementation
    */
    private static final ByteArrayImpl REF = new ByteArrayImpl() {
        public char getChar(byte[] b, int off) {
            return (char) ((b[off + 1] & 0xFF) +
                    (b[off] << 8));
        }

        public short getShort(byte[] b, int off) {
            return (short) ((b[off + 1] & 0xFF) +
                    (b[off] << 8));
        }

        public int getInt(byte[] b, int off) {
            return ((b[off + 3] & 0xFF)) +
                    ((b[off + 2] & 0xFF) << 8) +
                    ((b[off + 1] & 0xFF) << 16) +
                    ((b[off]) << 24);
        }

        public long getLong(byte[] b, int off) {
            return ((b[off + 7] & 0xFFL)) +
                    ((b[off + 6] & 0xFFL) << 8) +
                    ((b[off + 5] & 0xFFL) << 16) +
                    ((b[off + 4] & 0xFFL) << 24) +
                    ((b[off + 3] & 0xFFL) << 32) +
                    ((b[off + 2] & 0xFFL) << 40) +
                    ((b[off + 1] & 0xFFL) << 48) +
                    (((long) b[off]) << 56);
        }

        public void setChar(byte[] b, int off, char val) {
            b[off + 1] = (byte) (val);
            b[off] = (byte) (val >>> 8);
        }

        public void setShort(byte[] b, int off, short val) {
            b[off + 1] = (byte) (val);
            b[off] = (byte) (val >>> 8);
        }

        public void setInt(byte[] b, int off, int val) {
            b[off + 3] = (byte) (val);
            b[off + 2] = (byte) (val >>> 8);
            b[off + 1] = (byte) (val >>> 16);
            b[off] = (byte) (val >>> 24);
        }

        public void setLong(byte[] b, int off, long val) {
            b[off + 7] = (byte) (val);
            b[off + 6] = (byte) (val >>> 8);
            b[off + 5] = (byte) (val >>> 16);
            b[off + 4] = (byte) (val >>> 24);
            b[off + 3] = (byte) (val >>> 32);
            b[off + 2] = (byte) (val >>> 40);
            b[off + 1] = (byte) (val >>> 48);
            b[off] = (byte) (val >>> 56);
        }
    };

    private static final ByteArrayImpl REF_LE = new ByteArrayImpl() {
        public char getChar(byte[] b, int off) {
            return (char) ((b[off] & 0xFF) +
                    (b[off + 1] << 8));
        }

        public short getShort(byte[] b, int off) {
            return (short) ((b[off] & 0xFF) +
                    (b[off + 1] << 8));
        }

        public int getInt(byte[] b, int off) {
            return ((b[off] & 0xFF)) +
                    ((b[off + 1] & 0xFF) << 8) +
                    ((b[off + 2] & 0xFF) << 16) +
                    ((b[off + 3]) << 24);
        }

        public long getLong(byte[] b, int off) {
            return ((b[off] & 0xFFL)) +
                    ((b[off + 1] & 0xFFL) << 8) +
                    ((b[off + 2] & 0xFFL) << 16) +
                    ((b[off + 3] & 0xFFL) << 24) +
                    ((b[off + 4] & 0xFFL) << 32) +
                    ((b[off + 5] & 0xFFL) << 40) +
                    ((b[off + 6] & 0xFFL) << 48) +
                    (((long) b[off + 7]) << 56);
        }

        public void setChar(byte[] b, int off, char val) {
            b[off] = (byte) (val);
            b[off + 1] = (byte) (val >>> 8);
        }

        public void setShort(byte[] b, int off, short val) {
            b[off] = (byte) (val);
            b[off + 1] = (byte) (val >>> 8);
        }

        public void setInt(byte[] b, int off, int val) {
            b[off] = (byte) (val);
            b[off + 1] = (byte) (val >>> 8);
            b[off + 2] = (byte) (val >>> 16);
            b[off + 3] = (byte) (val >>> 24);
        }

        public void setLong(byte[] b, int off, long val) {
            b[off] = (byte) (val);
            b[off + 1] = (byte) (val >>> 8);
            b[off + 2] = (byte) (val >>> 16);
            b[off + 3] = (byte) (val >>> 24);
            b[off + 4] = (byte) (val >>> 32);
            b[off + 5] = (byte) (val >>> 40);
            b[off + 6] = (byte) (val >>> 48);
            b[off + 7] = (byte) (val >>> 56);
        }
    };

    private static final ByteArrayImpl UNSAFE = new ByteArrayImpl() {
        public char getChar(byte[] b, int off) {
            return ByteArray.getChar(b, off);
        }

        public short getShort(byte[] b, int off) {
            return ByteArray.getShort(b, off);
        }

        public int getUnsignedShort(byte[] b, int off) {
            return ByteArray.getUnsignedShort(b, off);
        }

        public int getInt(byte[] b, int off) {
            return ByteArray.getInt(b, off);
        }

        public long getUnsignedInt(byte[] b, int off) {
            return ByteArray.getUnsignedInt(b, off);
        }

        public long getLong(byte[] b, int off) {
            return ByteArray.getLong(b, off);
        }

        public float getFloat(byte[] b, int off) {
            return ByteArray.getFloat(b, off);
        }

        public double getDouble(byte[] b, int off) {
            return ByteArray.getDouble(b, off);
        }

        public void setChar(byte[] b, int off, char val) {
            ByteArray.setChar(b, off, val);
        }

        public void setShort(byte[] b, int off, short val) {
            ByteArray.setShort(b, off, val);
        }

        public void setUnsignedShort(byte[] b, int off, int val) {
            ByteArray.setUnsignedShort(b, off, val);
        }

        public void setInt(byte[] b, int off, int val) {
            ByteArray.setInt(b, off, val);
        }

        public void setUnsignedInt(byte[] b, int off, long val) {
            ByteArray.setUnsignedInt(b, off, val);
        }

        public void setLong(byte[] b, int off, long val) {
            ByteArray.setLong(b, off, val);
        }

        public void setFloat(byte[] b, int off, float val) {
            ByteArray.setFloat(b, off, val);
        }

        public void setDouble(byte[] b, int off, double val) {
            ByteArray.setDouble(b, off, val);
        }
    };

    private static final ByteArrayImpl UNSAFE_LE = new ByteArrayImpl() {
        public char getChar(byte[] b, int off) {
            return ByteArrayLittleEndian.getChar(b, off);
        }

        public short getShort(byte[] b, int off) {
            return ByteArrayLittleEndian.getShort(b, off);
        }

        public int getUnsignedShort(byte[] b, int off) {
            return ByteArrayLittleEndian.getUnsignedShort(b, off);
        }

        public int getInt(byte[] b, int off) {
            return ByteArrayLittleEndian.getInt(b, off);
        }

        public long getUnsignedInt(byte[] b, int off) {
            return ByteArrayLittleEndian.getUnsignedInt(b, off);
        }

        public long getLong(byte[] b, int off) {
            return ByteArrayLittleEndian.getLong(b, off);
        }

        public float getFloat(byte[] b, int off) {
            return ByteArrayLittleEndian.getFloat(b, off);
        }

        public double getDouble(byte[] b, int off) {
            return ByteArrayLittleEndian.getDouble(b, off);
        }

        public void setChar(byte[] b, int off, char val) {
            ByteArrayLittleEndian.setChar(b, off, val);
        }

        public void setShort(byte[] b, int off, short val) {
            ByteArrayLittleEndian.setShort(b, off, val);
        }

        public void setUnsignedShort(byte[] b, int off, int val) {
            ByteArrayLittleEndian.setUnsignedShort(b, off, val);
        }

        public void setInt(byte[] b, int off, int val) {
            ByteArrayLittleEndian.setInt(b, off, val);
        }

        public void setUnsignedInt(byte[] b, int off, long val) {
            ByteArrayLittleEndian.setUnsignedInt(b, off, val);
        }

        public void setLong(byte[] b, int off, long val) {
            ByteArrayLittleEndian.setLong(b, off, val);
        }

        public void setFloat(byte[] b, int off, float val) {
            ByteArrayLittleEndian.setFloat(b, off, val);
        }

        public void setDouble(byte[] b, int off, double val) {
            ByteArrayLittleEndian.setDouble(b, off, val);
        }
    };
}
