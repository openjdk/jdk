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

import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * A NEW_CONNECTION_ID Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class NewConnectionIDFrame extends QuicFrame {

    private final long sequenceNumber;
    private final long retirePriorTo;
    private final ByteBuffer connectionId;
    private final ByteBuffer statelessResetToken;

    /**
     * Incoming NEW_CONNECTION_ID frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    NewConnectionIDFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(NEW_CONNECTION_ID);
        sequenceNumber = decodeVLField(buffer, "sequenceNumber");
        retirePriorTo = decodeVLField(buffer, "retirePriorTo");
        if (retirePriorTo > sequenceNumber) {
            throw new QuicTransportException("Invalid retirePriorTo",
                    null, type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, 17, type);
        int length = Byte.toUnsignedInt(buffer.get());
        if (length < 1 || length > 20) {
            throw new QuicTransportException("Invalid connection ID",
                    null, type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
        validateRemainingLength(buffer, length + 16, type);
        int position = buffer.position();
        connectionId = buffer.slice(position, length);
        position += length;
        statelessResetToken = buffer.slice(position, 16);
        position += 16;
        buffer.position(position);
    }

    /**
     * Outgoing NEW_CONNECTION_ID frame
     */
    public NewConnectionIDFrame(long sequenceNumber, long retirePriorTo, ByteBuffer connectionId, ByteBuffer statelessResetToken) {
        super(NEW_CONNECTION_ID);
        this.sequenceNumber = requireVLRange(sequenceNumber, "sequenceNumber");
        this.retirePriorTo = requireVLRange(retirePriorTo, "retirePriorTo");
        int length = connectionId.remaining();
        if (length < 1 || length > 20)
            throw new IllegalArgumentException("invalid length");
        this.connectionId = connectionId.slice();
        if (statelessResetToken.remaining() != 16)
            throw new IllegalArgumentException("stateless reset token must be 16 bytes");
        this.statelessResetToken = statelessResetToken.slice();
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, NEW_CONNECTION_ID, "type");
        encodeVLField(buffer, sequenceNumber, "sequenceNumber");
        encodeVLField(buffer, retirePriorTo, "retirePriorTo");
        int length = connectionId.remaining();
        buffer.put((byte)length);
        putByteBuffer(buffer, connectionId);
        putByteBuffer(buffer, statelessResetToken);
        assert buffer.position() - pos == size();
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(NEW_CONNECTION_ID)
                + getVLFieldLengthFor(sequenceNumber)
                + getVLFieldLengthFor(retirePriorTo)
                + 1 // connection length
                + connectionId.remaining()
                + statelessResetToken.remaining();
    }

    public long sequenceNumber() {
        return sequenceNumber;
    }

    public long retirePriorTo() {
        return retirePriorTo;
    }

    public ByteBuffer connectionId() {
        return connectionId;
    }

    public ByteBuffer statelessResetToken() {
        return statelessResetToken;
    }

    @Override
    public String toString() {
        return "NewConnectionIDFrame(seqNumber=" + sequenceNumber
                + ", retirePriorTo=" + retirePriorTo
                + ", connIdLength=" + connectionId.remaining()
                + ")";
    }
}
