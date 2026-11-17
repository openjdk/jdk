/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/*
 * @test
 * @summary basic API verification tests for GZIPInputStream
 * @run junit BasicGZIPInputStreamTest
 */
public class BasicGZIPInputStreamTest {


    private static Stream<Arguments> npeFromConstructors() {
        return Stream.of(Arguments.of((Executable) () -> new GZIPInputStream(null)),
                Arguments.of((Executable) () -> new GZIPInputStream(null, 1)));
    }

    /*
     * Verifies that the GZIPInputStream constructors throw the expected NullPointerException
     */
    @ParameterizedTest
    @MethodSource("npeFromConstructors")
    public void testNPEFromConstructors(final Executable constructor) {
        assertThrows(NullPointerException.class, constructor,
                "GZIPInputStream constructor did not throw NullPointerException");
    }

    private static Stream<Arguments> iaeFromConstructors() {
        return Stream.of(
                Arguments.of((Executable) () -> new GZIPInputStream(
                        new ByteArrayInputStream(new byte[0]), 0)),
                Arguments.of((Executable) () -> new GZIPInputStream(
                        new ByteArrayInputStream(new byte[0]), -1)),
                Arguments.of((Executable) () -> new GZIPInputStream(
                        new ByteArrayInputStream(new byte[0]), -42)));
    }

    /*
     * Verifies that the GZIPInputStream constructors throw the expected IllegalArgumentException
     */
    @ParameterizedTest
    @MethodSource("iaeFromConstructors")
    public void testIAEFromConstructors(final Executable constructor) {
        assertThrows(IllegalArgumentException.class, constructor,
                "GZIPInputStream constructor did not throw IllegalArgumentException");
    }

    private static Stream<Arguments> ioeFromConstructors() {
        final ByteArrayInputStream notGZIPContent = new ByteArrayInputStream(new byte[0]);
        return Stream.of(
                Arguments.of((Executable) () -> new GZIPInputStream(notGZIPContent)),
                Arguments.of((Executable) () -> new GZIPInputStream(
                        notGZIPContent, 1024 /* buffer size */)));
    }

    /*
     * Verifies that the GZIPInputStream constructors throw the expected IOException
     */
    @ParameterizedTest
    @MethodSource("ioeFromConstructors")
    public void testIOEFromConstructors(final Executable constructor) {
        assertThrows(IOException.class, constructor,
                "GZIPInputStream constructor did not throw IOException");
    }

    /*
     * Verifies that GZIPInputStream.read() throws IOException when invoked on a closed
     * stream
     */
    @Test
    void testClosedStreamRead() throws Exception {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
            gzos.write(new byte[] {0x42, 0x42}); // GZIP compress these input bytes
        }
        final byte[] gzipCompressed = baos.toByteArray();
        // create the GZIPInputStream to test
        final GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipCompressed));
        in.close();
        final IOException ioe = assertThrows(IOException.class, () -> in.read(new byte[1], 0, 1));
        final String exMessage = ioe.getMessage();
        if (exMessage == null || !exMessage.contains("Stream closed")) {
            // unexpected exception message, propagate the original exception
            throw ioe;
        }
    }
}
