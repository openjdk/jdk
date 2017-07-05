/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.frame;

import jdk.incubator.http.internal.common.ByteBufferReference;
import jdk.incubator.http.internal.common.Utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Frames Encoder
 *
 * Encode framed into ByteBuffers.
 * The class is stateless.
 */
public class FramesEncoder {


    public FramesEncoder() {
    }

    public ByteBufferReference[] encodeFrames(List<HeaderFrame> frames) {
        List<ByteBufferReference> refs = new ArrayList<>(frames.size() * 2);
        for (HeaderFrame f : frames) {
            refs.addAll(Arrays.asList(encodeFrame(f)));
        }
        return refs.toArray(new ByteBufferReference[0]);
    }

    public ByteBufferReference encodeConnectionPreface(byte[] preface, SettingsFrame frame) {
        final int length = frame.length();
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length + preface.length);
        ByteBuffer buf = ref.get();
        buf.put(preface);
        putSettingsFrame(buf, frame, length);
        buf.flip();
        return ref;
    }

    public ByteBufferReference[] encodeFrame(Http2Frame frame) {
        switch (frame.type()) {
            case DataFrame.TYPE:
                return encodeDataFrame((DataFrame) frame);
            case HeadersFrame.TYPE:
                return encodeHeadersFrame((HeadersFrame) frame);
            case PriorityFrame.TYPE:
                return encodePriorityFrame((PriorityFrame) frame);
            case ResetFrame.TYPE:
                return encodeResetFrame((ResetFrame) frame);
            case SettingsFrame.TYPE:
                return encodeSettingsFrame((SettingsFrame) frame);
            case PushPromiseFrame.TYPE:
                return encodePushPromiseFrame((PushPromiseFrame) frame);
            case PingFrame.TYPE:
                return encodePingFrame((PingFrame) frame);
            case GoAwayFrame.TYPE:
                return encodeGoAwayFrame((GoAwayFrame) frame);
            case WindowUpdateFrame.TYPE:
                return encodeWindowUpdateFrame((WindowUpdateFrame) frame);
            case ContinuationFrame.TYPE:
                return encodeContinuationFrame((ContinuationFrame) frame);
            default:
                throw new UnsupportedOperationException("Not supported frame "+frame.type()+" ("+frame.getClass().getName()+")");
        }
    }

    private static final int NO_FLAGS = 0;
    private static final int ZERO_STREAM = 0;

    private ByteBufferReference[] encodeDataFrame(DataFrame frame) {
        // non-zero stream
        assert frame.streamid() != 0;
        ByteBufferReference ref = encodeDataFrameStart(frame);
        if (frame.getFlag(DataFrame.PADDED)) {
            return joinWithPadding(ref, frame.getData(), frame.getPadLength());
        } else {
            return join(ref, frame.getData());
        }
    }

    private ByteBufferReference encodeDataFrameStart(DataFrame frame) {
        boolean isPadded = frame.getFlag(DataFrame.PADDED);
        final int length = frame.getDataLength() + (isPadded ? (frame.getPadLength() + 1) : 0);
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + (isPadded ? 1 : 0));
        ByteBuffer buf = ref.get();
        putHeader(buf, length, DataFrame.TYPE, frame.getFlags(), frame.streamid());
        if (isPadded) {
            buf.put((byte) frame.getPadLength());
        }
        buf.flip();
        return ref;
    }

    private ByteBufferReference[] encodeHeadersFrame(HeadersFrame frame) {
        // non-zero stream
        assert frame.streamid() != 0;
        ByteBufferReference ref = encodeHeadersFrameStart(frame);
        if (frame.getFlag(HeadersFrame.PADDED)) {
            return joinWithPadding(ref, frame.getHeaderBlock(), frame.getPadLength());
        } else {
            return join(ref, frame.getHeaderBlock());
        }
    }

    private ByteBufferReference encodeHeadersFrameStart(HeadersFrame frame) {
        boolean isPadded = frame.getFlag(HeadersFrame.PADDED);
        boolean hasPriority = frame.getFlag(HeadersFrame.PRIORITY);
        final int length = frame.getHeaderLength() + (isPadded ? (frame.getPadLength() + 1) : 0) + (hasPriority ? 5 : 0);
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + (isPadded ? 1 : 0) + (hasPriority ? 5 : 0));
        ByteBuffer buf = ref.get();
        putHeader(buf, length, HeadersFrame.TYPE, frame.getFlags(), frame.streamid());
        if (isPadded) {
            buf.put((byte) frame.getPadLength());
        }
        if (hasPriority) {
            putPriority(buf, frame.getExclusive(), frame.getStreamDependency(), frame.getWeight());
        }
        buf.flip();
        return ref;
    }

    private ByteBufferReference[] encodePriorityFrame(PriorityFrame frame) {
        // non-zero stream; no flags
        assert frame.streamid() != 0;
        final int length = 5;
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, PriorityFrame.TYPE, NO_FLAGS, frame.streamid());
        putPriority(buf, frame.exclusive(), frame.streamDependency(), frame.weight());
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodeResetFrame(ResetFrame frame) {
        // non-zero stream; no flags
        assert frame.streamid() != 0;
        final int length = 4;
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, ResetFrame.TYPE, NO_FLAGS, frame.streamid());
        buf.putInt(frame.getErrorCode());
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodeSettingsFrame(SettingsFrame frame) {
        // only zero stream
        assert frame.streamid() == 0;
        final int length = frame.length();
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putSettingsFrame(buf, frame, length);
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodePushPromiseFrame(PushPromiseFrame frame) {
        // non-zero stream
        assert frame.streamid() != 0;
        boolean isPadded = frame.getFlag(PushPromiseFrame.PADDED);
        final int length = frame.getHeaderLength() + (isPadded ? 5 : 4);
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + (isPadded ? 5 : 4));
        ByteBuffer buf = ref.get();
        putHeader(buf, length, PushPromiseFrame.TYPE, frame.getFlags(), frame.streamid());
        if (isPadded) {
            buf.put((byte) frame.getPadLength());
        }
        buf.putInt(frame.getPromisedStream());
        buf.flip();

        if (frame.getFlag(PushPromiseFrame.PADDED)) {
            return joinWithPadding(ref, frame.getHeaderBlock(), frame.getPadLength());
        } else {
            return join(ref, frame.getHeaderBlock());
        }
    }

    private ByteBufferReference[] encodePingFrame(PingFrame frame) {
        // only zero stream
        assert frame.streamid() == 0;
        final int length = 8;
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, PingFrame.TYPE, frame.getFlags(), ZERO_STREAM);
        buf.put(frame.getData());
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodeGoAwayFrame(GoAwayFrame frame) {
        // only zero stream; no flags
        assert frame.streamid() == 0;
        byte[] debugData = frame.getDebugData();
        final int length = 8 + debugData.length;
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, GoAwayFrame.TYPE, NO_FLAGS, ZERO_STREAM);
        buf.putInt(frame.getLastStream());
        buf.putInt(frame.getErrorCode());
        if (debugData.length > 0) {
            buf.put(debugData);
        }
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodeWindowUpdateFrame(WindowUpdateFrame frame) {
        // any stream; no flags
        final int length = 4;
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE + length);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, WindowUpdateFrame.TYPE, NO_FLAGS, frame.streamid);
        buf.putInt(frame.getUpdate());
        buf.flip();
        return new ByteBufferReference[]{ref};
    }

    private ByteBufferReference[] encodeContinuationFrame(ContinuationFrame frame) {
        // non-zero stream;
        assert frame.streamid() != 0;
        final int length = frame.getHeaderLength();
        ByteBufferReference ref = getBuffer(Http2Frame.FRAME_HEADER_SIZE);
        ByteBuffer buf = ref.get();
        putHeader(buf, length, ContinuationFrame.TYPE, frame.getFlags(), frame.streamid());
        buf.flip();
        return join(ref, frame.getHeaderBlock());
    }

    private ByteBufferReference[] joinWithPadding(ByteBufferReference ref, ByteBufferReference[] data, int padLength) {
        ByteBufferReference[] references = new ByteBufferReference[2 + data.length];
        references[0] = ref;
        System.arraycopy(data, 0, references, 1, data.length);
        assert references[references.length - 1] == null;
        references[references.length - 1] = getPadding(padLength);
        return references;
    }

    private ByteBufferReference[] join(ByteBufferReference ref, ByteBufferReference[] data) {
        ByteBufferReference[] references = new ByteBufferReference[1 + data.length];
        references[0] = ref;
        System.arraycopy(data, 0, references, 1, data.length);
        return references;
    }

    private void putSettingsFrame(ByteBuffer buf, SettingsFrame frame, int length) {
        // only zero stream;
        assert frame.streamid() == 0;
        putHeader(buf, length, SettingsFrame.TYPE, frame.getFlags(), ZERO_STREAM);
        frame.toByteBuffer(buf);
    }

    private void putHeader(ByteBuffer buf, int length, int type, int flags, int streamId) {
        int x = (length << 8) + type;
        buf.putInt(x);
        buf.put((byte) flags);
        buf.putInt(streamId);
    }

    private void putPriority(ByteBuffer buf, boolean exclusive, int streamDependency, int weight) {
        buf.putInt(exclusive ? (1 << 31) + streamDependency : streamDependency);
        buf.put((byte) weight);
    }

    private ByteBufferReference getBuffer(int capacity) {
        return ByteBufferReference.of(ByteBuffer.allocate(capacity));
    }

    public ByteBufferReference getPadding(int length) {
        if (length > 255) {
            throw new IllegalArgumentException("Padding too big");
        }
        return ByteBufferReference.of(ByteBuffer.allocate(length)); // zeroed!
    }

}
