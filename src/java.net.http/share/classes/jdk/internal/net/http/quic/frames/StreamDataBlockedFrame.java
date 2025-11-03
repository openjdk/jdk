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
 * A STREAM_DATA_BLOCKED Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class StreamDataBlockedFrame extends QuicFrame {

    private final long streamId;
    private final long maxStreamData;

    /**
     * Incoming STREAM_DATA_BLOCKED frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    StreamDataBlockedFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STREAM_DATA_BLOCKED);
        assert type == STREAM_DATA_BLOCKED : "STREAM_DATA_BLOCKED, unexpected frame type 0x"
                + Integer.toHexString(type);
        streamId = decodeVLField(buffer, "streamID");
        maxStreamData = decodeVLField(buffer, "maxData");
    }

    /**
     * Outgoing STREAM_DATA_BLOCKED frame
     */
    public StreamDataBlockedFrame(long streamId, long maxStreamData) {
        super(STREAM_DATA_BLOCKED);
        this.streamId = requireVLRange(streamId, "streamID");
        this.maxStreamData = requireVLRange(maxStreamData, "maxStreamData");
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, STREAM_DATA_BLOCKED, "type");
        encodeVLField(buffer, streamId, "streamID");
        encodeVLField(buffer, maxStreamData, "maxStreamData");
        assert buffer.position() - pos == size();
    }

    /**
     */
    public long maxStreamData() {
        return maxStreamData;
    }

    public long streamId() {
        return streamId;
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(STREAM_DATA_BLOCKED)
                + getVLFieldLengthFor(streamId)
                + getVLFieldLengthFor(maxStreamData);
    }

    @Override
    public String toString() {
        return "StreamDataBlockedFrame(" +
                "streamId=" + streamId +
                ", maxStreamData=" + maxStreamData +
                ')';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StreamDataBlockedFrame that)) return false;
        if (streamId != that.streamId) return false;
        return maxStreamData == that.maxStreamData;
    }

    @Override
    public int hashCode() {
        int result = (int) (streamId ^ (streamId >>> 32));
        result = 31 * result + (int) (maxStreamData ^ (maxStreamData >>> 32));
        return result;
    }
}
