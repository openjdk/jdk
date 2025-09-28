/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.http.quic.frames.QuicFrame;
import jdk.internal.net.quic.QuicTransportErrors;
import jdk.internal.net.quic.QuicTransportException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.HexFormat;

import static org.testng.Assert.*;


/*
 * @test
 * @library /test/lib
 * @summary Tests to check QUIC frame decoding errors are handled correctly
 * @run testng/othervm QuicFramesDecoderTest
 */
public class QuicFramesDecoderTest {


    // correct frames. Single byte frames (padding, ping, handshake_done) omitted.
    // ACK without ECN, acked = 0, 2; delay = 2
    private static final byte[] ACK_BASE = HexFormat.of().parseHex("02020201000000");
    private static final byte[] ACK_BASE_B = HexFormat.of().parseHex("0202020100004000");

    // ACK with ECN, acked = 0, 2; delay = 2, ECN=(3,4,5)
    private static final byte[] ACK_ECN = HexFormat.of().parseHex("03020201000000030405");
    private static final byte[] ACK_ECN_B = HexFormat.of().parseHex("0302020100000003044005");

    // RESET_STREAM, stream 3, error 2, final size 1
    private static final byte[] RESET_STREAM = HexFormat.of().parseHex("04030201");
    private static final byte[] RESET_STREAM_B = HexFormat.of().parseHex("0403024001");

    // STOP_SENDING, stream 4, error 3
    private static final byte[] STOP_SENDING = HexFormat.of().parseHex("050403");
    private static final byte[] STOP_SENDING_B = HexFormat.of().parseHex("05044003");

    // CRYPTO, offset 5, length 4, data
    private static final byte[] CRYPTO = HexFormat.of().parseHex("06050403020100");

    // NEW_TOKEN, length 6, data
    private static final byte[] NEW_TOKEN = HexFormat.of().parseHex("0706050403020100");

    // STREAM-o-l-f, stream 7, no data
    private static final byte[] STREAM = HexFormat.of().parseHex("0807");

    // STREAM-o-l+f, stream 8, no data
    private static final byte[] STREAM_F = HexFormat.of().parseHex("0908");

    // STREAM-o+l-f, stream 9, length 8
    private static final byte[] STREAM_L = HexFormat.of().parseHex("0a09080706050403020100");

    // STREAM-o+l+f, stream 10, length 9
    private static final byte[] STREAM_LF = HexFormat.of().parseHex("0b0a09080706050403020100");

    // STREAM+o-l-f, stream 11, offset 0, no data
    private static final byte[] STREAM_O = HexFormat.of().parseHex("0c000a");

    // STREAM+o-l+f, stream 12, offset 0, no data
    private static final byte[] STREAM_OF = HexFormat.of().parseHex("0d000b");

    // STREAM+o+l-f, stream 13, offset 0, length 3
    private static final byte[] STREAM_OL = HexFormat.of().parseHex("0e000c03020100");

    // STREAM+o+l+f, stream 14, offset 0, length 3
    private static final byte[] STREAM_OLF = HexFormat.of().parseHex("0f000d03020100");

    // MAX_DATA, max=15
    private static final byte[] MAX_DATA = HexFormat.of().parseHex("100f");
    private static final byte[] MAX_DATA_B = HexFormat.of().parseHex("10400f");

    // MAX_STREAM_DATA, stream = 16 max=15
    private static final byte[] MAX_STREAM_DATA = HexFormat.of().parseHex("11100f");
    private static final byte[] MAX_STREAM_DATA_B = HexFormat.of().parseHex("1110400f");

    // MAX_STREAMS, bidi, streams = 2^60
    private static final byte[] MAX_STREAMS_B = HexFormat.of().parseHex("12d000000000000000");

    // MAX_STREAMS, uni, streams = 2^60
    private static final byte[] MAX_STREAMS_U = HexFormat.of().parseHex("13d000000000000000");

    // DATA_BLOCKED, max=19
    private static final byte[] DATA_BLOCKED = HexFormat.of().parseHex("1413");
    private static final byte[] DATA_BLOCKED_B = HexFormat.of().parseHex("144013");

