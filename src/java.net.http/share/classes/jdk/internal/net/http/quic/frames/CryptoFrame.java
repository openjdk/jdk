/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.frames;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * A CRYPTO Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class CryptoFrame extends QuicFrame {

    private final long offset;
    private final int length;
    private final ByteBuffer cryptoData;

    CryptoFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(CRYPTO);
        offset = decodeVLField(buffer, "offset");
        length = decodeVLFieldAsInt(buffer, "length");
        if (offset + length > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new QuicTransportException("Maximum crypto offset exceeded",
                    null, type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length, type);
        int pos = buffer.position();
        // The buffer is the datagram: we will make a copy if the datagram
        // is larger than the crypto frame by 64 bytes.
        cryptoData = Utils.sliceOrCopy(buffer, pos, length, 64);
        buffer.position(pos + length);
    }

    /**
     * Creates CryptoFrame
     */
    public CryptoFrame(long offset, int length, ByteBuffer cryptoData) {
        this(offset, length, cryptoData, true);
    }

    private CryptoFrame(long offset, int length, ByteBuffer cryptoData, boolean slice)
    {
        super(CRYPTO);
        this.offset = requireVLRange(offset, "offset");
        if (length != cryptoData.remaining())
            throw new IllegalArgumentException("bad length: " + length);
        this.length = length;
        this.cryptoData = slice
                ? cryptoData.slice(cryptoData.position(), length)
                : cryptoData;
    }

    /**
     * Creates a new CryptoFrame which is a slice of this crypto frame.
     * @param offset the new offset
     * @param length the new length
     * @return a slice of the current crypto frame
     * @throws IndexOutOfBoundsException if the offset or length
     * exceed the bounds of this crypto frame
     */
    public CryptoFrame slice(long offset, int length) {
        long offsetdiff = offset - offset();
        long oldlen = length();
        Objects.checkFromIndexSize(offsetdiff, length, oldlen);
        int pos = cryptoData.position();
        // safe cast to int since offsetdiff < length
        int newpos = Math.addExact(pos, (int)offsetdiff);
        ByteBuffer slice = Utils.sliceOrCopy(cryptoData, newpos, length);
        return new CryptoFrame(offset, length, slice, false);
    }

    @Override
    public void encode(ByteBuffer dest) {
        if (size() > dest.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = dest.position();
        encodeVLField(dest, CRYPTO, "type");
        encodeVLField(dest, offset, "offset");
        encodeVLField(dest, length, "length");
        assert cryptoData.remaining() == length;
        putByteBuffer(dest, cryptoData);
        assert dest.position() - pos == size();
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(CRYPTO)
                + getVLFieldLengthFor(offset)
                + getVLFieldLengthFor(length)
                + length;
    }

    /**
     * {@return the frame offset}
     */
    public long offset() {
        return offset;
    }

    public int length() {
        return length;
    }

    /**
     * {@return the frame payload}
     */
    public ByteBuffer payload() {
        return cryptoData.slice();
    }

    @Override
    public String toString() {
        return "CryptoFrame(" +
                "offset=" + offset +
                ", length=" + length +
                ')';
    }

    public static int compareOffsets(CryptoFrame cf1, CryptoFrame cf2) {
        return Long.compare(cf1.offset, cf2.offset);
    }
}
