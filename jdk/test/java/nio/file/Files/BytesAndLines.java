/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7006126
 * @summary Unit test for methods for Files readAllBytes, readAllLines and
 *     and write methods.
 */

import java.nio.file.*;
import static java.nio.file.Files.*;
import java.io.*;
import java.util.*;
import java.nio.charset.*;

public class BytesAndLines {
    static final Random rand = new Random();

    static final Charset US_ASCII = Charset.forName("US-ASCII");

    public static void main(String[] args) throws IOException {
        testReadAndWriteBytes();
        testReadLines();
        testWriteLines();
    }

    /**
     * Test readAllBytes(Path) and write(Path, byte[], OpenOption...)
     */
    static void testReadAndWriteBytes() throws IOException {
        // exercise methods with various sizes
        testReadAndWriteBytes(0);
        for (int i=0; i<100; i++) {
            testReadAndWriteBytes(rand.nextInt(32000));
        }

        // NullPointerException
        Path file = Paths.get("foo");
        List<String> lines = Collections.emptyList();
        try {
            readAllBytes(null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            write(null, lines, Charset.defaultCharset());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            write(file, null, Charset.defaultCharset());
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            write(file, lines, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            write(file, lines, Charset.defaultCharset(), (OpenOption[])null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            OpenOption[] opts = { null };
            write(file, lines, Charset.defaultCharset(), opts);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
    }


    static void testReadAndWriteBytes(int size) throws IOException {
        Path path = createTempFile("blah", null);
        try {
            boolean append = rand.nextBoolean();

            byte[] b1 = new byte[size];
            rand.nextBytes(b1);

            byte[] b2 = (append) ? new byte[size] : new byte[0];
            rand.nextBytes(b2);

            // write method should create file if it doesn't exist
            if (rand.nextBoolean())
                delete(path);

            // write bytes to file
            Path target = write(path, b1);
            assertTrue(target==path, "Unexpected path");
            assertTrue(size(path) == b1.length, "Unexpected file size");

            // append bytes to file (might be 0 bytes)
            write(path, b2, StandardOpenOption.APPEND);
            assertTrue(size(path) == b1.length + b2.length, "Unexpected file size");

            // read entire file
            byte[] read = readAllBytes(path);

            // check bytes are correct
            byte[] expected;
            if (append) {
                expected = new byte[b1.length + b2.length];
                System.arraycopy(b1, 0, expected, 0, b1.length);
                System.arraycopy(b2, 0, expected, b1.length, b2.length);
            } else {
                expected = b1;
            }
            assertTrue(Arrays.equals(read, expected),
                       "Bytes read not the same as bytes written");
        } finally {
            deleteIfExists(path);
        }
    }

    /**
     * Test readAllLines(Path,Charset)
     */
    static void testReadLines() throws IOException {
        Path tmpfile = createTempFile("blah", "txt");
        try {
            List<String> lines;

            // zero lines
            assertTrue(size(tmpfile) == 0, "File should be empty");
            lines = readAllLines(tmpfile, US_ASCII);
            assertTrue(lines.isEmpty(), "No line expected");

            // one line
            byte[] hi = { (byte)'h', (byte)'i' };
            write(tmpfile, hi);
            lines = readAllLines(tmpfile, US_ASCII);
            assertTrue(lines.size() == 1, "One line expected");
            assertTrue(lines.get(0).equals("hi"), "'Hi' expected");

            // two lines using platform's line separator
            List<String> expected = Arrays.asList("hi", "there");
            write(tmpfile, expected, US_ASCII);
            assertTrue(size(tmpfile) > 0, "File is empty");
            lines = readAllLines(tmpfile, US_ASCII);
            assertTrue(lines.equals(expected), "Unexpected lines");

            // MalformedInputException
            byte[] bad = { (byte)0xff, (byte)0xff };
            write(tmpfile, bad);
            try {
                readAllLines(tmpfile, US_ASCII);
                throw new RuntimeException("MalformedInputException expected");
            } catch (MalformedInputException ignore) { }


            // NullPointerException
            try {
                readAllLines(null, US_ASCII);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }
            try {
                readAllLines(tmpfile, null);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }

        } finally {
            delete(tmpfile);
        }
    }

    /**
     * Test write(Path,Iterable<? extends CharSequence>,Charset,OpenOption...)
     */
    static void testWriteLines() throws IOException {
        Path tmpfile = createTempFile("blah", "txt");
        try {
            // write method should create file if it doesn't exist
            if (rand.nextBoolean())
                delete(tmpfile);

            // zero lines
            Path result = write(tmpfile, Collections.<String>emptyList(), US_ASCII);
            assert(size(tmpfile) == 0);
            assert(result == tmpfile);

            // two lines
            List<String> lines = Arrays.asList("hi", "there");
            write(tmpfile, lines, US_ASCII);
            List<String> actual = readAllLines(tmpfile, US_ASCII);
            assertTrue(actual.equals(lines), "Unexpected lines");

            // append two lines
            write(tmpfile, lines, US_ASCII, StandardOpenOption.APPEND);
            List<String> expected = new ArrayList<String>();
            expected.addAll(lines);
            expected.addAll(lines);
            assertTrue(expected.size() == 4, "List should have 4 elements");
            actual = readAllLines(tmpfile, US_ASCII);
            assertTrue(actual.equals(expected), "Unexpected lines");

            // UnmappableCharacterException
            try {
                String s = "\u00A0\u00A1";
                write(tmpfile, Arrays.asList(s), US_ASCII);
                throw new RuntimeException("UnmappableCharacterException expected");
            } catch (UnmappableCharacterException ignore) { }

            // NullPointerException
            try {
                write(null, lines, US_ASCII);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }
            try {
                write(tmpfile, null, US_ASCII);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }
            try {
                write(tmpfile, lines, null);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }
            try {
                write(tmpfile, lines, US_ASCII, (OpenOption[])null);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }
            try {
                OpenOption[] opts = { (OpenOption)null };
                write(tmpfile, lines, US_ASCII, opts);
                throw new RuntimeException("NullPointerException expected");
            } catch (NullPointerException ignore) { }

        } finally {
            delete(tmpfile);
        }

    }

    static void assertTrue(boolean expr, String errmsg) {
        if (!expr)
            throw new RuntimeException(errmsg);
    }
}
