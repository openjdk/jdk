/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.http3.frames;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import jdk.internal.net.http.common.Logger;
import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * Represents a GOAWAY HTTP3 frame
 */
public final class GoAwayFrame extends AbstractHttp3Frame {

    public static final int TYPE = Http3FrameType.TYPE.GOAWAY_FRAME;
    private final long length;
    // represents either a stream id or a push id depending on the context
    // of the frame
    private final long id;

    public GoAwayFrame(final long id) {
        super(TYPE);
        this.id = id;
        // the payload length of this frame
        this.length = VariableLengthEncoder.getEncodedSize(this.id);
    }

    // only used when constructing the frame during decoding content over a stream
    private GoAwayFrame(final long length, final long id) {
        super(Http3FrameType.GOAWAY.type());
        this.length = length;
        this.id = id;
    }

    @Override
    public long length() {
        return this.length;
    }

    /**
     * {@return the id of either the stream or a push promise, depending on the context
     * of this frame}
     */
    public long getTargetId() {
        return this.id;
    }

    public void writeFrame(final ByteBuffer buf) {
        // write the type of the frame
        VariableLengthEncoder.encode(buf, this.type);
        // write the length of the payload
        VariableLengthEncoder.encode(buf, this.length);
        // write the stream id/push id
        VariableLengthEncoder.encode(buf, this.id);
    }

    static AbstractHttp3Frame decodeFrame(final BuffersReader reader, final Logger debug) {
        final long position = reader.position();
        // read the frame type
        decodeRequiredType(reader, Http3FrameType.GOAWAY.type());
        // read length of the payload
        final long length = VariableLengthEncoder.decode(reader);
        if (length < 0 || length > reader.remaining()) {
            reader.position(position);
            throw new BufferUnderflowException();
        }
        // position before reading payload
        long start = reader.position();

        if (length == 0 || length != VariableLengthEncoder.peekEncodedValueSize(reader, start)) {
            // frame length does not match the enclosed targetId
            return new MalformedFrame(TYPE,
                    Http3Error.H3_FRAME_ERROR.code(),
                    "Invalid length in GOAWAY frame: " + length);
        }

        // read stream id / push id
        final long targetId = VariableLengthEncoder.decode(reader);
        if (targetId == -1) {
            reader.position(position);
            throw new BufferUnderflowException();
        }

        // check position after reading payload
        var malformed = checkPayloadSize(TYPE, reader, start, length);
        if (malformed != null) return malformed;

        reader.release();
        return new GoAwayFrame(length, targetId);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.toString()).append(" stream/push id: ").append(this.id);
        return sb.toString();
    }
}