    // STREAM_DATA_BLOCKED, stream = 20 max=19
    private static final byte[] STREAM_DATA_BLOCKED = HexFormat.of().parseHex("151413");
    private static final byte[] STREAM_DATA_BLOCKED_B = HexFormat.of().parseHex("15144013");

    // STREAMS_BLOCKED, bidi, streams = 2^60
    private static final byte[] STREAMS_BLOCKED_B = HexFormat.of().parseHex("16d000000000000000");

    // STREAMS_BLOCKED, uni, streams = 2^60
    private static final byte[] STREAMS_BLOCKED_U = HexFormat.of().parseHex("17d000000000000000");

    // NEW_CONNECTION_ID, seq=23, retire=22, len = 5
    private static final byte[] NEW_CONNECTION_ID = HexFormat.of().parseHex("181716"+
            "051413121110"+"0f0e0d0c0b0a09080706050403020100");

    // RETIRE_CONNECTION_ID, seq=24
    private static final byte[] RETIRE_CONNECTION_ID = HexFormat.of().parseHex("1918");
    private static final byte[] RETIRE_CONNECTION_ID_B = HexFormat.of().parseHex("194018");

    // PATH_CHALLENGE
    private static final byte[] PATH_CHALLENGE = HexFormat.of().parseHex("1a0706050403020100");

    // PATH_RESPONSE
    private static final byte[] PATH_RESPONSE = HexFormat.of().parseHex("1b0706050403020100");

    // CONNECTION_CLOSE, quic, error 27, frame type 26, reason='\0'
    private static final byte[] CONNECTION_CLOSE_Q = HexFormat.of().parseHex("1c1b1a0100");
    // CONNECTION_CLOSE, quic, error 27, frame type 26, reason=
    // efbfbf (U+FFFF) - "not a valid unicode character
    // edb080 (U+DC00) - low surrogate, prohibited in UTF8 (RFC3629), must be preceded by high surrogate otherwise
    // eda080 (U+D800) - high surrogate, prohibited in UTF8 (RFC3629), must be followed by low surrogate otherwise
    // 80 - not a first byte of UTF8 sequence
    // c0d0e0f0ff - not a valid UTF8 sequence
    private static final byte[] CONNECTION_CLOSE_Q_BAD_REASON = HexFormat.of().parseHex("1c1b1a0fefbfbfedb080eda08080c0d0e0f0ff");

    // CONNECTION_CLOSE, app, error 28, reason='\0'
    private static final byte[] CONNECTION_CLOSE_A = HexFormat.of().parseHex("1d1c0100");
    // CONNECTION_CLOSE, app, error 28, reason= same as CONNECTION_CLOSE_Q_BAD_REASON
    private static final byte[] CONNECTION_CLOSE_A_BAD_REASON = HexFormat.of().parseHex("1d1c0fefbfbfedb080eda08080c0d0e0f0ff");
    // end of correct frames

    // malformed frames other than truncated
    // ACK acknowledging negative packet
    // ACK without ECN, acked = -1, 1; delay = 2
    private static final byte[] ACK_NEG_BASE = HexFormat.of().parseHex("02010201000000");

    // ACK with ECN, acked = -1, 1; delay = 2, ECN=(3,4,5)
    private static final byte[] ACK_NEG_ECN = HexFormat.of().parseHex("03010201000000030405");

    // ACK without ECN, acked = -1, 0; delay = 2
    private static final byte[] ACK_NEG_BASE_2 = HexFormat.of().parseHex("0200020001");

    // CRYPTO out of range: offset MAX_VL_INT, len=1
    private static final byte[] CRYPTO_OOR = HexFormat.of().parseHex("06ffffffffffffffff0100");

    // NEW_TOKEN empty
    private static final byte[] NEW_TOKEN_EMPTY = HexFormat.of().parseHex("0700");

