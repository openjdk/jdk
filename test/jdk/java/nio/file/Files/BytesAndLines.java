/*
 * Copyright (c) 2011, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7006126 8020669 8024788 8019526
 * @build BytesAndLines PassThroughFileSystem
 * @run junit BytesAndLines
 * @summary Unit test for methods for Files readAllBytes, readAllLines and
 *     and write methods.
 * @key randomness
 */

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import static java.nio.charset.StandardCharsets.*;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class BytesAndLines {

    // data for text files
    private static final String EN_STRING = "The quick brown fox jumps over the lazy dog";
    private static final String JA_STRING = "\u65e5\u672c\u8a9e\u6587\u5b57\u5217";

    // used for random byte content
    private static Random RAND = new Random();

    // file used by most tests
    private static Path tmpfile;

    @BeforeAll
    static void setup() throws IOException {
        tmpfile = Files.createTempFile("blah", null);
    }

    @AfterAll
    static void cleanup() throws IOException {
        Files.deleteIfExists(tmpfile);
    }

    /**
     * Returns a byte[] of the given size with random content
     */
    private byte[] genBytes(int size) {
        byte[] arr = new byte[size];
        RAND.nextBytes(arr);
        return arr;
    }

    /**
     * Exercise NullPointerException
     */
    @Test
    public void testNulls() {
        Path file = Paths.get("foo");
        byte[] bytes = new byte[100];
        List<String> lines = Collections.emptyList();

        checkNullPointerException(() -> Files.readAllBytes(null));

        checkNullPointerException(() -> Files.write(null, bytes));
        checkNullPointerException(() -> Files.write(file, (byte[])null));
        checkNullPointerException(() -> Files.write(file, bytes, (OpenOption[])null));
        checkNullPointerException(() -> Files.write(file, bytes, new OpenOption[] { null } ));

        checkNullPointerException(() -> Files.readAllLines(null));
        checkNullPointerException(() -> Files.readAllLines(file, (Charset)null));
        checkNullPointerException(() -> Files.readAllLines(null, Charset.defaultCharset()));

        checkNullPointerException(() -> Files.write(null, lines));
        checkNullPointerException(() -> Files.write(file, (List<String>)null));
        checkNullPointerException(() -> Files.write(file, lines, (OpenOption[])null));
        checkNullPointerException(() -> Files.write(file, lines, new OpenOption[] { null } ));
        checkNullPointerException(() -> Files.write(null, lines, Charset.defaultCharset()));
        checkNullPointerException(() -> Files.write(file, null, Charset.defaultCharset()));
        checkNullPointerException(() -> Files.write(file, lines, (Charset)null));
        checkNullPointerException(() -> Files.write(file, lines, Charset.defaultCharset(), (OpenOption[])null));
        checkNullPointerException(() -> Files.write(file, lines, Charset.defaultCharset(), new OpenOption[] { null } ));
    }

    private void checkNullPointerException(Callable<?> c) {
        assertThrows(NullPointerException.class, () -> c.call());
    }

    /**
     * Exercise Files.readAllBytes(Path) on varied file sizes
     */
    @Test
    public void testReadAllBytes() throws IOException {
        int size = 0;
        while (size <= 16*1024) {
            testReadAllBytes(size);
            size += 512;
        }
    }

    private void testReadAllBytes(int size) throws IOException {
        // write bytes to file (random content)
        byte[] expected = genBytes(size);
        Files.write(tmpfile, expected);

        // check expected bytes are read
        byte[] read = Files.readAllBytes(tmpfile);
        assertArrayEquals(expected, read, "Bytes read not the same as written");
    }

    /**
     * Linux specific test to exercise Files.readAllBytes on /proc. This is
     * special because file sizes are reported as 0 even though the file
     * has content.
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testReadAllBytesOnProcFS() throws IOException {
        // read from procfs
        Path statFile = Paths.get("/proc/self/stat");
        byte[] data = Files.readAllBytes(statFile);
        assertTrue(data.length > 0,
                   "Files.readAllBytes('" + statFile + "') failed to read");
    }

    /**
     * Exercise Files.readAllBytes(Path) on custom file system. This is special
     * because readAllBytes was originally implemented to use FileChannel
     * and so may not be supported by custom file system providers.
     */
    @Test
    public void testReadAllBytesOnCustomFS() throws IOException {
        Path myfile = PassThroughFileSystem.create().getPath("myfile");
        try {
            int size = 0;
            while (size <= 1024) {
                byte[] b1 = genBytes(size);
                Files.write(myfile, b1);
                byte[] b2 = Files.readAllBytes(myfile);
                assertArrayEquals(b1, b2, "bytes not equal");
                size += 512;
            }
        } finally {
            Files.deleteIfExists(myfile);
        }
    }

    /**
     * Exercise Files.write(Path, byte[], OpenOption...) on various sizes
     */
    @Test
    public void testWriteBytes() throws IOException {
        int size = 0;
        while (size < 16*1024) {
            testWriteBytes(size, false);
            testWriteBytes(size, true);
            size += 512;
        }
    }

    private void testWriteBytes(int size, boolean append) throws IOException {
        byte[] bytes = genBytes(size);
        Path result = Files.write(tmpfile, bytes);
        assertSame(tmpfile, result);
        if (append) {
            Files.write(tmpfile, bytes, APPEND);
            assertEquals(size*2, Files.size(tmpfile));
        }

        byte[] expected;
        if (append) {
            expected = new byte[size << 1];
            System.arraycopy(bytes, 0, expected, 0, bytes.length);
            System.arraycopy(bytes, 0, expected, bytes.length, bytes.length);
        } else {
            expected = bytes;
        }

        byte[] read = Files.readAllBytes(tmpfile);
        assertArrayEquals(expected, read, "Bytes read not the same as written");
    }

    /**
     * Exercise Files.readAllLines(Path, Charset)
     */
    @Test
    public void testReadAllLines() throws IOException {
        // zero lines
        Files.write(tmpfile, new byte[0]);
        List<String> lines = Files.readAllLines(tmpfile, US_ASCII);
            assertTrue(lines.isEmpty(), "No line expected");

        // one line
        byte[] hi = { (byte)'h', (byte)'i' };
        Files.write(tmpfile, hi);
        lines = Files.readAllLines(tmpfile, US_ASCII);
        assertEquals(1, lines.size(), "One line expected");
        assertEquals("hi", lines.get(0), "'Hi' expected");

        // two lines using platform's line separator
        List<String> expected = Arrays.asList("hi", "there");
        Files.write(tmpfile, expected, US_ASCII);
        assertTrue(Files.size(tmpfile) > 0, "File is empty");
        lines = Files.readAllLines(tmpfile, US_ASCII);
        assertLinesMatch(expected, lines, "Unexpected lines");

        // MalformedInputException
        byte[] bad = { (byte)0xff, (byte)0xff };
        Files.write(tmpfile, bad);
        assertThrows(MalformedInputException.class,
                     () -> Files.readAllLines(tmpfile, US_ASCII),
                     "MalformedInputException expected");
    }

    /**
     * Linux specific test to exercise Files.readAllLines(Path) on /proc. This
     * is special because file sizes are reported as 0 even though the file
     * has content.
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testReadAllLinesOnProcFS() throws IOException {
        Path statFile = Paths.get("/proc/self/stat");
        List<String> lines = Files.readAllLines(statFile);
        assertTrue(lines.size() > 0,
                   "Files.readAllLines('" + statFile + "') failed to read");
    }

    /**
     * Exercise Files.readAllLines(Path)
     */
    @Test
    public void testReadAllLinesUTF8() throws IOException {
        Files.write(tmpfile, encodeAsUTF8(EN_STRING + "\n" + JA_STRING));

        List<String> lines = Files.readAllLines(tmpfile);
        assertEquals(2, lines.size(),
                     "Read " + lines.size() + " lines instead of 2");
        assertEquals(EN_STRING, lines.get(0));
        assertEquals(JA_STRING, lines.get(1));

        // a sample of malformed sequences
        testReadAllLinesMalformedUTF8((byte)0xFF); // one-byte sequence
        testReadAllLinesMalformedUTF8((byte)0xC0, (byte)0x80);  // invalid first byte
        testReadAllLinesMalformedUTF8((byte)0xC2, (byte)0x00); // invalid second byte
    }

    private byte[] encodeAsUTF8(String s) throws CharacterCodingException {
        // not using s.getBytes here so as to catch unmappable characters
        ByteBuffer bb = UTF_8.newEncoder().encode(CharBuffer.wrap(s));
        byte[] result = new byte[bb.limit()];
        bb.get(result);
        assertEquals(0, bb.remaining());
        return result;
    }

    private void testReadAllLinesMalformedUTF8(byte... bytes) throws IOException {
        Files.write(tmpfile, bytes);
        assertThrows(MalformedInputException.class,
                     () -> Files.readAllLines(tmpfile));
    }

    /**
     * Exercise Files.write(Path, Iterable<? extends CharSequence>, Charset, OpenOption...)
     */
    @Test
    public void testWriteLines() throws IOException {
        // zero lines
        Path result = Files.write(tmpfile, Collections.<String>emptyList(), US_ASCII);
        assertEquals(0, Files.size(tmpfile));
        assertSame(tmpfile, result);

        // two lines
        List<String> lines = Arrays.asList("hi", "there");
        Files.write(tmpfile, lines, US_ASCII);
        List<String> actual = Files.readAllLines(tmpfile, US_ASCII);
        assertLinesMatch(lines, actual, "Unexpected lines");

        // append two lines
        Files.write(tmpfile, lines, US_ASCII, APPEND);
        List<String> expected = new ArrayList<>();
        expected.addAll(lines);
        expected.addAll(lines);
        assertEquals(4, expected.size());
        actual = Files.readAllLines(tmpfile, US_ASCII);
        assertLinesMatch(expected, actual, "Unexpected lines");

        // UnmappableCharacterException
        String s = "\u00A0\u00A1";
        assertThrows(UnmappableCharacterException.class,
                     () -> Files.write(tmpfile, Arrays.asList(s), US_ASCII));
    }

    /**
     * Exercise Files.write(Path, Iterable<? extends CharSequence>, OpenOption...)
     */
    @Test
    public void testWriteLinesUTF8() throws IOException {
        List<String> lines = Arrays.asList(EN_STRING, JA_STRING);
        Files.write(tmpfile, lines);
        List<String> actual = Files.readAllLines(tmpfile, UTF_8);
        assertLinesMatch(lines, actual, "Unexpected lines");
    }
}
