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

import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedOutputStream;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/*
 * @test
 * @summary Basic API verification tests for GZIPOutputStream
 * @run junit BasicGZIPOutputStreamTest
 */
public class BasicGZIPOutputStreamTest {

    // Verify that the GZIPOutputStream constructors throw the expected NullPointerException
    private static Stream<Arguments> npeFromConstructors() {
        return Stream.of(
            ctorTestCase(() -> new GZIPOutputStream(null)),
            ctorTestCase(() -> new GZIPOutputStream(null, 1)),
            ctorTestCase(() -> new GZIPOutputStream(newValidOutput(), null, 1, true)),
            ctorTestCase(() -> new GZIPOutputStream(null, new Deflater(), 1, true)),
            ctorTestCase(() -> new GZIPOutputStream(null, null, 1, true)));
    }

    @ParameterizedTest
    @MethodSource("npeFromConstructors")
    public void testNPEFromConstructors(final Executable constructor) {
        Assertions.assertThrows(NullPointerException.class, constructor,
                "GZIPOutputStream constructor did not throw NullPointerException");
    }

    // Verify that the GZIPOutputStream constructors throw the expected IllegalArgumentException
    private static Stream<Arguments> iaeFromConstructors() {
        return Stream.of(
            ctorTestCase(() -> new GZIPOutputStream(newValidOutput(), 0)),
            ctorTestCase(() -> new GZIPOutputStream(newValidOutput(), 0, true)),
            ctorTestCase(() -> new GZIPOutputStream(newValidOutput(), new Deflater(), 0, true)));
    }

    @ParameterizedTest
    @MethodSource("iaeFromConstructors")
    public void testIAEFromConstructors(final Executable constructor) {
        Assertions.assertThrows(IllegalArgumentException.class, constructor,
                "GZIPOutputStream constructor did not throw IllegalArgumentException");
    }

    // Verify that the GZIPOutputStream constructors throw the expected IOException
    private static Stream<Arguments> ioeFromConstructors() {
        return Stream.of(
            ctorTestCase(() -> new GZIPOutputStream(newInvalidOutput())),
            ctorTestCase(() -> new GZIPOutputStream(newInvalidOutput(), true)),
            ctorTestCase(() -> new GZIPOutputStream(newInvalidOutput(), 1024)),
            ctorTestCase(() -> new GZIPOutputStream(newInvalidOutput(), 1024, true)),
            ctorTestCase(() -> new GZIPOutputStream(newInvalidOutput(), new Deflater(), 1024, true)));
    }

    @ParameterizedTest
    @MethodSource("ioeFromConstructors")
    public void testIOEFromConstructors(final Executable constructor) {
        Assertions.assertThrows(IOException.class, constructor,
                "GZIPOutputStream constructor did not throw IOException");
    }

// Helpers

    // This helps reduce clutter
    private static Arguments ctorTestCase(Executable ctor) {
        return Arguments.of(ctor);
    }

    // Create an OutputStream that always throws IOException
    private static OutputStream newInvalidOutput() {
        return new PipedOutputStream();         // unconnected, so it will always throw IOException
    }

    // Create a normal OutputStream
    private static OutputStream newValidOutput() {
        return new ByteArrayOutputStream();
    }
}
