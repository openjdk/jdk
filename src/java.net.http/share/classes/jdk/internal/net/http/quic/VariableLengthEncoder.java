/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.internal.net.http.quic;

import java.nio.ByteBuffer;

/**
 *  QUIC packets and frames commonly use a variable-length encoding for
 *  non-negative values. This encoding ensures that smaller values will use less
 *  in the packet or frame.
 *
 *  <p>The QUIC variable-length encoding reserves the two most significant bits
 *  of the first byte to encode the size of the length value as a base 2 logarithm
 *  value. The length itself is then encoded on the remaining bits, in network
 *  byte order. This means that the length values will be encoded on 1, 2, 4, or
 *  8 bytes and can encode 6-, 14-, 30-, or 62-bit values
 *  respectively, or a value within the range of 0 to 4611686018427387903
 *  inclusive.
 *
 * @spec https://www.rfc-editor.org/rfc/rfc9000.html#integer-encoding
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public class VariableLengthEncoder {

    /**
     * The maximum number of bytes on which a variable length
     * integer can be encoded.
     */
    public static final int MAX_INTEGER_LENGTH = 8;

    /**
     * The maximum value a variable length integer can
     * take.
     */
    public static final long MAX_ENCODED_INTEGER = (1L << 62) - 1;

    static {
        assert MAX_ENCODED_INTEGER == 4611686018427387903L;
    }

    private VariableLengthEncoder() {
        throw new InternalError("should not come here");
    }

    /**
     * Decode a variable length value from {@code ByteBuffer}. This method assumes that the
     * position of {@code buffer} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last byte read.
     *
     * @param buffer the {@code ByteBuffer} that the length will be decoded from
     *
     * @return the value. If an error occurs, {@code -1} is returned and
     *         the buffer position is left unchanged.
     */
    public static long decode(ByteBuffer buffer) {
        return decode(BuffersReader.single(buffer));
    }

    /**
     * Decode a variable length value from {@code BuffersReader}. This method assumes that the
     * position of {@code buffers} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last byte.
     *
     * @param buffers the {@code BuffersReader} that the length will be decoded from
     *
     * @return the value. If an error occurs, {@code -1} is returned and
     *         the buffer position is left unchanged.
     */
    public static long decode(BuffersReader buffers) {
        if (!buffers.hasRemaining())
            return -1;

        long pos = buffers.position();
        int lenByte = buffers.get(pos) & 0xFF;
        pos++;
        // read size of length from leading two bits
        int prefix = lenByte >> 6;
        int len = 1 << prefix;
        // retrieve remaining bits that constitute the length
        long result = lenByte & 0x3F;
        long idx = 0, lim = buffers.limit();
        if (lim - pos < len - 1) return -1;
        while (idx++ < len - 1) {
            assert pos < lim;
            result = ((result << Byte.SIZE) + (buffers.get(pos) & 0xFF));
            pos++;
        }
        // Set position of ByteBuffer to next byte following length
        assert pos == buffers.position() + len;
        assert pos <= buffers.limit();
        buffers.position(pos);

        assert (result >= 0) && (result < (1L << 62));
        return result;
    }

    /**
     * Encode (a variable length) value into {@code ByteBuffer}. This method assumes that the
     * position of {@code buffer} has been set to the first byte where the length
     * begins. If the methods completes successfully, the position will be set
     * to the byte after the last length byte.
     *
     * @param buffer the {@code ByteBuffer} that the length will be encoded into
     * @param value the variable length value
     *
     * @throws IllegalArgumentException
     *    if value supplied falls outside of acceptable bounds  [0, 2^62-1],
     *    or if the given buffer doesn't contain enough space to encode the
     *    value
     *
     * @return the {@code position} of the buffer
     */
    public static int encode(ByteBuffer buffer, long value) throws IllegalArgumentException {
        // check for valid parameters
        if (value < 0 || value > MAX_ENCODED_INTEGER)
            throw new IllegalArgumentException(
                    "value supplied falls outside of acceptable bounds");
        if (!buffer.hasRemaining())
            throw new IllegalArgumentException(
                    "buffer does not contain enough bytes to store length");

        // set length prefix to indicate size of length
        int lengthPrefix = getVariableLengthPrefix(value);
        assert lengthPrefix >= 0 && lengthPrefix <= 3;
        lengthPrefix <<= (Byte.SIZE - 2);

        int lengthSize = getEncodedSize(value);
        assert lengthSize > 0;
        assert lengthSize <= 8;

        var limit = buffer.limit();
        var pos = buffer.position();

        // check that it's possible to add length to buffer
        if (lengthSize > limit - pos)
            throw new IllegalArgumentException("buffer does not contain enough bytes to store length");

        // create mask to use in isolating byte to transfer to buffer
        long mask = 255L << (Byte.SIZE * (lengthSize - 1));
        // convert length to bytes and add to buffer
        boolean isFirstByte = true;
        for (int i = lengthSize; i > 0; i--) {
            assert buffer.hasRemaining() : "no space left at " + (lengthSize - i);
            assert mask != 0;
            assert mask == (255L << ((i - 1) * 8))
                    : "mask: %x, expected %x".formatted(mask, (255L << ((i - 1) * 8)));

            long b = value & mask;
            for (int j = i - 1; j > 0; j--) {
                b >>= Byte.SIZE;
            }

            assert b == (value & mask) >> (8 * (i - 1));

            if (isFirstByte) {
                assert (b & 0xC0) == 0;
                buffer.put((byte) (b | lengthPrefix));
                isFirstByte = false;
            } else {
                buffer.put((byte) b);
            }
            // move mask over to next byte - avoid carrying sign bit
            mask = (mask >>> Byte.SIZE);
        }
        var bytes = buffer.position() - pos;
        assert bytes == lengthSize;
        return lengthSize;
    }

    /**
     * Returns the variable length prefix.
     * The variable length prefix is the base 2 logarithm of
     * the number of bytes required to encode
     * a positive value as a variable length integer:
     * [0, 1, 2, 3] for [1, 2, 4, 8] bytes.
     *
     * @param value the value to encode
     *
     * @throws IllegalArgumentException
     *    if the supplied value falls outside the acceptable bounds [0, 2^62-1]
     *
     * @return the base 2 logarithm of the number of bytes required to encode
     *         the value as a variable length integer.
     */
    public static int getVariableLengthPrefix(long value) throws IllegalArgumentException {
        if ((value > MAX_ENCODED_INTEGER) || (value < 0))
            throw new IllegalArgumentException("invalid length");

        int lengthPrefix;
        if (value > (1L << 30) - 1)
            lengthPrefix = 3; // 8 bytes
        else if (value > (1L << 14) - 1)
            lengthPrefix = 2; // 4 bytes
        else if (value > (1L << 6) - 1)
            lengthPrefix = 1; // 2 bytes
        else
            lengthPrefix = 0; // 1 byte

        return lengthPrefix;
    }

    /**
     * Returns the number of bytes needed to encode
     * the given value as a variable length integer.
     * This a number between 1 and 8.
     *
     * @param value the value to encode
     *
     * @return the number of bytes needed to encode
     *         the given value as a variable length integer.
     *
     * @throws IllegalArgumentException
     *   if the value supplied falls outside of acceptable bounds [0, 2^62-1]
     */
    public static int getEncodedSize(long value) throws IllegalArgumentException {
        if (value < 0 || value > MAX_ENCODED_INTEGER)
            throw new IllegalArgumentException("invalid variable length integer: " + value);
        return 1 << getVariableLengthPrefix(value);
    }

    /**
     * Peeks at a variable length value encoded at the given offset.
     * If the byte buffer doesn't contain enough bytes to read the
     * variable length value, -1 is returned.
     *
     * <p>This method doesn't advance the buffer position.
     *
     * @param buffer the buffer to read from
     * @param offset the offset in the buffer to start reading from
     *
     * @return the variable length value encoded at the given offset, or -1
     */
    public static long peekEncodedValue(ByteBuffer buffer, int offset) {
        return peekEncodedValue(BuffersReader.single(buffer), offset);
    }

    /**
     * Peeks at a variable length value encoded at the given offset.
     * If the byte buffer doesn't contain enough bytes to read the
     * variable length value, -1 is returned.
     *
     * This method doesn't advance the buffer position.
     *
     * @param buffers the buffer to read from
     * @param offset the offset in the buffer to start reading from
     *
     * @return the variable length value encoded at the given offset, or -1
     */
    public static long peekEncodedValue(BuffersReader buffers, long offset) {

        // figure out on how many bytes the length is encoded.
        int size = peekEncodedValueSize(buffers, offset);
        if (size <= 0) return -1L;
        assert size > 0 && size <= 8;

        // check that we have enough bytes in the buffer
        long limit = buffers.limit();
        long pos = offset;
        if (limit - size < pos) return -1L;

        // peek at the variable length:
        //  - read first byte
        int first = buffers.get(pos++);
        long res = first & 0x3F;
        if (size == 1) return res;

        // - read the rest of the bytes
        size -= 1;
        assert size > 0;
        for (int i=0 ; i < size; i++) {
            if (limit <= pos) return -1L;
            res = (res << 8) | (long) (buffers.get(pos++) & 0xFF);
        }
        return res;
    }

    /**
     * Peeks at a variable length value encoded at the given offset,
     * and return the number of bytes on which this value is encoded.
     * If the byte buffer is empty or the offset is past
     * the limit -1 is returned.
     * This method doesn't advance the buffer position.
     *
     * @param buffer the buffer to read from
     * @param offset the offset in the buffer to start reading from
     *
     * @return the number of bytes on which the variable length
     *         value is encoded at the given offset, or -1
     */
    public static int peekEncodedValueSize(ByteBuffer buffer, int offset) {
        return peekEncodedValueSize(BuffersReader.single(buffer), offset);
    }

    /**
     * Peeks at a variable length value encoded at the given offset,
     * and return the number of bytes on which this value is encoded.
     * If the byte buffer is empty or the offset is past
     * the limit -1 is returned.
     * This method doesn't advance the buffer position.
     *
     * @param buffers the buffers to read from
     * @param offset the offset in the buffer to start reading from
     *
     * @return the number of bytes on which the variable length
     *         value is encoded at the given offset, or -1
     */
    public static int peekEncodedValueSize(BuffersReader buffers, long offset) {
        long limit = buffers.limit();
        long pos = offset;
        if (limit <= pos) return -1;
        int first = buffers.get(pos);
        int prefix = (first & 0xC0) >>> 6;
        int size = 1 << prefix;
        assert size > 0 && size <= 8;
        return size;
    }
}
