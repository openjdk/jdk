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
package jdk.internal.net.http.quic.frames;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

/**
 * A NEW_TOKEN frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class NewTokenFrame extends QuicFrame {
    private final byte[] token;

    /**
     * Incoming NEW_TOKEN frame
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    NewTokenFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(NEW_TOKEN);
        int length = decodeVLFieldAsInt(buffer, "token length");
        if (length == 0) {
            throw new QuicTransportException("Empty token",
                    null, type,
                    QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length, type);
        final byte[] t = new byte[length];
        buffer.get(t);
        this.token = t;
    }

    /**
     * Outgoing NEW_TOKEN frame whose token is the given ByteBuffer
     * (position to limit)
     */
    public NewTokenFrame(final ByteBuffer tokenBuf) {
        super(NEW_TOKEN);
        Objects.requireNonNull(tokenBuf);
        final int length = tokenBuf.remaining();
        if (length <= 0) {
            throw new IllegalArgumentException("Invalid token length");
        }
        final byte[] t = new byte[length];
        tokenBuf.get(t);
        this.token = t;
    }

    @Override
    public void encode(final ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, NEW_TOKEN, "type");
        encodeVLField(buffer, token.length, "token length");
        buffer.put(token);
        assert buffer.position() - pos == size();
    }

    public byte[] token() {
        return this.token;
    }

    @Override
    public int size() {
        final int tokenLength = token.length;
        return getVLFieldLengthFor(NEW_TOKEN)
                + getVLFieldLengthFor(tokenLength)
                + tokenLength;
    }
}
