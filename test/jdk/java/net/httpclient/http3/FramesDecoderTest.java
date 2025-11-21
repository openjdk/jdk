/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.http3.Http3Error;
import jdk.internal.net.http.http3.frames.FramesDecoder;
import jdk.internal.net.http.http3.frames.Http3Frame;
import jdk.internal.net.http.http3.frames.MalformedFrame;
import jdk.internal.net.http.quic.streams.QuicStreamReader;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


/*
 * @test
 * @library /test/lib
 * @modules java.net.http/jdk.internal.net.http.http3
 * @modules java.net.http/jdk.internal.net.http.http3.frames
 * @modules java.net.http/jdk.internal.net.http.quic.streams
 * @run junit/othervm FramesDecoderTest
 * @summary Tests to check HTTP3 methods decode frames correctly
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FramesDecoderTest {
    // frames with arbitrary content, interpreted as PartialFrames
    byte[][] vlframes() {
        return new byte[][]{
                {0, 2, 0, 0}, // DATA frame, 2 bytes = 0,0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 2, 0, 0}, // DATA frame, 8-byte VL encoding, 2 bytes = 0,0
                {1, 2, 0, 0}, // HEADERS frame, 2 bytes = 0,0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 1, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 2, 0, 0}, // HEADERS frame, 8-byte VL encoding, 2 bytes = 0,0
                {5, 3, 0, 0, 0}, // PUSH_PROMISE frame, Push ID = 0, 2 bytes = 0,0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 5, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 10, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }, // PUSH_PROMISE frame, 8-byte VL encoding, Push ID = 0, 2 bytes = 0,0
                {33, 2, 0, 0}, // RESERVED frame, 2 bytes = 0,0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 33, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 2, 0, 0}, // RESERVED frame, 8-byte VL encoding, 2 bytes = 0,0
                {32, 2, 0, 0}, // UNKNOWN frame, 2 bytes = 0,0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 32, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 2, 0, 0}, // UNKNOWN frame, 8-byte VL encoding, 2 bytes = 0,0
        };
    }

    // frames with predefined content, correct
    byte[][] fixedframes() {
        return new byte[][]{
                {3, 1, 0}, // CANCEL_PUSH frame, Push ID = 0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 3, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 8, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0}, // CANCEL_PUSH frame, 8-byte VL encoding, Push ID = 0
                {7, 1, 0}, // GOAWAY frame, Push ID = 0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 8, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0}, // GOAWAY frame, 8-byte VL encoding, Push ID = 0
                {13, 1, 0}, // MAX_PUSH_ID frame, Push ID = 0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 13, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 8, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0}, // MAX_PUSH_ID frame, 8-byte VL encoding, Push ID = 0
                {4, 0}, // SETTINGS frame, empty
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 4, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0}, // SETTINGS frame, 8-byte VL encoding, empty
                {4, 2, 31, 0}, // SETTINGS frame, 31(reserved)->0
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 4, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 16, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 33, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 0}, // SETTINGS frame, 8-byte VL encoding, 33(reserved)->0
                {4, 3, 0x40, 33, 0}, // SETTINGS frame, 33(reserved)->0
        };
    }

    // incorrect frames
    byte[][] badframes() {
        return new byte[][]{
                {3, 2, 0, 0}, // CANCEL_PUSH frame, Push ID = 0, extra byte
                {3, 2, (byte) 0xC0, 0}, // CANCEL_PUSH frame, Push ID = truncated VL
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 3, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 5, (byte) 0x80, 0, 0, 0, 0}, // CANCEL_PUSH frame, 8-byte VL encoding, Push ID = 0, extra byte
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 3, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0}, // CANCEL_PUSH frame, 8-byte VL encoding, Push ID = truncated VL
                {7, 2, 0, 0}, // GOAWAY frame, Push ID = 0, extra byte
                {7, 2, (byte) 0xC0, 0}, // GOAWAY frame, Push ID = truncated VL
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 5, (byte) 0x80, 0, 0, 0, 0}, // GOAWAY frame, 8-byte VL encoding, Push ID = 0, extra byte
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0}, // GOAWAY frame, 8-byte VL encoding, Push ID = truncated VL
                {13, 2, 0, 0}, // MAX_PUSH_ID frame, Push ID = 0, extra byte
                {13, 2, (byte) 0xC0, 0}, // MAX_PUSH_ID frame, Push ID = truncated VL
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 13, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 5, (byte) 0x80, 0, 0, 0, 0}, // MAX_PUSH_ID frame, 8-byte VL encoding, Push ID = 0, extra byte
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 13, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 7, (byte) 0xC0, 0, 0, 0, 0, 0, 0}, // MAX_PUSH_ID frame, 8-byte VL encoding, Push ID = truncated VL
                {(byte) 0xC0, 0, 0, 0, 0, 0, 0, 5, (byte) 0xC0, 0, 0, 0, 0, 0, 0, 1, (byte) 0xC0 }, // PUSH_PROMISE frame, 8-byte VL encoding, Push ID = truncated VL
                {4, 5, 33, 0, 0x41, 0, 0x40}, // SETTINGS frame, 33(reserved)->0, 64 -> truncated VL
                {4, 4, 33, 0, 0x41, 0}, // SETTINGS frame, 33(reserved)->0, 64 -> ?
                {4, 3, 33, 0, 0x41}, // SETTINGS frame, 33(reserved)->0, truncated VL
                {4, 2, 33, 0x40}, // SETTINGS frame, 33(reserved)-> truncated VL
                {4, 1, 33}, // SETTINGS frame, 33(reserved)->?
        };
    }

    private static int bufLength(List<ByteBuffer> bufs) {
        return bufs.stream().mapToInt(ByteBuffer::remaining).sum();
    }

    @ParameterizedTest
    @MethodSource("vlframes")
    public void testFullVLFrames(byte[] frame) {
        // offer the entire frame at once
        FramesDecoder fd = new FramesDecoder("test");
        fd.submit(ByteBuffer.wrap(frame));
        fd.submit(QuicStreamReader.EOF);
        Http3Frame h3frame = fd.poll();
        assertEquals(2, h3frame.streamingLength());
        assertEquals(h3frame, fd.poll());
        assertFalse(fd.eof());
        List<ByteBuffer> bufs = fd.readPayloadBytes();
        assertEquals(2, bufLength(bufs));
        assertNull(fd.poll());
        assertTrue(fd.eof());
    }

    @ParameterizedTest
    @MethodSource("vlframes")
    public void testSplitVLFrames(byte[] frame) {
        // offer the frame one byte at a time
        FramesDecoder fd = new FramesDecoder("test");
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        for (int i = 1; i <= frame.length; i++) {
            buffer.position(i-1);
            buffer.limit(i);
            fd.submit(buffer.asReadOnlyBuffer());
            if (i < frame.length - 2) {
                assertNull(fd.poll());
            } else {
                Http3Frame h3frame = fd.poll();
                assertEquals(2, h3frame.streamingLength());
            }
        }
        Http3Frame h3frame = fd.poll();
        assertEquals(2, h3frame.streamingLength());
        assertEquals(h3frame, fd.poll());
        assertFalse(fd.eof());
        List<ByteBuffer> bufs = fd.readPayloadBytes();
        assertEquals(2, bufLength(bufs));
        assertNull(fd.poll());
        assertFalse(fd.eof());
        fd.submit(QuicStreamReader.EOF);
        assertTrue(fd.eof());
    }

    @ParameterizedTest
    @MethodSource("fixedframes")
    public void testFullGoodFrames(byte[] frame) {
        // offer the entire frame at once
        FramesDecoder fd = new FramesDecoder("test");
        fd.submit(ByteBuffer.wrap(frame));
        fd.submit(QuicStreamReader.EOF);
        Http3Frame h3frame = fd.poll();
        assertEquals(0, h3frame.streamingLength());
        assertTrue(fd.eof());
        List<ByteBuffer> bufs = fd.readPayloadBytes();
        assertNull(bufs);
        assertNull(fd.poll());
        assertTrue(fd.eof());
    }

    @ParameterizedTest
    @MethodSource("fixedframes")
    public void testSplitGoodFrames(byte[] frame) {
        // offer the frame one byte at a time
        FramesDecoder fd = new FramesDecoder("test");
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        for (int i = 1; i <= frame.length; i++) {
            buffer.position(i-1);
            buffer.limit(i);
            fd.submit(buffer.asReadOnlyBuffer());
            if (i < frame.length) {
                assertNull(fd.poll());
            } else {
                Http3Frame h3frame = fd.poll();
                assertEquals(0, h3frame.streamingLength());
            }
        }
        assertNull(fd.poll());
        assertFalse(fd.eof());
        fd.submit(QuicStreamReader.EOF);
        assertTrue(fd.eof());
    }

    @ParameterizedTest
    @MethodSource("badframes")
    public void testFullBadFrames(byte[] frame) {
        // offer the entire frame at once
        FramesDecoder fd = new FramesDecoder("test");
        fd.submit(ByteBuffer.wrap(frame));
        fd.submit(QuicStreamReader.EOF);
        Http3Frame h3frame = fd.poll();
        assertInstanceOf(MalformedFrame.class, h3frame);
        assertEquals(Http3Error.H3_FRAME_ERROR.code(), ((MalformedFrame)h3frame).getErrorCode());
    }

    @ParameterizedTest
    @MethodSource("badframes")
    public void testSplitBadFrames(byte[] frame) {
        // offer the frame one byte at a time
        FramesDecoder fd = new FramesDecoder("test");
        ByteBuffer buffer = ByteBuffer.wrap(frame);
        for (int i = 1; i <= frame.length; i++) {
            buffer.position(i-1);
            buffer.limit(i);
            fd.submit(buffer.asReadOnlyBuffer());
            if (i < frame.length) {
                assertNull(fd.poll());
            } else {
                Http3Frame h3frame = fd.poll();
                assertInstanceOf(MalformedFrame.class, h3frame);
                assertEquals(Http3Error.H3_FRAME_ERROR.code(), ((MalformedFrame)h3frame).getErrorCode());
            }
        }
    }
}
