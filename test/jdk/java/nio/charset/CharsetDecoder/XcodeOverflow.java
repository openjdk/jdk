/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8272613
 * @summary Make sure IAE is not thrown on `int` overflow, turning negative
 *          size. The test should either not throw any Throwable, or an OOME
 *          with real Java heap space error (not "exceeds VM limit").
 * @modules java.base/jdk.internal.util
 * @requires sun.arch.data.model == "64"
 * @run junit/othervm XcodeOverflow
 */

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import jdk.internal.util.ArraysSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

public class XcodeOverflow {
    private static Stream<Arguments> sizes() {
        return Stream.of(
            // No overflow; no OOME.
            Arguments.of(ArraysSupport.SOFT_MAX_ARRAY_LENGTH),

            // overflow case: OOME w/ "Java heap space" is thrown on decoding
            Arguments.of(Integer.MAX_VALUE - 1000000)
        );
    }

    @ParameterizedTest
    @MethodSource("sizes")
    public void testEncodeOverflow(int size) throws CharacterCodingException {
        try {
            StandardCharsets.UTF_8
                    .newEncoder()
                    .encode(CharBuffer.wrap(new char[size], 0, size));
            System.out.println("Encoded without error");
        } catch (OutOfMemoryError oome) {
            if (oome.getMessage().equals("Java heap space")) {
                System.out.println("OOME for \"Java heap space\" is thrown correctly during encoding");
            } else {
                throw new RuntimeException("Unexpected OOME", oome);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("sizes")
    public void testDecodeOverflow(int size) throws CharacterCodingException {
        try {
            StandardCharsets.UTF_8
                .newDecoder()
                .decode(ByteBuffer.wrap(new byte[size], 0, size));
            System.out.println("Decoded without error");
        } catch (OutOfMemoryError oome) {
            if (oome.getMessage().equals("Java heap space")) {
                System.out.println("OOME for \"Java heap space\" is thrown correctly during decoding");
            } else {
                throw new RuntimeException("Unexpected OOME", oome);
            }
        }
    }
}