    // MAX_STREAMS out of range
    // MAX_STREAMS, bidi, streams = 2^60+1
    private static final byte[] MAX_STREAMS_B_OOR = HexFormat.of().parseHex("12d000000000000001");
    // MAX_STREAMS, uni, streams = 2^60+1
    private static final byte[] MAX_STREAMS_U_OOR = HexFormat.of().parseHex("13d000000000000001");

    // STREAMS_BLOCKED out of range
    // STREAMS_BLOCKED, bidi, streams = 2^60+1
    private static final byte[] STREAMS_BLOCKED_B_OOR = HexFormat.of().parseHex("16d000000000000001");

    // STREAMS_BLOCKED, uni, streams = 2^60+1
    private static final byte[] STREAMS_BLOCKED_U_OOR = HexFormat.of().parseHex("17d000000000000001");

    // NEW_CONNECTION_ID, seq=23, retire=22, len = 0
    private static final byte[] NEW_CONNECTION_ID_ZERO = HexFormat.of().parseHex("181716"+
            "00"+"0f0e0d0c0b0a09080706050403020100");

    @DataProvider
    public static Object[][] goodFrames() {
        return new Object[][]{
                new Object[]{"ack without ecn", ACK_BASE, false},
                new Object[]{"ack without ecn", ACK_BASE_B, true},
                new Object[]{"ack with ecn", ACK_ECN, false},
                new Object[]{"ack with ecn", ACK_ECN_B, true},
                new Object[]{"RESET_STREAM", RESET_STREAM, false},
                new Object[]{"RESET_STREAM", RESET_STREAM_B, true},
                new Object[]{"STOP_SENDING", STOP_SENDING, false},
                new Object[]{"STOP_SENDING", STOP_SENDING_B, true},
                new Object[]{"CRYPTO", CRYPTO, false},
                new Object[]{"NEW_TOKEN", NEW_TOKEN, false},
                new Object[]{"STREAM-o-l-f", STREAM, false},
                new Object[]{"STREAM-o-l+f", STREAM_F, false},
                new Object[]{"STREAM-o+l-f", STREAM_L, false},
                new Object[]{"STREAM-o+l+f", STREAM_LF, false},
                new Object[]{"STREAM+o-l-f", STREAM_O, false},
                new Object[]{"STREAM+o-l+f", STREAM_OF, false},
                new Object[]{"STREAM+o+l-f", STREAM_OL, false},
                new Object[]{"STREAM+o+l+f", STREAM_OLF, false},
                new Object[]{"MAX_DATA", MAX_DATA, false},
                new Object[]{"MAX_DATA", MAX_DATA_B, true},
                new Object[]{"MAX_STREAM_DATA", MAX_STREAM_DATA, false},
                new Object[]{"MAX_STREAM_DATA", MAX_STREAM_DATA_B, true},
                new Object[]{"MAX_STREAMS bidi", MAX_STREAMS_B, false},
                new Object[]{"MAX_STREAMS uni", MAX_STREAMS_U, false},
                new Object[]{"DATA_BLOCKED", DATA_BLOCKED, false},
                new Object[]{"DATA_BLOCKED", DATA_BLOCKED_B, true},
                new Object[]{"STREAM_DATA_BLOCKED", STREAM_DATA_BLOCKED, false},
                new Object[]{"STREAM_DATA_BLOCKED", STREAM_DATA_BLOCKED_B, true},
                new Object[]{"STREAMS_BLOCKED bidi", STREAMS_BLOCKED_B, false},
                new Object[]{"STREAMS_BLOCKED uni", STREAMS_BLOCKED_U, false},
                new Object[]{"NEW_CONNECTION_ID", NEW_CONNECTION_ID, false},
                new Object[]{"RETIRE_CONNECTION_ID", RETIRE_CONNECTION_ID, false},
                new Object[]{"RETIRE_CONNECTION_ID", RETIRE_CONNECTION_ID_B, true},
                new Object[]{"PATH_CHALLENGE", PATH_CHALLENGE, false},
                new Object[]{"PATH_RESPONSE", PATH_RESPONSE, false},
                new Object[]{"CONNECTION_CLOSE QUIC", CONNECTION_CLOSE_Q, false},
                new Object[]{"CONNECTION_CLOSE QUIC non-utf8 reason", CONNECTION_CLOSE_Q_BAD_REASON, false},
                new Object[]{"CONNECTION_CLOSE app", CONNECTION_CLOSE_A, false},
                new Object[]{"CONNECTION_CLOSE app non-utf8 reason", CONNECTION_CLOSE_A_BAD_REASON, false},
        };
    }

