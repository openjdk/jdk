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

import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;

/**
 * A STREAMS_BLOCKED Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class StreamsBlockedFrame extends QuicFrame {

    private final long maxStreams;
    private final boolean bidi;

    /**
     * Incoming STREAMS_BLOCKED frame returned by QuicFrame.decode()
     *
     * @param buffer
     * @param type
     * @throws QuicTransportException if the frame was malformed
     */
    StreamsBlockedFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STREAMS_BLOCKED);
        bidi = (type == STREAMS_BLOCKED);
        maxStreams = decodeVLField(buffer, "maxStreams");
        if (maxStreams > MaxStreamsFrame.MAX_VALUE) {
            throw new QuicTransportException("Invalid maximum streams",
                    null, type, QuicTransportErrors.FRAME_ENCODING_ERROR);
        }
    }

    /**
     * Outgoing STREAMS_BLOCKED frame
     */
    public StreamsBlockedFrame(boolean bidi, long maxStreams) {
        super(STREAMS_BLOCKED);
        this.bidi = bidi;
        this.maxStreams = requireVLRange(maxStreams, "maxStreams");
    }

    @Override
    public long getTypeField() {
        return STREAMS_BLOCKED + (bidi?0:1);
    }

    @Override
    public void encode(ByteBuffer buffer) {
        if (size() > buffer.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = buffer.position();
        encodeVLField(buffer, getTypeField(), "type");
        encodeVLField(buffer, maxStreams, "maxStreams");
        assert buffer.position() - pos == size();
    }

    @Override
    public int size() {
        return getVLFieldLengthFor(STREAMS_BLOCKED)
                + getVLFieldLengthFor(maxStreams);
    }

    /**
     */
    public long maxStreams() {
        return maxStreams;
    }

    public boolean isBidi() {
        return bidi;
    }

    @Override
    public String toString() {
        return "StreamsBlockedFrame(bidi=" + bidi +
                ", maxStreams=" + maxStreams + ')';
    }
}
