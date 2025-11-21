/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import jdk.internal.net.http.quic.packets.*;
import java.util.Arrays;

/**
 * @test
 * @summary Unit encoding/decoding tests for the QUICPacketNumbers.
 * @modules java.net.http/jdk.internal.net.http.quic.packets
 */
public class QuicPacketNumbersTest {

    public static void main(String[] args) throws Exception {

        // Test encoding logic.
        // Test values from the upcoming QUIC RFC, Appendix A.2.
        checkEncodePacketNumber(0xac5c02L, 0xabe8b3L,
                new byte[]{(byte) 0x5c, (byte) 0x02});
        checkEncodePacketNumber(0xace8feL, 0xabe8bcL,
                new byte[]{(byte) 0xac, (byte) 0xe8, (byte) 0xfe}
        );

        // Various checks for "None" encodings.
        checkEncodePacketNumber(0x00L, -1L,
                new byte[]{(byte) 0x00});
        checkEncodePacketNumber(0x05L, -1L,
                new byte[]{(byte) 0x05});
        checkEncodePacketNumber(0x7eL, -1L,
                new byte[]{(byte) 0x7E});
        checkEncodePacketNumber(0x7fL, -1L,
                new byte[]{(byte) 0x00, (byte) 0x7f});
        checkEncodePacketNumber(0x80L, -1L,
                new byte[]{(byte) 0x00, (byte) 0x80});
        checkEncodePacketNumber(0xffL, -1L,
                new byte[]{(byte) 0x00, (byte) 0xff});
        checkEncodePacketNumber(0x100L, -1L,
                new byte[]{(byte) 0x01, (byte) 0x00});

        // Various checks for a packet 0 that has been ack'd.
        checkEncodePacketNumber(0x7fL, 0L,
                new byte[]{(byte) 0x7f});
        checkEncodePacketNumber(0x80L, 0L,
                new byte[]{(byte) 0x00, (byte) 0x80});
        checkEncodePacketNumber(0xffL, 0L,
                new byte[]{(byte) 0x00, (byte) 0xff});
        checkEncodePacketNumber(0x100L, 0L,
                new byte[]{(byte) 0x01, (byte) 0x00});
        checkEncodePacketNumber(0x7FFFL, 0L,
                new byte[]{(byte) 0x7f, (byte) 0xFF});
        checkEncodePacketNumber(0x8000L, 0L,
                new byte[]{(byte) 0x00, (byte) 0x80, (byte) 0x00});
        checkEncodePacketNumber(0x7FFFFFL, 0L,
                new byte[]{(byte) 0x7f, (byte) 0xff, (byte) 0xFF});
        checkEncodePacketNumber(0x800000L, 0L,
                new byte[]{(byte) 0x00, (byte) 0x80, (byte) 0x00, (byte) 0x00});
        checkEncodePacketNumber(0x7FFFFFFFL, 0L,
                new byte[]{(byte) 0x7f, (byte) 0xff, (byte) 0xff, (byte) 0xFF});

        // Check some similar truncations.
        checkEncodePacketNumber(0x0101L, 0x82L,
                new byte[]{(byte) 0x01});
        checkEncodePacketNumber(0x0101L, 0x81L,
                new byte[]{(byte) 0x01, (byte) 0x01});
        checkEncodePacketNumber(0x10001L, 0xFF82,
                new byte[]{(byte) 0x01});
        checkEncodePacketNumber(0x10001L, 0xFF81,
                new byte[]{(byte) 0x00, (byte) 0x01});
        checkEncodePacketNumber(0x1000001L, 0xFFFF82,
                new byte[]{(byte) 0x01});
        checkEncodePacketNumber(0x1000001L, 0xFFFF81,
                new byte[]{(byte) 0x00, (byte) 0x01});

        // Check that > 4 bytes are not generated.
        try {
            checkEncodePacketNumber(0x80000000L, 0L,
                    new byte[]{(byte) 0x00, (byte) 0x80, (byte) 0x00,
                        (byte) 0x00, (byte) 0x00});
            throw new Exception("Shouldn't encode");
        } catch (RuntimeException e) {
            System.out.println("Caught the right exception!");
        }

        // Test decoding logic.

        // Test values from the upcoming QUIC RFC, Appendix A.3.
        checkDecodePacketNumber(0xa82f30eaL,
                ByteBuffer.wrap(new byte[]{(byte) 0x9b, (byte) 0x32}),
                2, 0xa82f9b32L);

        // TBD: More test values
    }

    public static void checkEncodePacketNumber(
            long fullPN, long largestAcked, byte[] bytes) throws Exception {

        byte[] answer = QuicPacketNumbers.encodePacketNumber(
                fullPN, largestAcked);

        if (!Arrays.equals(answer, bytes)) {
            throw new Exception("Encoding Problem");
        }
    }

    public static void checkDecodePacketNumber(
            long largestPN, ByteBuffer buf, int headerNumBytes,
            long answer) throws Exception {

        long result = QuicPacketNumbers.decodePacketNumber(
                largestPN, buf, headerNumBytes);

        if (result != answer) {
            throw new Exception("Decoding Problem");
        }
    }
}
