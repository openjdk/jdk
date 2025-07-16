/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.expectThrows;

/*
 * @test
 * @library /test/lib
 * @modules java.net.http/jdk.internal.net.http.quic
 * @run testng/othervm VariableLengthTest
 * @summary Tests to check quic/util methods encode/decodeVariableLength methods
 *  work as expected.
 */
public class VariableLengthTest {
    static final Class<? extends Throwable> IAE = IllegalArgumentException.class;

    @DataProvider(name = "decode invariants")
    public Object[][] decodeInvariants() {
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
    @DataProvider(name = "encode invariants")
    public Object[][] encodeInvariants() {
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
    @DataProvider(name = "prefix invariants")
    public Object[][] prefixInvariants() {
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

    @Test(dataProvider = "decode invariants")
    public void testDecode(byte[] values, long expectedLength, int expectedPosition) {
        ByteBuffer bb = ByteBuffer.wrap(values);
        var actualLength = VariableLengthEncoder.decode(bb);
        assertEquals(actualLength, expectedLength);

        var actualPosition = bb.position();
        assertEquals(actualPosition, expectedPosition);
    }

    @Test(dataProvider = "decode invariants")
    public void testPeek(byte[] values, long expectedLength, int expectedPosition) {
        ByteBuffer bb = ByteBuffer.wrap(values);
        var actualLength = VariableLengthEncoder.peekEncodedValue(bb, 0);
        assertEquals(actualLength, expectedLength);

        var actualPosition = bb.position();
        assertEquals(actualPosition, 0);
    }

    @Test(dataProvider = "encode invariants")
    public void testEncode(long length, int capacity, Class<? extends Exception> exception) throws IOException {
        var actualBuffer = ByteBuffer.allocate(capacity);
        var expectedBuffer = getTestBuffer(length, capacity);

        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.encode(actualBuffer, length));
            // if method fails ensure that position hasn't changed
            var actualPosition = actualBuffer.position();
            assertEquals(actualPosition, capacity);
        } else {
            VariableLengthEncoder.encode(actualBuffer, length);
            var actualPosition = actualBuffer.position();
            assertEquals(actualPosition, capacity);

            // check length prefix
            int firstByte = actualBuffer.get(0) & 0xFF;
            int lengthPrefix = firstByte & 0xC0;
            lengthPrefix >>= 6;
            int expectedValue = (int)(Math.log(capacity) / Math.log(2));
            assertEquals(lengthPrefix, expectedValue);

            // check length encoded in buffer correctly
            int b  = firstByte & 0x3F;
            actualBuffer.put(0, (byte) b);
            assertEquals(actualBuffer.compareTo(expectedBuffer), 0);
        }
    }

    @Test(dataProvider = "prefix invariants")
    public void testLengthPrefix(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getVariableLengthPrefix(length));
        } else {
            var actualValue = VariableLengthEncoder.getVariableLengthPrefix(length);
            assertEquals(actualValue, expectedPrefix);
        }
    }

    // Encode the given length and then decodes it and compares
    // the results, asserting various invariants along the way.
    @Test(dataProvider = "prefix invariants")
    public void testEncodeDecode(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getEncodedSize(length));
            assertThrows(exception, () -> VariableLengthEncoder.encode(ByteBuffer.allocate(16), length));
        } else {
            var actualSize = VariableLengthEncoder.getEncodedSize(length);
            assertEquals(actualSize, 1 << expectedPrefix);
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
                expectThrows(IAE, () -> VariableLengthEncoder.encode(shorter, length));
                assertEquals(shorter.position(), offset);
                assertEquals(shorter.limit(), shorter.capacity());

                assertEquals(shorter.mismatch(shorterref), -1);
                assertEquals(shorterref.mismatch(shorter), -1);

                // attempt to encode with a buffer that has the exact size
                var exactres = VariableLengthEncoder.encode(exact, length);
                assertEquals(exactres, actualSize);
                assertEquals(exact.position(), actualSize + offset);
                assertFalse(exact.hasRemaining());

                // attempt to encode with a buffer that has more bytes
                var longres = VariableLengthEncoder.encode(longer, length);
                assertEquals(longres, actualSize);
                assertEquals(longer.position(), offset + actualSize);
                assertEquals(longer.limit(), longer.capacity());
                assertEquals(longer.remaining(), 10);

                // compare encodings

                // first reset buffer positions for reading.
                exact.position(offset);
                longer.position(offset);
                assertEquals(longer.mismatch(exact), actualSize);
                assertEquals(exact.mismatch(longer), actualSize);

                // decode with a buffer that is missing the last
                // byte...
                var shortSlice = exact.duplicate();
                shortSlice.position(offset);
                shortSlice.limit(offset + actualSize -1);
                var actualLength = VariableLengthEncoder.decode(shortSlice);
                assertEquals(actualLength, -1L);
                assertEquals(shortSlice.position(), offset);
                assertEquals(shortSlice.limit(), offset + actualSize - 1);

                // decode with the exact buffer
                actualLength = VariableLengthEncoder.decode(exact);
                assertEquals(actualLength, length);
                assertEquals(exact.position(), offset + actualSize);
                assertFalse(exact.hasRemaining());

                // decode with the longer buffer
                actualLength = VariableLengthEncoder.decode(longer);
                assertEquals(actualLength, length);
                assertEquals(longer.position(), offset + actualSize);
                assertEquals(longer.remaining(), 10);
            }

        }
    }

    // Encode the given length and then peeks it and compares
    // the results, asserting various invariants along the way.
    @Test(dataProvider = "prefix invariants")
    public void testEncodePeek(long length, int expectedPrefix,  Class<? extends Exception> exception) {
        if (exception != null) {
            assertThrows(exception, () -> VariableLengthEncoder.getEncodedSize(length));
            assertThrows(exception, () -> VariableLengthEncoder.encode(ByteBuffer.allocate(16), length));
            return;
        }

        var actualSize = VariableLengthEncoder.getEncodedSize(length);
        assertEquals(actualSize, 1 << expectedPrefix);
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
            assertEquals(exactres, actualSize);
            assertEquals(exact.position(), actualSize + offset);
            assertFalse(exact.hasRemaining());

            // attempt to encode with a buffer that has more bytes
            var longres = VariableLengthEncoder.encode(longer, length);
            assertEquals(longres, actualSize);
            assertEquals(longer.position(), offset + actualSize);
            assertEquals(longer.limit(), longer.capacity());
            assertEquals(longer.remaining(), 10);

            // compare encodings

            // first reset buffer positions for reading.
            exact.position(offset);
            longer.position(offset);
            assertEquals(longer.mismatch(exact), actualSize);
            assertEquals(exact.mismatch(longer), actualSize);
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
            assertEquals(VariableLengthEncoder.peekEncodedValueSize(shortSlice, offset), expectedSize);
            var actualLength = VariableLengthEncoder.peekEncodedValue(shortSlice, offset);
            assertEquals(actualLength, -1L);
            assertEquals(shortSlice.position(), 0);
            assertEquals(shortSlice.limit(), offset + actualSize - 1);

            // decode with the exact buffer
            assertEquals(VariableLengthEncoder.peekEncodedValueSize(exact, offset), actualSize);
            actualLength = VariableLengthEncoder.peekEncodedValue(exact, offset);
            assertEquals(actualLength, length);
            assertEquals(exact.position(), 0);
            assertEquals(exact.limit(), exact.capacity());

            // decode with the longer buffer
            assertEquals(VariableLengthEncoder.peekEncodedValueSize(longer, offset), actualSize);
            actualLength = VariableLengthEncoder.peekEncodedValue(longer, offset);
            assertEquals(actualLength, length);
            assertEquals(longer.position(), 0);
            assertEquals(longer.limit(), longer.capacity());
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