    @DataProvider
    public static Object[][] badFrames() {
        return new Object[][]{
                new Object[]{"ack without ecn, negative pn", ACK_NEG_BASE},
                new Object[]{"ack without ecn, negative pn, v2", ACK_NEG_BASE_2},
                new Object[]{"ack with ecn, negative pn", ACK_NEG_ECN},
                new Object[]{"CRYPTO out of range", CRYPTO_OOR},
                new Object[]{"NEW_TOKEN empty", NEW_TOKEN_EMPTY},
                new Object[]{"MAX_STREAMS bidi out of range", MAX_STREAMS_B_OOR},
                new Object[]{"MAX_STREAMS uni out of range", MAX_STREAMS_U_OOR},
                new Object[]{"STREAMS_BLOCKED bidi out of range", STREAMS_BLOCKED_B_OOR},
                new Object[]{"STREAMS_BLOCKED uni out of range", STREAMS_BLOCKED_U_OOR},
                new Object[]{"NEW_CONNECTION_ID zero length", NEW_CONNECTION_ID_ZERO},
        };
    }

    @Test(dataProvider = "goodFrames")
    public void testReencode(String desc, byte[] frame, boolean bloated) throws Exception {
        // check if the goodFrames provider indeed contains good frames
        ByteBuffer buf = ByteBuffer.wrap(frame);
        var qf = QuicFrame.decode(buf);
        assertFalse(buf.hasRemaining(), buf.remaining() + " bytes left in buffer after parsing");
        // some frames deliberately use suboptimal encoding, skip them
        if (bloated) return;
        assertEquals(qf.size(), frame.length, "Frame size mismatch");
        buf.clear();
        ByteBuffer encoded = ByteBuffer.allocate(frame.length);
        qf.encode(encoded);
        assertFalse(encoded.hasRemaining(), "Actual frame length mismatch");
        encoded.flip();
        assertEquals(buf, encoded, "Encoded buffer is different from the original one");
    }

    @Test(dataProvider = "goodFrames")
    public void testToString(String desc, byte[] frame, boolean bloated) throws Exception {
        // check if the goodFrames provider indeed contains good frames
        ByteBuffer buf = ByteBuffer.wrap(frame);
        var qf = QuicFrame.decode(buf);
        assertFalse(buf.hasRemaining(), buf.remaining() + " bytes left in buffer after parsing");
        System.out.println(qf); // should not throw
    }

    @Test(dataProvider = "goodFrames")
    public void testTruncatedFrame(String desc, byte[] frame, boolean bloated) throws Exception {
        // check if parsing a truncated frame throws the right error
        ByteBuffer buf = ByteBuffer.wrap(frame);
        for (int i = 1; i < buf.capacity(); i++) {
            buf.position(0);
            buf.limit(i);
            try {
                var qf = QuicFrame.decode(buf);
                fail("Expected the decoder to throw on length " + i + ", got: " + qf);
            } catch (QuicTransportException e) {
                assertEquals(e.getErrorCode(), QuicTransportErrors.FRAME_ENCODING_ERROR.code());
            }
        }
    }

    @Test(dataProvider = "badFrames")
    public void testBadFrame(String desc, byte[] frame) throws Exception {
        // check if parsing a bad frame throws the right error
        ByteBuffer buf = ByteBuffer.wrap(frame);
        try {
            var qf = QuicFrame.decode(buf);
            fail("Expected the decoder to throw, got: "+qf);
        } catch (QuicTransportException e) {
            assertEquals(e.getErrorCode(), QuicTransportErrors.FRAME_ENCODING_ERROR.code());
        }
    }
}
