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

import java.nio.ByteBuffer;

import jdk.internal.net.http.quic.VariableLengthEncoder;

/**
 * Represents a PUSH_PROMISE HTTP3 frame
 */
public final class PushPromiseFrame extends PartialFrame {

    /**
     * The PUSH_PROMISE frame type, as defined by HTTP/3
     */
    public static final int TYPE = Http3FrameType.TYPE.PUSH_PROMISE_FRAME;

    private final long length;
    private final long pushId;

    public PushPromiseFrame(final long pushId, final long fieldLength) {
        super(TYPE, fieldLength);
        if (pushId < 0 || pushId > VariableLengthEncoder.MAX_ENCODED_INTEGER) {
            throw new IllegalArgumentException("invalid pushId: " + pushId);
        }
        this.pushId = pushId;
        // the payload length of this frame
        this.length = VariableLengthEncoder.getEncodedSize(this.pushId) + fieldLength;
    }

    @Override
    public long length() {
        return this.length;
    }

    public long getPushId() {
        return this.pushId;
    }

    /**
     * Write the frame header and the promise {@link #getPushId()
     * pushId} to the given buffer. The caller will be responsible
     * for writing the remaining {@link #streamingLength()} bytes
     * that constitutes the field section length.
     * @param buf a buffer to write the headers into
     */
    @Override
    public void writeHeaders(ByteBuffer buf) {
        super.writeHeaders(buf);
        VariableLengthEncoder.encode(buf, this.pushId);
    }

    /**
     * {@return the number of bytes needed to write the headers and
     * the promised {@link #getPushId() pushId}}.
     */
    @Override
    public int headersSize() {
        return super.headersSize() + VariableLengthEncoder.getEncodedSize(pushId);
    }
}
