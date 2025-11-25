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

import jdk.internal.net.quic.QuicTransportException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * A RESET_STREAM Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class ResetStreamFrame extends QuicFrame {

    private final long streamID;
    private final long errorCode;
    private final long finalSize;

    ResetStreamFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(RESET_STREAM);
        streamID = decodeVLField(buffer, "streamID");
        errorCode = decodeVLField(buffer, "errorCode");
        finalSize = decodeVLField(buffer, "finalSize");
    }

    /**
     */
    public ResetStreamFrame(
        long streamID,
        long errorCode,
        long finalSize)
    {
        super(RESET_STREAM);
        this.streamID = requireVLRange(streamID, "streamID");
        this.errorCode = requireVLRange(errorCode, "errorCode");
        this.finalSize = requireVLRange(finalSize, "finalSize");
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, RESET_STREAM, "type");
        encodeVLField(buffer, streamID, "streamID");
        encodeVLField(buffer, errorCode, "errorCode");
        encodeVLField(buffer, finalSize, "finalSize");
        assert buffer.position() - pos == size();
    }

    /**
     */
    public long streamId() {
        return streamID;
    }

    /**
     */
    public long errorCode() {
        return errorCode;
    }

    /**
     */
    public long finalSize() {
        return finalSize;
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(RESET_STREAM)
                + getVLFieldLengthFor(streamID)
                + getVLFieldLengthFor(errorCode)
                + getVLFieldLengthFor(finalSize);
    }

    @Override
    public String toString() {
        return "ResetStreamFrame(stream=" + streamID +
                ", errorCode=" + errorCode +
                ", finalSize=" + finalSize + ')';
    }
}
