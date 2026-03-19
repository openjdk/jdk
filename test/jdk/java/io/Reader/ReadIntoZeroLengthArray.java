/*
 * Copyright (c) 2020, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8248383
 * @summary Ensure that zero is returned for read into zero length array
 * @run junit ReadIntoZeroLengthArray
 */
public class ReadIntoZeroLengthArray {
    private static File file;

    private static char[] cbuf0;
    private static char[] cbuf1;

    @BeforeAll
    public static void setup() throws IOException {
        file = File.createTempFile("foo", "bar", new File("."));

        cbuf0 = new char[0];
        cbuf1 = new char[1];
    }

    @AfterAll
    public static void teardown() throws IOException {
        file.delete();
    }

    public static Stream<Reader> readers() throws IOException {
        return Stream.of(new LineNumberReader(new FileReader(file)),
                         new CharArrayReader(new char[] {27}),
                         new PushbackReader(new FileReader(file)),
                         new FileReader(file),
                         new StringReader(new String(new byte[] {(byte)42})));
    }

    @ParameterizedTest
    @MethodSource("readers")
    void test0(Reader r) throws IOException {
        assertEquals(0, r.read(cbuf0));
    }

    @ParameterizedTest
    @MethodSource("readers")
    void test1(Reader r) throws IOException {
        assertEquals(0, r.read(cbuf1, 0, 0));
    }
}
