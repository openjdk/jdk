/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.quic.VariableLengthEncoder;
import jtreg.SkippedException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @library /test/lib
 * @modules java.net.http/jdk.internal.net.http.quic
 * @run junit/othervm VariableLengthTest
 * @summary Tests to check quic/util methods encode/decodeVariableLength methods
 *  work as expected.
 */
public class VariableLengthTest {
    static final Class<? extends Throwable> IAE = IllegalArgumentException.class;

    public static Object[][] decodeInvariants() {
        return new Object[][]
            {
                { new byte[]{7},                                                        7, 1 }, // 00
                { new byte[]{65, 11},                                                 267, 2 }, // 01
                { new byte[]{-65, 11, 22, 33},                                 1057691169, 4 }, // 10
                { new byte[]{-1, 11, 22, 33, 44, 55, 66, 77},        4542748980864827981L, 8 }, // 11
                { new byte[]{-1, -11, -22, -33, -44, -55, -66, -77}, 4608848040752168627L, 8 },
                { new byte[]{},                                                        -1, 0 },
                { new byte[]{-65},                                                     -1, 0 },
            };
    }
    public static Object[][] encodeInvariants() {
        return new Object[][]
            {
                { 7,                      1, null }, // 00
                { 267,                    2, null }, // 01
                { 1057691169,             4, null }, // 10
                { 4542748980864827981L,   8, null }, // 11
                { Long.MAX_VALUE,         0, IAE  },
                { -1,                     0, IAE  },
            };
    }
    public static Object[][] prefixInvariants() {
        return new Object[][]
            {
                { Long.MAX_VALUE,          0, IAE  },
                { 4611686018427387903L+1,  0, IAE  },
                { 4611686018427387903L,    3, null },
                { 4611686018427387903L-1,  3, null },
                { 1073741823+1,            3, null },
                { 1073741823,              2, null }, // (length > (1L << 30)-1)
                { 1073741823-1,            2, null },
                { 16383+1,                 2, null },
                { 16383,                   1, null }, // (length > (1L << 14)-1
                { 16383-1,                 1, null },
                { 63+1,                    1, null },
                { 63  ,                    0, null }, // (length > (1L << 6)-1
                { 63-1,                    0, null },
                { 100,                     1, null },
                { 10,                      0, null },
                { 1,                       0, null },
                { 0,                       0, null }, // (length >= 0)
                { -1,                      0, IAE  },
                { -10,                     0, IAE  },
                { -100,                    0, IAE  },
                { Long.MIN_VALUE,          0, IAE  },
                { -4611686018427387903L-1, 0, IAE  },
                { -4611686018427387903L,   0, IAE  },
                { -4611686018427387903L+1, 0, IAE  },
                { -1073741823-1,           0, IAE  },
                { -1073741823,             0, IAE  }, // (length > (1L << 30)-1)
                { -1073741823+1,           0, IAE  },
                { -16383-1,                0, IAE  },
                { -16383,                  0, IAE  }, // (length > (1L << 14)-1
                { -16383+1,                0, IAE  },
                { -63-1,                   0, IAE  },
                { -63  ,                   0, IAE  }, // (length > (1L << 6)-1
                { -63+1,                   0, IAE  },
            };
    }

    @ParameterizedTest
    @MethodSource("decodeInvariants")
    public void testDecode(byte[] values, long expectedLength, int expectedPosition) {
        ByteBuffer bb = ByteBuffer.wrap(values);
        var actualLength = VariableLengthEncoder.decode(bb);
        assertEquals(expectedLength, actualLength);

        var actualPosition = bb.position();
        assertEquals(expectedPosition, actualPosition);
    }

    @ParameterizedTest
    @MethodSource("decodeInvariants")
    public void testPeek(byte[] values, long expectedLength, int expectedPosition) {
        ByteBuffer bb = ByteBuffer.wrap(values);
        var actualLength = VariableLengthEncoder.peekEncodedValue(bb, 0);
        assertEquals(expectedLength, actualLength);

        var actualPosition = bb.position();
        assertEquals(0, actualPosition);
    }

