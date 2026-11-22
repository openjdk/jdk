/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2026, Amazon.com Inc. or its affiliates. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jdk.internal.net.http.frame;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class FramesDecoderTest {

    abstract static class TestFrameProcessor implements FramesDecoder.FrameProcessor {
        protected volatile int count;
        public int numberOfFramesDecoded() { return count; }
    }

    /**
     * Verifies that a ByteBuffer containing more that one frame, destined
     * to be returned to the user's subscriber, i.e. a data frame, does not
     * inadvertently expose the following frame ( between its limit and
     * capacity ).
     */
    @Test
    public void decodeDataFrameFollowedByAnother() throws Exception {
        // input frames for to the decoder
        List<ByteBuffer> data1 = List.of(ByteBuffer.wrap("XXXX".getBytes(UTF_8)));
        DataFrame dataFrame1 = new DataFrame(1, 0, data1);
        List<ByteBuffer> data2 = List.of(ByteBuffer.wrap("YYYY".getBytes(UTF_8)));
        DataFrame dataFrame2 = new DataFrame(1, 0, data2);

        List<ByteBuffer> buffers = new ArrayList<>();
        FramesEncoder encoder = new FramesEncoder();
        buffers.addAll(encoder.encodeFrame(dataFrame1));
        buffers.addAll(encoder.encodeFrame(dataFrame2));

        ByteBuffer combined = ByteBuffer.allocate(1024);
        buffers.stream().forEach(combined::put);
        combined.flip();

        TestFrameProcessor testFrameProcessor = new TestFrameProcessor() {
            @Override
            public void processFrame(Http2Frame frame) throws IOException {
                assertTrue(frame instanceof DataFrame);
                DataFrame dataFrame = (DataFrame) frame;
                List<ByteBuffer> list = dataFrame.getData();
                assertEquals(1, list.size());
                ByteBuffer data = list.get(0);
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                if (count == 0) {
                    assertEquals("XXXX", new String(bytes, UTF_8));
                    out.println("First data received:" + data);
                    assertEquals(data.limit(), data.position());  // since bytes read
                    assertEquals(data.capacity(), data.limit());
                } else {
                    assertEquals("YYYY", new String(bytes, UTF_8));
                    out.println("Second data received:" + data);
                }
                count++;
            }
        };
        FramesDecoder decoder = new FramesDecoder(testFrameProcessor);

        out.println("Sending " + combined + " to decoder: ");
        decoder.decode(combined);
        assertEquals(2, testFrameProcessor.numberOfFramesDecoded());
    }


    /**
     * Verifies that a ByteBuffer containing ONLY data one frame, destined
     * to be returned to the user's subscriber, does not restrict the capacity.
     * The complete buffer ( all its capacity ), since no longer used by the
     * HTTP Client, should be returned to the user.
     */
    @Test
    public void decodeDataFrameEnsureNotCapped() throws Exception {
        // input frames for to the decoder
        List<ByteBuffer> data1 = List.of(ByteBuffer.wrap("XXXX".getBytes(UTF_8)));
        DataFrame dataFrame1 = new DataFrame(1, 0, data1);

        List<ByteBuffer> buffers = new ArrayList<>();
        FramesEncoder encoder = new FramesEncoder();
        buffers.addAll(encoder.encodeFrame(dataFrame1));

        ByteBuffer combined = ByteBuffer.allocate(1024);
        buffers.stream().forEach(combined::put);
        combined.flip();

        TestFrameProcessor testFrameProcessor = new TestFrameProcessor() {
            @Override
            public void processFrame(Http2Frame frame) throws IOException {
                assertTrue(frame instanceof DataFrame);
                DataFrame dataFrame = (DataFrame) frame;
                List<ByteBuffer> list = dataFrame.getData();
                assertEquals(1, list.size());
                ByteBuffer data = list.get(0);
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                assertEquals("XXXX", new String(bytes, UTF_8));
                out.println("First data received:" + data);
                assertEquals(data.limit(), data.position());  // since bytes read
                //assertNotEquals(data.limit(), data.capacity());
                assertEquals(1024 - 9 /*frame header*/, data.capacity());
                count++;
            }
        };
        FramesDecoder decoder = new FramesDecoder(testFrameProcessor);

        out.println("Sending " + combined + " to decoder: ");
        decoder.decode(combined);
        assertEquals(1, testFrameProcessor.numberOfFramesDecoded());
    }

    private static byte[] frame(int type, int streamId, byte[] payload) {
        ByteBuffer buffer = ByteBuffer.allocate(Http2Frame.FRAME_HEADER_SIZE
                + payload.length);
        int len = payload.length;
        buffer.put((byte) (len >> 16));
        buffer.put((byte) (len >> 8));
        buffer.put((byte) len);
        buffer.put((byte) type);
        buffer.put((byte) 0);
        buffer.putInt(streamId);
        buffer.put(payload);
        return buffer.array();
    }

    private static final byte[] PING = frame(PingFrame.TYPE, 0, new byte[8]);

    private static List<Http2Frame> decode(byte[][] frames) throws IOException {
        int len = 0;
        for (byte[] frame : frames) {
            len += frame.length;
        }
        ByteBuffer input = ByteBuffer.allocate(len);
        for (byte[] frame : frames) {
            input.put(frame);
        }
        input.flip();
        List<Http2Frame> decoded = new ArrayList<>();
        new FramesDecoder(decoded::add).decode(input);
        return decoded;
    }

    /**
     * A PRIORITY frame with a length other than 5
     */
    @Test
    public void decodeBadLengthPriorityFrame() throws Exception {
        List<Http2Frame> frames = decode(new byte[][]{frame(PriorityFrame.TYPE, 3, new byte[4]), PING});
        assertEquals(2, frames.size(), frames.toString());
        MalformedFrame malformedFrame = assertInstanceOf(MalformedFrame.class, frames.get(0));
        assertEquals(ErrorFrame.FRAME_SIZE_ERROR, malformedFrame.getErrorCode());
        assertEquals(3, malformedFrame.streamid());
        assertFalse(malformedFrame.isConnectionError());
        assertInstanceOf(PingFrame.class, frames.get(1));
    }

    /**
     * ALTSVC frames with non-ASCII origin or value.
     */
    @Test
    public void decodeNonAsciiAltSvcFrame() throws Exception {
        byte[] badOrigin = {0, 2, (byte) 0xC3, (byte) 0xA9, 'h', '2', '=', '"', ':', '1', '"'};
        byte[] badValue = {0, 1, 'x', 'h', '2', '=', (byte) 0xC3, (byte) 0xA9};
        List<Http2Frame> frames = decode(new byte[][]{frame(AltSvcFrame.TYPE, 0, badOrigin),
                frame(AltSvcFrame.TYPE, 0, badValue), PING});
        assertEquals(1, frames.size(), frames.toString());
        assertInstanceOf(PingFrame.class, frames.get(0));
    }

    /**
     * ALTSVC frames too small.
     */
    @Test
    public void decodeTooSmallAltSvcFrame() throws Exception {
        for (byte[] payload : List.of(new byte[] {0}, new byte[] {0, 10, 'a', 'b'})) {
            List<Http2Frame> frames = decode(new byte[][]{frame(AltSvcFrame.TYPE, 0, payload)});
            assertEquals(1, frames.size(), frames.toString());
            MalformedFrame malformedFrame = assertInstanceOf(MalformedFrame.class, frames.get(0));
            assertEquals(ErrorFrame.FRAME_SIZE_ERROR, malformedFrame.getErrorCode());
            assertTrue(malformedFrame.isConnectionError());
        }
    }
}
