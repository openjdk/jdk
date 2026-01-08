/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import jdk.internal.net.http.quic.packets.QuicPacketNumbers;
import org.testng.SkipException;
import org.testng.annotations.Test;
import org.testng.annotations.DataProvider;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

/**
 * @test
 * @run testng PacketNumbersTest
 */
public class PacketNumbersTest {

    record EncodeResult(int expected, boolean assertion, Class<? extends Throwable> failure) {
        public static EncodeResult illegal() {
            return new EncodeResult(-1, true, IllegalArgumentException.class);
        }
        public static EncodeResult asserting() {
            return new EncodeResult(-1, true, AssertionError.class);
        }
        public static EncodeResult success(int result) {
            return new EncodeResult(result, false, null);
        }
        public static EncodeResult fail(Class<? extends Throwable> failure) {
            return new EncodeResult(-1, false, failure);
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

    @DataProvider
    public Object[][] encode() {
        return List.of(
                // these first three test cases are extracted from RFC 9000, appendix A.2 and A.3
                new TestCase("success",  0xa82f9b32L, 0xa82f30eaL, EncodeResult.success(0x9b32)),
                new TestCase("success",  0xace8feL, 0xabe8b3L, EncodeResult.success(0xace8fe & 0xFFFFFF)),
                new TestCase("success",  0xac5c02L, 0xabe8b3L, EncodeResult.success(0xac5c02 & 0xFFFF)),
                // additional test cases - these have been obtained empirically to test at the limits
                new TestCase("success",  0x7FFFFFFFFFFFL, 0x7FFFFFFFFF00L, EncodeResult.success(0x0000FFFF)),
                new TestCase("success",  0xFFFFFFFFFFL, 0xFFFFFFFF00L, EncodeResult.success(0x0000FFFF)),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFFFFFEL, EncodeResult.success(0x000000FF)),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFFFF00L, EncodeResult.success(0x0000FFFF)),
                new TestCase("success",  0xFFFFFFFFL, 0xFFFF0000L, EncodeResult.success(0x00FFFFFF)),
                new TestCase("success",  0xFFFFFFFFL, 0xFF000000L, EncodeResult.success(0xFFFFFFFF)),
                new TestCase("success",  0xFFFFFFFFL, 0xF0000000L, EncodeResult.success(0xFFFFFFFF)),
                new TestCase("success",  0xFFFFFFFFL, 0x80000000L, EncodeResult.success(0xFFFFFFFF)),
                new TestCase("illegal(5)",0xFFFFFFFFL, 0x7FFFFFFFL, EncodeResult.illegal()),
                new TestCase("success",  0x8FFFFFFFL, 0x10000000L, EncodeResult.success(0x8FFFFFFF)),
                new TestCase("illegal(5)",0x8FFFFFFFL, 0x0FFFFFFFL, EncodeResult.illegal()),
                new TestCase("illegal(5)",0x8FFFFFFFL, 256L, EncodeResult.illegal()),
                new TestCase("success",  0x7FFFFFFFL, 255L, EncodeResult.success(0x7FFFFFFF)),
                new TestCase("success",  0x7FFFFFFFL, 0L, EncodeResult.success(0x7FFFFFFF)),
                new TestCase("illegal(5)",0x7FFFFFFFL, -1L, EncodeResult.illegal()),
                new TestCase("success",  0x6FFFFFFFL, 0L, EncodeResult.success(0x6FFFFFFF)),
                new TestCase("success",  0xFFFFFFL, 0L, EncodeResult.success(0xFFFFFF)),
                new TestCase("success",  0xFFFFL, 0L, EncodeResult.success(0xFFFF)),
                new TestCase("success",  255L, 0L, EncodeResult.success(255)),
                new TestCase("success",  1L, 0L, EncodeResult.success(1)),
                new TestCase("success",  0x6FFFFFFFL, -1L, EncodeResult.success(0x6FFFFFFF)),
                new TestCase("success",  0xFFFFFFL, -1L, EncodeResult.success(0xFFFFFF)),
                new TestCase("success",  0xFFFFL, -1L, EncodeResult.success(0xFFFF)),
                new TestCase("success",  255L, -1L, EncodeResult.success(255)),
                new TestCase("success",  1L, -1L, EncodeResult.success(1)),
                new TestCase("success",  0L, -1L, EncodeResult.success(0)),
                new TestCase("assert",   0L, 1L, EncodeResult.asserting()),
                new TestCase("assert",   0L, 0L, EncodeResult.asserting()),
                new TestCase("assert",   1L, 1L, EncodeResult.asserting())
        ).stream().map(Stream::of)
                .map(Stream::toArray)
                .toArray(Object[][]::new);
    }

    @Test(dataProvider = "encode")
    public void testEncodePacketNumber(TestCase test) {
        System.out.println(test);
        if (test.result().assertion()) {
            if (!QuicPacketNumbers.class.desiredAssertionStatus()) {
                throw new SkipException("needs assertion enabled (-esa)");
            }
            Throwable t = expectThrows(test.result().failure(), test::encode);
            System.out.println("Got expected assertion: " + t);
            return;
        }
        if (test.result().fail()) {
            Throwable t = expectThrows(test.result().failure(), test::encode);
            System.out.println("Got expected exception: " + t);
            return;

        }
        byte[] res = test.encode();
        int truncated = 0;
        for (int i=0; i<res.length; i++) {
            truncated = truncated << 8;
            truncated = truncated | (res[i] & 0xFF);
        }

        // encode the full PN - check that the truncated PN == expected
        assertEquals(truncated, test.result().expected());
        // check that decode(encoded) == fullPN
        assertEquals(test.decode(), test.fullPN());
    }
}