    @ParameterizedTest
    @MethodSource("encodeInvariants")
    public void testEncode(long length, int capacity, Class<? extends Exception> exception) throws IOException {
        var actualBuffer = ByteBuffer.allocate(capacity);
        var expectedBuffer = getTestBuffer(length, capacity);

        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.encode(actualBuffer, length));
            // if method fails ensure that position hasn't changed
            var actualPosition = actualBuffer.position();
            assertEquals(capacity, actualPosition);
        } else {
            VariableLengthEncoder.encode(actualBuffer, length);
            var actualPosition = actualBuffer.position();
            assertEquals(capacity, actualPosition);

            // check length prefix
            int firstByte = actualBuffer.get(0) & 0xFF;
            int lengthPrefix = firstByte & 0xC0;
            lengthPrefix >>= 6;
            int expectedValue = (int)(Math.log(capacity) / Math.log(2));
            assertEquals(expectedValue, lengthPrefix);

            // check length encoded in buffer correctly
            int b  = firstByte & 0x3F;
            actualBuffer.put(0, (byte) b);
            assertEquals(0, actualBuffer.compareTo(expectedBuffer));
        }
    }

    @ParameterizedTest
    @MethodSource("prefixInvariants")
    public void testLengthPrefix(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getVariableLengthPrefix(length));
        } else {
            var actualValue = VariableLengthEncoder.getVariableLengthPrefix(length);
            assertEquals(expectedPrefix, actualValue);
        }
    }

    // Encode the given length and then decodes it and compares
    // the results, asserting various invariants along the way.
    @ParameterizedTest
    @MethodSource("prefixInvariants")
    public void testEncodeDecode(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getEncodedSize(length));
            assertThrows(exception, () -> VariableLengthEncoder.encode(ByteBuffer.allocate(16), length));
        } else {
            var actualSize = VariableLengthEncoder.getEncodedSize(length);
            assertEquals(1 << expectedPrefix, actualSize);
            assertTrue(actualSize > 0, "length is negative or zero: " + actualSize);
            assertTrue(actualSize < 9, "length is too big: " + actualSize);

            // Use different offsets for the position at which to encode/decode
            for (int offset : List.of(0, 10)) {
                System.out.printf("Encode/Decode %s on %s bytes with offset %s%n",
                        length, actualSize, offset);

                // allocate buffers: one exact, one too short, one too long
                ByteBuffer exact = ByteBuffer.allocate(actualSize + offset);
                exact.position(offset);
                ByteBuffer shorter = ByteBuffer.allocate(actualSize - 1 + offset);
                shorter.position(offset);
                ByteBuffer shorterref = ByteBuffer.allocate(actualSize - 1 + offset);
                shorterref.position(offset);
                ByteBuffer longer = ByteBuffer.allocate(actualSize + 10 + offset);
                longer.position(offset);

                // attempt to encode with a buffer too short
                assertThrows(IAE, () -> VariableLengthEncoder.encode(shorter, length));
                assertEquals(offset, shorter.position());
                assertEquals(shorter.capacity(), shorter.limit());

                assertEquals(-1, shorter.mismatch(shorterref));
                assertEquals(-1, shorterref.mismatch(shorter));

                // attempt to encode with a buffer that has the exact size
                var exactres = VariableLengthEncoder.encode(exact, length);
                assertEquals(actualSize, exactres);
                assertEquals(actualSize + offset, exact.position());
                assertFalse(exact.hasRemaining());

                // attempt to encode with a buffer that has more bytes
                var longres = VariableLengthEncoder.encode(longer, length);
                assertEquals(actualSize, longres);
                assertEquals(offset + actualSize, longer.position());
                assertEquals(longer.capacity(), longer.limit());
                assertEquals(10, longer.remaining());

                // compare encodings

                // first reset buffer positions for reading.
                exact.position(offset);
                longer.position(offset);
                assertEquals(actualSize, longer.mismatch(exact));
                assertEquals(actualSize, exact.mismatch(longer));

                // decode with a buffer that is missing the last
                // byte...
                var shortSlice = exact.duplicate();
                shortSlice.position(offset);
                shortSlice.limit(offset + actualSize -1);
                var actualLength = VariableLengthEncoder.decode(shortSlice);
                assertEquals(-1L, actualLength);
                assertEquals(offset, shortSlice.position());
                assertEquals(offset + actualSize - 1, shortSlice.limit());

                // decode with the exact buffer
                actualLength = VariableLengthEncoder.decode(exact);
                assertEquals(length, actualLength);
                assertEquals(offset + actualSize, exact.position());
                assertFalse(exact.hasRemaining());

                // decode with the longer buffer
                actualLength = VariableLengthEncoder.decode(longer);
                assertEquals(length, actualLength);
                assertEquals(offset + actualSize, longer.position());
                assertEquals(10, longer.remaining());
            }

        }
    }

    // Encode the given length and then peeks it and compares
    // the results, asserting various invariants along the way.
    @ParameterizedTest
    @MethodSource("prefixInvariants")
    public void testEncodePeek(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getEncodedSize(length));
            assertThrows(exception, () -> VariableLengthEncoder.encode(ByteBuffer.allocate(16), length));
            return;
        }

        var actualSize = VariableLengthEncoder.getEncodedSize(length);
        assertEquals(1 << expectedPrefix, actualSize);
        assertTrue(actualSize > 0, "length is negative or zero: " + actualSize);
        assertTrue(actualSize < 9, "length is too big: " + actualSize);

        // Use different offsets for the position at which to encode/decode
        for (int offset : List.of(0, 10)) {
            System.out.printf("Encode/Peek %s on %s bytes with offset %s%n",
                    length, actualSize, offset);

            // allocate buffers: one exact, one too long
            ByteBuffer exact = ByteBuffer.allocate(actualSize + offset);
            exact.position(offset);
            ByteBuffer longer = ByteBuffer.allocate(actualSize + 10 + offset);
            longer.position(offset);

            // attempt to encode with a buffer that has the exact size
            var exactres = VariableLengthEncoder.encode(exact, length);
            assertEquals(actualSize, exactres);
            assertEquals(actualSize + offset, exact.position());
            assertFalse(exact.hasRemaining());

            // attempt to encode with a buffer that has more bytes
            var longres = VariableLengthEncoder.encode(longer, length);
            assertEquals(actualSize, longres);
            assertEquals(offset + actualSize, longer.position());
            assertEquals(longer.capacity(), longer.limit());
            assertEquals(10, longer.remaining());

            // compare encodings

            // first reset buffer positions for reading.
            exact.position(offset);
            longer.position(offset);
            assertEquals(actualSize, longer.mismatch(exact));
            assertEquals(actualSize, exact.mismatch(longer));
            exact.position(0);
            longer.position(0);
            exact.limit(exact.capacity());
            longer.limit(longer.capacity());

            // decode with a buffer that is missing the last
            // byte...
            var shortSlice = exact.duplicate();
            shortSlice.position(0);
            shortSlice.limit(offset + actualSize - 1);
            // need at least one byte to decode the size len...
            var expectedSize = shortSlice.limit() <= offset ? -1 : actualSize;
            assertEquals(expectedSize, VariableLengthEncoder.peekEncodedValueSize(shortSlice, offset));
            var actualLength = VariableLengthEncoder.peekEncodedValue(shortSlice, offset);
            assertEquals(-1L, actualLength);
            assertEquals(0, shortSlice.position());
            assertEquals(offset + actualSize - 1, shortSlice.limit());

            // decode with the exact buffer
            assertEquals(actualSize, VariableLengthEncoder.peekEncodedValueSize(exact, offset));
            actualLength = VariableLengthEncoder.peekEncodedValue(exact, offset);
            assertEquals(length, actualLength);
            assertEquals(0, exact.position());
            assertEquals(exact.capacity(), exact.limit());

            // decode with the longer buffer
            assertEquals(actualSize, VariableLengthEncoder.peekEncodedValueSize(longer, offset));
            actualLength = VariableLengthEncoder.peekEncodedValue(longer, offset);
            assertEquals(length, actualLength);
            assertEquals(0, longer.position());
            assertEquals(longer.capacity(), longer.limit());
        }

    }


    private ByteBuffer getTestBuffer(long length, int capacity) {
        return switch (capacity) {
            case 0 -> ByteBuffer.allocate(1).put((byte) length);
            case 1 -> ByteBuffer.allocate(capacity).put((byte) length);
            case 2 -> ByteBuffer.allocate(capacity).putShort((short) length);
            case 4 -> ByteBuffer.allocate(capacity).putInt((int) length);
            case 8 -> ByteBuffer.allocate(capacity).putLong(length);
            default -> throw new SkippedException("bad value used for capacity");
        };
    }
}
