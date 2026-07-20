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
import java.util.List;

import jdk.internal.net.http.quic.VariableLengthEncoder;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import jdk.internal.net.http.quic.BuffersReader;

/**
 * A PartialFrame helps to read the payload of a frame.
 * This class is not multi-thread safe.
 */
public abstract sealed class PartialFrame
        extends AbstractHttp3Frame
        permits HeadersFrame,
                DataFrame,
                PushPromiseFrame,
                UnknownFrame {

    private static final List<ByteBuffer> NONE = List.of();
    private final long streamingLength;
    private long remaining;
    PartialFrame(long frameType, long streamingLength) {
        super(frameType);
        this.remaining = this.streamingLength = streamingLength;
    }

    @Override
    public final long streamingLength() {
        return streamingLength;
    }

    /**
     * {@return the number of payload bytes that remains to read}
     */
    public final long remaining() {
        return remaining;
    }

    /**
     * Reads remaining payload bytes from the given {@link BuffersReader}.
     * This method must not run concurrently with any code that submit
     * new buffers to the {@link BuffersReader}.
     * @param buffers a {@link BuffersReader} that contains payload bytes.
     * @return the payload bytes available so far, an empty list if no
     *         bytes are available or the whole payload has already been
     *         read
     */
    public final List<ByteBuffer> nextPayloadBytes(BuffersReader buffers) {
        var remaining = this.remaining;
        if (remaining > 0) {
            long available = buffers.remaining();
            if (available > 0) {
                long read = Math.min(remaining, available);
                this.remaining = remaining - read;
                return buffers.getAndRelease(read);
            }
        }
        return NONE;
    }

    /**
     * Reads remaining payload bytes from the given {@link ByteBuffer}.
     * @param buffer a {@link ByteBuffer} that contains payload bytes.
     * @return the payload bytes available in the given buffer, or
     * {@code null} if all payload has been read.
     */
    public final ByteBuffer nextPayloadBytes(ByteBuffer buffer) {
        var remaining = this.remaining;
        if (remaining > 0) {
            int available = buffer.remaining();
            if (available > 0) {
                long read = Math.min(remaining, available);
                remaining -= read;
                this.remaining = remaining;
                assert read <= available;
                int pos = buffer.position();
                int len = (int) read;
                // always create a slice, so that we can move the position
                // of the original buffer, as if the data had been read.
                ByteBuffer next = buffer.slice(pos, len);
                buffer.position(pos + len);
                return next;
            } else return buffer == QuicStreamReader.EOF ? buffer : buffer.slice();
        }
        return null;
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
        assert pos1 - pos0 == super.headersSize();
    }

    @Override
    public String toString() {
        var len = length();
        return "%s (partial: %s/%s)".formatted(this.getClass().getSimpleName(), len - remaining, len);
    }

}
