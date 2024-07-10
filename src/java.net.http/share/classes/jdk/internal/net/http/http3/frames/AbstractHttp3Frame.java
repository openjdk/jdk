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
import java.util.Random;

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.quic.BuffersReader;
import jdk.internal.net.http.quic.VariableLengthEncoder;
import static jdk.internal.net.http.http3.frames.Http3FrameType.asString;

/**
 * Super class for all HTTP/3 frames.
 */

public abstract non-sealed class AbstractHttp3Frame implements Http3Frame {
    public static final Random RANDOM = new Random();
    final long type;
    public AbstractHttp3Frame(long type) {
        this.type = type;
    }

    public final String typeAsString() {
        return asString(type());
    }

    @Override
    public long type() {
        return type;
    }


    /**
     * Computes the size of this frame. This corresponds to
     * the {@linkplain #length()} of the frame's payload, plus the
     * size needed to encode this length, plus the size needed to
     * encode the frame type.
     *
     * @return the size of this frame.
     */
    public long size() {
        var len = length();
        return len + VariableLengthEncoder.getEncodedSize(len)
                + VariableLengthEncoder.getEncodedSize(type());
    }

    private int defaultHeadersSize() {
        var len = length();
        return VariableLengthEncoder.getEncodedSize(len)
                + VariableLengthEncoder.getEncodedSize(type());
    }

    public int headersSize() {
        return defaultHeadersSize();
    }

    @Override
    public long streamingLength() {
        return 0;
    }


    /**
     * Write the frame headers to the given buffer.
     *
     * @apiNote
     * The caller will be responsible for writing the
     * remaining {@linkplain #length() length} bytes of
     * the frame content after writing the frame headers.
     *
     * @implSpec
     * Usually the header of a frame is assumed to simply
     * contain the frame type and frame length.
     * Some subclasses of {@code AbstractHttp3Frame} may
     * however include some additional information.
     * For instance, {@link PushPromiseFrame} may consider
     * the {@link PushPromiseFrame#getPushId() pushId} as
     * being in part of the headers, and write it along
     * in this method after the frame type and length.
     * In such a case, a subclass would also need to
     * override {@link #headersSize()} in order to add
     * the size of the additional information written
     * by {@link #writeHeaders(ByteBuffer)}.
     *
     * @param buf a buffer to write the headers into
     */
    public void writeHeaders(ByteBuffer buf) {
        long len = length();
        int pos0 = buf.position();
        VariableLengthEncoder.encode(buf, type());
        VariableLengthEncoder.encode(buf, len);
        int pos1 = buf.position();
        assert pos1 - pos0 == defaultHeadersSize();
    }

    protected static long decodeRequiredType(final BuffersReader reader, final long expectedType) {
        final long pos = reader.position();
        final long type = VariableLengthEncoder.decode(reader);
        if (type < 0) throw new BufferUnderflowException();
        // TODO: throw an exception instead?
        assert type == expectedType : "bad frame type: " + type + " expected: " + expectedType;
        return type;
    }

    protected static MalformedFrame checkPayloadSize(long frameType,
                                                     BuffersReader reader,
                                                     long start,
                                                     long length) {
        // check position after reading payload
        long read = reader.position() - start;
        if (length != read) {
            reader.position(start + length);
            reader.release();
            return new MalformedFrame(frameType,
                    Http3Error.H3_FRAME_ERROR.code(),
                    "payload length mismatch (length=%s, read=%s)"
                            .formatted(length, start));

        }

        assert length == reader.position() - start;
        return null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(typeAsString())
                .append(": length=")
                .append(length());
        return sb.toString();
    }


}
