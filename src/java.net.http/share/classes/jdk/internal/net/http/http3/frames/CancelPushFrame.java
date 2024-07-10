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
 * Represents the CANCEL_PUSH HTTP3 frame
 */
public final class CancelPushFrame extends AbstractHttp3Frame {

    public static final int TYPE = Http3FrameType.TYPE.CANCEL_PUSH_FRAME;
    private final long length;
    private final long pushId;

    public CancelPushFrame(final long pushId) {
        super(Http3FrameType.CANCEL_PUSH.type());
        this.pushId = pushId;
        // the payload length of this frame
        this.length = VariableLengthEncoder.getEncodedSize(this.pushId);
    }

    // only used when constructing the frame during decoding content over a stream
    private CancelPushFrame(final long pushId, final long length) {
        super(Http3FrameType.CANCEL_PUSH.type());
        this.pushId = pushId;
        this.length = length;
    }

    @Override
    public long length() {
        return this.length;
    }

    public long getPushId() {
        return pushId;
    }

    public void writeFrame(final ByteBuffer buf) {
        // write the type of the frame
        VariableLengthEncoder.encode(buf, this.type);
        // write the length of the payload
        VariableLengthEncoder.encode(buf, this.length);
        // write the push id that needs to be cancelled
        VariableLengthEncoder.encode(buf, this.pushId);
    }

    /**
     * This method is expected to be called when the reader
     * contains enough bytes to decode the frame.
     * @param reader the reader
     * @param debug  a logger for debugging purposes
     * @return the new frame
     * @throws BufferUnderflowException if the reader doesn't contain
     *         enough bytes to decode the frame
     */
    static AbstractHttp3Frame decodeFrame(final BuffersReader reader, final Logger debug) {
        long position = reader.position();
        decodeRequiredType(reader, TYPE);
        long length = VariableLengthEncoder.decode(reader);
        if (length > reader.remaining() || length < 0) {
            reader.position(position);
            throw new BufferUnderflowException();
        }
        // position before reading payload
        long start = reader.position();
        if (length == 0 || length != VariableLengthEncoder.peekEncodedValueSize(reader, start)) {
            // frame length does not match the enclosed pushId
            return new MalformedFrame(TYPE, Http3Error.H3_FRAME_ERROR.code(),
                    "Invalid length in CANCEL_PUSH frame: " + length);
        }

        long pushId = VariableLengthEncoder.decode(reader);
        if (pushId == -1) {
            reader.position(position);
            throw new BufferUnderflowException();
        }

        // check position after reading payload
        var malformed = checkPayloadSize(TYPE, reader, start, length);
        if (malformed != null) return malformed;

        reader.release();
        return new CancelPushFrame(pushId);
    }
}
