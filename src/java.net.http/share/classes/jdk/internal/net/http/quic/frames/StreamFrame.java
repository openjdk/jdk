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

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;

import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.quic.packets.QuicPacketEncoder;
import jdk.internal.net.quic.QuicTransportException;

/**
 * A STREAM Frame
 *
 * @spec https://www.rfc-editor.org/info/rfc9000
 *      RFC 9000: QUIC: A UDP-Based Multiplexed and Secure Transport
 */
public final class StreamFrame extends QuicFrame {

    // Flags in frameType()
    private static final int OFF = 0x4;
    private static final int LEN = 0x2;
    private static final int FIN = 0x1;

    private final long streamID;
    // true if the OFF bit in the type field has been set
    private final boolean typeFieldHasOFF;
    private final long offset;
    private final int length; // -1 means consume all data in packet
    private final int dataLength;
    private final ByteBuffer streamData;
    private final boolean fin;

    StreamFrame(ByteBuffer buffer, int type) throws QuicTransportException {
        super(STREAM);
        streamID = decodeVLField(buffer, "streamID");
        if ((type & OFF) > 0) {
            typeFieldHasOFF = true;
            offset = decodeVLField(buffer, "offset");
        } else {
            typeFieldHasOFF = false;
            offset = 0;
        }
        if ((type & LEN) > 0) {
            length = decodeVLFieldAsInt(buffer, "length");
        } else {
            length = -1;
        }
        if (length == -1) {
            int remaining = buffer.remaining();
            streamData = Utils.sliceOrCopy(buffer, buffer.position(), remaining);
            buffer.position(buffer.limit());
            dataLength = remaining;
        } else {
            validateRemainingLength(buffer, length, type);
            int pos = buffer.position();
            streamData = Utils.sliceOrCopy(buffer, pos, length);
            buffer.position(pos + length);
            dataLength = length;
        }
        fin = (type & FIN) == 1;
    }

    /**
     * Creates StreamFrame (length == -1 means no length specified in frame
     * and is assumed to occupy the remainder of the Quic/UDP packet.
     * If a length is specified then it must correspond with the remaining bytes
     * in streamData
     */
    // It would be interesting to have a version of this constructor that can take
    // a list of ByteBuffer.
    public StreamFrame(long streamID, long offset, int length, boolean fin, ByteBuffer streamData) {
        this(streamID, offset, length, fin, streamData, true);
    }

    private StreamFrame(long streamID, long offset, int length, boolean fin, ByteBuffer streamData, boolean slice)
    {
        super(STREAM);
        this.streamID = requireVLRange(streamID, "streamID");
        this.offset = requireVLRange(offset, "offset");
        // if offset is non-zero then we mark that the type field has OFF bit set
        // to allow for that bit to be set when encoding this frame
        this.typeFieldHasOFF = this.offset != 0;
        if (length != -1 && length != streamData.remaining()) {
            throw new IllegalArgumentException("bad length");
        }
        this.length = length;
        this.dataLength = streamData.remaining();
        this.fin = fin;
        this.streamData = slice
                ? streamData.slice(streamData.position(), dataLength)
                : streamData;
    }

    /**
     * Creates a new StreamFrame which is a slice of this stream frame.
     * @param offset the new offset
     * @param length the new length
     * @return a slice of the current stream frame
     * @throws IndexOutOfBoundsException if the offset or length
     * exceed the bounds of this stream frame
     */
    public StreamFrame slice(long offset, int length) {
        long oldoffset = offset();
        long offsetdiff = offset - oldoffset;
        long oldlen = dataLength();
        Objects.checkFromIndexSize(offsetdiff, length, oldlen);
        int pos = streamData.position();
        // safe cast to int since offsetdiff < length
        int newpos = Math.addExact(pos, (int)offsetdiff);
        // preserves the FIN bit if set
        boolean fin = this.fin && offset + length == oldoffset + oldlen;
        ByteBuffer slice = Utils.sliceOrCopy(streamData, newpos, length);
        return new StreamFrame(streamID, offset, length, fin, slice, false);
    }

    /**
     * {@return the stream id}
     */
    public long streamId() {
        return streamID;
    }

    /**
     * {@return whether this frame has a length}
     * A frame that doesn't have a length must be the last
     * frame in the packet.
     */
    public boolean hasLength() {
        return length != -1;
    }

    /**
     * {@return true if this is the last frame in the stream}
     * The last frame has the FIN bit set.
     */
    public boolean isLast() { return fin; }

    @Override
    public long getTypeField() {
        return STREAM | (hasLength() ? LEN : 0)
                      | (typeFieldHasOFF ? OFF : 0)
                      | (fin ? FIN : 0);
    }

    @Override
    public void encode(ByteBuffer dest) {
        if (size() > dest.remaining()) {
            throw new BufferOverflowException();
        }
        int pos = dest.position();
        encodeVLField(dest, getTypeField(), "type");
        encodeVLField(dest, streamID, "streamID");
        if (typeFieldHasOFF) {
            encodeVLField(dest, offset, "offset");
        }
        if (hasLength()) {
            encodeVLField(dest, length, "length");
            assert streamData.remaining() == length;
        }
        putByteBuffer(dest, streamData);
        assert dest.position() - pos == size();
    }

    @Override
    public int size() {
        int size = getVLFieldLengthFor(getTypeField())
                + getVLFieldLengthFor(streamID);
        if (typeFieldHasOFF) {
            size += getVLFieldLengthFor(offset);
        }
        if (hasLength()) {
            return size + getVLFieldLengthFor(length) + length;
        } else {
            return size + streamData.remaining();
        }
    }

    /**
     * {@return the frame payload}
     */
    public ByteBuffer payload() {
        return streamData.slice();
    }

    /**
     * {@return the frame offset}
     */
    public long offset() { return offset; }

    /**
     * {@return the number of data bytes in the frame}
     * @apiNote
     * This is equivalent to calling {@code payload().remaining()}.
     */
    public int dataLength() {
        return dataLength;
    }

    public static int compareOffsets(StreamFrame sf1, StreamFrame sf2) {
        return Long.compare(sf1.offset, sf2.offset);
    }

    /**
     * Computes the header size that would be required to encode a frame with
     * the given streamId, offset, and length.
     * @apiNote
     * This method is useful to figure out how many bytes can be allocated for
     * the frame data, given a size constraint imposed by the space available
     * for the whole datagram payload.
     * @param encoder   the {@code QuicPacketEncoder} - which can be used in case
     *                  some part of the computation is Quic-version dependent.
     * @param streamId  the stream id
     * @param offset    the stream offset
     * @param length    the estimated length of the frame, typically this will be
     *                  the min between the data available in the stream with respect
     *                  to flow control, and the maximum remaining size for the datagram
     *                  payload
     * @return the estimated size of the header for a {@code StreamFrame} that would
     *         be created with the given parameters.
     */
    public static int headerSize(QuicPacketEncoder encoder, long streamId, long offset, long length) {
        // the header length is the size needed to encode the frame type,
        // plus the size needed to encode the streamId, plus the size needed
        // to encode the offset (if not 0) and the size needed to encode the
        // length (if present)
        int headerLength = getVLFieldLengthFor(STREAM | OFF | LEN | FIN)
                + getVLFieldLengthFor(streamId);
        if (offset != 0) headerLength += getVLFieldLengthFor(offset);
        if (length >= 0) headerLength += getVLFieldLengthFor(length);
        return headerLength;
    }

    @Override
    public String toString() {
        return "StreamFrame(stream=" + streamID +
                ", offset=" + offset +
                ", length=" + length +
                ", fin=" + fin + ')';
    }
}
