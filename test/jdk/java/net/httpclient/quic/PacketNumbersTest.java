/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jdk.internal.net.http.quic.packets.QuicPacketNumbers;

import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @run junit ${test.main.class}
 */
public class PacketNumbersTest {

    record EncodeResult(String expected, boolean assertion, Class<? extends Throwable> failure) {
        public static EncodeResult illegal() {
            return new EncodeResult(null, false, IllegalArgumentException.class);
        }
        public static EncodeResult asserting() {
            return new EncodeResult(null, true, AssertionError.class);
        }
        public static EncodeResult success(String result) {
            return new EncodeResult(result, false, null);
        }
        public static EncodeResult fail(Class<? extends Throwable> failure) {
            return new EncodeResult(null, false, failure);
        }
        public boolean fail() {
            return failure() != null;
        }

        @Override
        public String toString() {
            return fail() ? failure.getSimpleName()
                    : String.valueOf(expected);
        }
    }
    record TestCase(String desc, long fullPN, long largestAck, EncodeResult result) {
        static AtomicInteger count = new AtomicInteger();
        TestCase {
            desc = count.incrementAndGet() + " - expecting " + desc;
        }
        public byte[] encode() {
            return QuicPacketNumbers.encodePacketNumber(fullPN(), largestAck());
        }

        public long decode() {
            byte[] encoded = encode();
            var largestProcessed = largestAck();
            return QuicPacketNumbers.decodePacketNumber(largestProcessed,
                ByteBuffer.wrap(encoded), encoded.length);
        }

        @Override
        public String toString() {
            return "%s: (%d, %d) -> %s".formatted(desc, fullPN, largestAck, result);
        }
    }

