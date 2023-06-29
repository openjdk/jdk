/*
 * Copyright (c) 2010, 2023, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @summary Unit test for encode/decode convenience methods
 * @run junit EncDec
 */

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class EncDec {

    /**
     * Test that the input String is the same after round tripping
     * the Charset.encode() and Charset.decode() methods.
     */
    @ParameterizedTest
    @MethodSource("stringProvider")
    public void roundTripTest(String pre) {
        ByteBuffer bb = ByteBuffer.allocate(100);
        Charset preCs = Charset.forName("ISO-8859-15");
        if (!preCs.canEncode()) {
            throw new RuntimeException("Error: Trying to test encode and " +
                    "decode methods on a charset that does not support encoding");
        }
        bb.put(preCs.encode(pre)).flip();
        String post = Charset.forName("UTF-8").decode(bb).toString();
        assertEquals(pre, post, "Mismatch after encoding + decoding, :");
    }

    static Stream<String> stringProvider() {
        return Stream.of(
                "Hello, world!",
                "apple, banana, orange",
                "car, truck, horse");
    }
}
