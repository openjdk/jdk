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
 * A MAX_STREAM_DATA Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class MaxStreamDataFrame extends QuicFrame {

    private final long streamID;
    private final long maxStreamData;

    /**
     * Incoming MAX_STREAM_DATA frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    MaxStreamDataFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(MAX_STREAM_DATA);
        streamID = decodeVLField(buffer, "streamID");
        maxStreamData = decodeVLField(buffer, "maxData");
    }

    /**
     * Outgoing MAX_STREAM_DATA frame
     */
    public MaxStreamDataFrame(long streamID, long maxStreamData) {
        super(MAX_STREAM_DATA);
        this.streamID = requireVLRange(streamID, "streamID");
        this.maxStreamData = requireVLRange(maxStreamData, "maxStreamData");
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, MAX_STREAM_DATA, "type");
        encodeVLField(buffer, streamID, "streamID");
        encodeVLField(buffer, maxStreamData, "maxStreamData");
        assert buffer.position() - pos == size();
    }

    /**
     */
    public long maxStreamData() {
        return maxStreamData;
    }

    public long streamID() {
        return streamID;
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(MAX_STREAM_DATA)
                + getVLFieldLengthFor(streamID)
                + getVLFieldLengthFor(maxStreamData);
    }

    @Override
    public String toString() {
        return "MaxStreamDataFrame(" +
                "streamId=" + streamID +
                ", maxStreamData=" + maxStreamData +
                ')';
    }
}