    static Stream<TestCase> encode() {
        return Stream.of(
                // these first three test cases are extracted from RFC 9000, appendix A.2 and A.3
                new TestCase("success",  0xa82f9b32L, 0xa82f30eaL, EncodeResult.success("9B32")),
                new TestCase("success",  0xace8feL, 0xabe8b3L, EncodeResult.success("ACE8FE")),
                new TestCase("success",  0xac5c02L, 0xabe8b3L, EncodeResult.success("5C02")),
                // additional test cases - these have been obtained empirically to test at the limits
                new TestCase("success",  0x7FFFFFFFFFFFL, 0x7FFFFFFFFF00L, EncodeResult.success("FFFF")),
                new TestCase("success",  0xFFFFFFFFFFL, 0xFFFFFFFF00L, EncodeResult.success("FFFF")),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFFFFFEL, EncodeResult.success("FF")),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFFFF00L, EncodeResult.success("FFFF")),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFF0000L, EncodeResult.success("FFFFFF")),
                new TestCase("success",  0xFFFFFFFFL, 0xFF000000L, EncodeResult.success("FFFFFFFF")),
                new TestCase("success",  0xFFFFFFFFL, 0xF0000000L, EncodeResult.success("FFFFFFFF")),
                new TestCase("success",  0xFFFFFFFFL, 0x80000000L, EncodeResult.success("FFFFFFFF")),
                new TestCase("illegal(5)",0xFFFFFFFFL, 0x7FFFFFFFL, EncodeResult.illegal()),
                new TestCase("success",  0x8FFFFFFFL, 0x10000000L, EncodeResult.success("8FFFFFFF")),
                new TestCase("illegal(5)",0x8FFFFFFFL, 0x0FFFFFFFL, EncodeResult.illegal()),
                new TestCase("illegal(5)",0x8FFFFFFFL, 256L, EncodeResult.illegal()),
                new TestCase("success",  0x7FFFFFFFL, 255L, EncodeResult.success("7FFFFFFF")),
                new TestCase("success",  0x7FFFFFFFL, 0L, EncodeResult.success("7FFFFFFF")),
                new TestCase("illegal(5)",0x7FFFFFFFL, -1L, EncodeResult.illegal()),
                new TestCase("illegal(5)",0x80000000L, 0L, EncodeResult.illegal()),
                new TestCase("success",  0x6FFFFFFFL, 0L, EncodeResult.success("6FFFFFFF")),
                new TestCase("success",  0xFFFFFFL, 0L, EncodeResult.success("00FFFFFF")),
                new TestCase("success",  0xFFFFL, 0L, EncodeResult.success("00FFFF")),
                new TestCase("success",  255L, 0L, EncodeResult.success("00FF")),
                new TestCase("success",  1L, 0L, EncodeResult.success("01")),
                new TestCase("success",  0x6FFFFFFFL, -1L, EncodeResult.success("6FFFFFFF")),
                new TestCase("success",  0xFFFFFFL, -1L, EncodeResult.success("00FFFFFF")),
                new TestCase("success",  0xFFFFL, -1L, EncodeResult.success("00FFFF")),
                new TestCase("success",  255L, -1L, EncodeResult.success("00FF")),
                new TestCase("success",  1L, -1L, EncodeResult.success("01")),
                new TestCase("success",  0L, -1L, EncodeResult.success("00")),
                new TestCase("assert",   0L, 1L, EncodeResult.asserting()),
                new TestCase("assert",   0L, 0L, EncodeResult.asserting()),
                new TestCase("assert",   1L, 1L, EncodeResult.asserting()),
                // Various other checks for first-packet encodings.
                new TestCase("success",  5L, -1L, EncodeResult.success("05")),
                new TestCase("success",  0x7EL, -1L, EncodeResult.success("7E")),
                new TestCase("success",  0x7FL, -1L, EncodeResult.success("007F")),
                new TestCase("success",  0x80L, -1L, EncodeResult.success("0080")),
                new TestCase("success",  0x100L, -1L, EncodeResult.success("0100")),
                // Various checks for a packet 0 that has been ack'd.
                new TestCase("success",  0x7FL, 0L, EncodeResult.success("7F")),
                new TestCase("success",  0x80L, 0L, EncodeResult.success("0080")),
                new TestCase("success",  0x100L, 0L, EncodeResult.success("0100")),
                new TestCase("success",  0x7FFFL, 0L, EncodeResult.success("7FFF")),
                new TestCase("success",  0x8000L, 0L, EncodeResult.success("008000")),
                new TestCase("success",  0x7FFFFFL, 0L, EncodeResult.success("7FFFFF")),
                new TestCase("success",  0x800000L, 0L, EncodeResult.success("00800000")),
                // Check some similar truncations.
                new TestCase("success",  0x0101L, 0x82L, EncodeResult.success("01")),
                new TestCase("success",  0x0101L, 0x81L, EncodeResult.success("0101")),
                new TestCase("success",  0x010001L, 0xFF82L, EncodeResult.success("01")),
                new TestCase("success",  0x010001L, 0xFF81L, EncodeResult.success("0001")),
                new TestCase("success",  0x01000001L, 0xFFFF82L, EncodeResult.success("01")),
                new TestCase("success",  0x01000001L, 0xFFFF81L, EncodeResult.success("0001"))
        );
    }

    @ParameterizedTest
    @MethodSource("encode")
    void testEncodePacketNumber(TestCase test) {
        System.out.println(test);
        if (test.result().assertion()) {
            Assumptions.assumeTrue(QuicPacketNumbers.class.desiredAssertionStatus(), "needs assertion enabled (-ea)");
        }
        if (test.result().fail()) {
            Throwable t = Assertions.assertThrows(test.result().failure(), test::encode);
            System.out.println("Got expected exception: " + t);
            return;
        }
        byte[] res = test.encode();
        String expected = HexFormat.of().withUpperCase().formatHex(res);

        // encode the full PN - check that the truncated PN == expected
        assertEquals(test.result().expected(), expected);
        // check that decode(encoded) == fullPN
        assertEquals(test.fullPN(), test.decode());
    }
}
