/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_32;
import static java.nio.charset.StandardCharsets.UTF_32BE;
import static java.nio.charset.StandardCharsets.UTF_32LE;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/* @test
 * @bug 8201276 8205058 8209576 8287541 8288589 8325590
 * @build ReadWriteString PassThroughFileSystem
 * @run junit ReadWriteString
 * @summary Unit test for methods for Files readString and write methods.
 * @key randomness
 * @modules jdk.charsets
 */
public class ReadWriteString {

    // data for text files
    final static String TEXT_UNICODE = "\u201CHello\u201D";
    final static String TEXT_ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n abcdefghijklmnopqrstuvwxyz\n 1234567890\n";
    final static String TEXT_PERSON_CART_WHEELING = "\ud83e\udd38";
    private static final String JA_STRING = "\u65e5\u672c\u8a9e\u6587\u5b57\u5217";
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final Charset WINDOWS_31J = Charset.forName("windows-31j");

    static byte[] data = getData();

    static byte[] getData() {
        try {
            String str1 = "A string that contains ";
            String str2 = " , an invalid character for UTF-8.";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(str1.getBytes());
            baos.write(0xFA);
            baos.write(str2.getBytes());
            return baos.toByteArray();
        } catch (IOException ex) {
            // in case it happens, fail the test
            fail(ex);
            return null; // appease the compiler
        }
    }

    // file used by testReadWrite, testReadString and testWriteString
    private static Path[] testFiles = new Path[3];

    /*
     * MethodSource for malformed write test. Provides the following fields:
     * file path, malformed input string, charset
     */
    public static Stream<Arguments> getMalformedWrite() throws IOException {
        Path path = Files.createFile(Path.of("malformedWrite"));
        return Stream.of
            (Arguments.of(path, "\ud800", null),  //the default Charset is UTF_8
             Arguments.of(path, "\u00A0\u00A1", US_ASCII),
             Arguments.of(path, "\ud800", UTF_8),
             Arguments.of(path, JA_STRING, ISO_8859_1),
             Arguments.of(path, "\u041e", WINDOWS_1252), // cyrillic capital letter O
             Arguments.of(path, "\u091c", WINDOWS_31J)); // devanagari letter ja
    }

    /*
     * MethodSource for illegal input test
     * Writes the data in ISO8859 and reads with UTF_8, expects MalformedInputException
     */
    public static Stream<Arguments> getIllegalInput() throws IOException {
        Path path = Files.createFile(Path.of("illegalInput"));
        return Stream.of(Arguments.of(path, data, ISO_8859_1, null),
                         Arguments.of(path, data, ISO_8859_1, UTF_8));
    }

    /*
     * MethodSource for illegal input bytes test
     */
    public static Stream<Arguments> getIllegalInputBytes() throws IOException {
        return Stream.of
            (Arguments.of(new byte[] {(byte)0x00, (byte)0x20, (byte)0x00}, UTF_16, MalformedInputException.class),
             Arguments.of(new byte[] {-50}, UTF_16, MalformedInputException.class),
             Arguments.of(new byte[] {(byte)0x81}, WINDOWS_1252, UnmappableCharacterException.class), // unused in Cp1252
             Arguments.of(new byte[] {(byte)0x81, (byte)0xff}, WINDOWS_31J, UnmappableCharacterException.class)); // invalid trailing byte
    }

    /*
     * MethodSource for writeString test
     * Writes the data using both the existing and new method and compares the results.
     */
    public static Stream<Arguments> getWriteString() {
        return Stream.of
            (Arguments.of(testFiles[1], testFiles[2], TEXT_ASCII, US_ASCII, null),
             Arguments.of(testFiles[1], testFiles[2], TEXT_ASCII, US_ASCII, US_ASCII),
             Arguments.of(testFiles[1], testFiles[2], TEXT_UNICODE, UTF_8, null),
             Arguments.of(testFiles[1], testFiles[2], TEXT_UNICODE, UTF_8, UTF_8));
    }

    /*
     * MethodSource for readString test
     * Reads the file using both the existing and new method and compares the results.
     */
    public static Stream<Arguments> getReadString() {
        return Stream.of
            (Arguments.of(testFiles[1], TEXT_ASCII, US_ASCII, US_ASCII),
             Arguments.of(testFiles[1], TEXT_ASCII, US_ASCII, UTF_8),
             Arguments.of(testFiles[1], TEXT_UNICODE, UTF_8, null),
             Arguments.of(testFiles[1], TEXT_UNICODE, UTF_8, UTF_8),
             Arguments.of(testFiles[1], TEXT_ASCII, US_ASCII, ISO_8859_1),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16, UTF_16),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16BE, UTF_16BE),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16LE, UTF_16LE),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32, UTF_32),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32BE, UTF_32BE),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32LE, UTF_32LE),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, WINDOWS_1252, WINDOWS_1252),
             Arguments.of(testFiles[1], TEXT_PERSON_CART_WHEELING, WINDOWS_31J, WINDOWS_31J));
    }

    @BeforeAll
    static void setup() throws IOException {
        testFiles[0] = Files.createFile(Path.of("readWriteString"));
        testFiles[1] = Files.createFile(Path.of("writeString_file1"));
        testFiles[2] = Files.createFile(Path.of("writeString_file2"));
    }

    /**
     * Verifies that NPE is thrown when one of the parameters is null.
     */
    @Test
    public void testNulls() {
        Path path = Paths.get("foo");
        String s = "abc";

        checkNullPointerException(() -> Files.readString((Path) null));
        checkNullPointerException(() -> Files.readString((Path) null, UTF_8));
        checkNullPointerException(() -> Files.readString(path, (Charset) null));

        checkNullPointerException(() -> Files.writeString((Path) null, s, CREATE));
        checkNullPointerException(() -> Files.writeString(path, (CharSequence) null, CREATE));
        checkNullPointerException(() -> Files.writeString(path, s, (OpenOption[]) null));

        checkNullPointerException(() -> Files.writeString((Path) null, s, UTF_8, CREATE));
        checkNullPointerException(() -> Files.writeString(path, (CharSequence) null, UTF_8, CREATE));
        checkNullPointerException(() -> Files.writeString(path, s, (Charset) null, CREATE));
        checkNullPointerException(() -> Files.writeString(path, s, UTF_8, (OpenOption[]) null));
    }

    /**
     * Verifies the readString and write String methods. Writes to files Strings
     * of various sizes, with/without specifying the Charset, and then compares
     * the result of reading the files.
     */
    @Test
    public void testReadWrite() throws IOException {
        int size = 0;
        while (size < 16 * 1024) {
            testReadWrite(size, null, false);
            testReadWrite(size, null, true);
            testReadWrite(size, UTF_8, false);
            testReadWrite(size, UTF_8, true);
            size += 1024;
        }
    }

    /**
     * Verifies fix for @bug 8209576 that the writeString method converts the
     * bytes properly.
     * This method compares the results written by the existing write method and
     * the writeString method added since 11.
     */
    @ParameterizedTest
    @MethodSource("getWriteString")
    public void testWriteString(Path path, Path path2, String text, Charset cs, Charset cs2) throws IOException {
        Files.write(path, text.getBytes(cs));

        // writeString @since 11
        if (cs2 == null) {
            Files.writeString(path2, text);
        } else {
            Files.writeString(path2, text, cs2);
        }
        byte[] bytes = Files.readAllBytes(path);
        byte[] bytes2 = Files.readAllBytes(path2);
        assertArrayEquals(bytes2, bytes, "The bytes should be the same");
    }

    /**
     * Verifies that the readString method added since 11 behaves the same as
     * constructing a string from the existing readAllBytes method.
     */
    @ParameterizedTest
    @MethodSource("getReadString")
    public void testReadString(Path path, String text, Charset cs, Charset cs2) throws IOException {
        Files.write(path, text.getBytes(cs));
        String str = new String(Files.readAllBytes(path), cs);

        // readString @since 11
        String str2 = (cs2 == null) ? Files.readString(path) :
                                      Files.readString(path, cs2);
        assertEquals(str, str2, "The strings should be the same");
    }

    /**
     * Verifies that IOException is thrown (as specified) when giving a malformed
     * string input.
     *
     * @param path the path to write
     * @param s the string
     * @param cs the Charset
     * @throws IOException if the input is malformed
     */
    @ParameterizedTest
    @MethodSource("getMalformedWrite")
    public void testMalformedWrite(Path path, String s, Charset cs) throws IOException {
        assertThrows(UnmappableCharacterException.class,
                     () -> {
                         if (cs == null) {
                             Files.writeString(path, s);
                         } else {
                             Files.writeString(path, s, cs);
                         }
                     });
    }

    /**
     * Verifies that IOException is thrown when reading a file using the wrong
     * Charset.
     *
     * @param path the path to write and read
     * @param data the data used for the test
     * @param csWrite the Charset to use for writing the test file
     * @param csRead the Charset to use for reading the file
     * @throws IOException when the Charset used for reading the file is incorrect
     */
    @ParameterizedTest
    @MethodSource("getIllegalInput")
    public void testMalformedRead(Path path, byte[] data, Charset csWrite, Charset csRead) throws IOException {
        String temp = new String(data, csWrite);
        Files.writeString(path, temp, csWrite);
        assertThrows(MalformedInputException.class,
                     () -> {
                         if (csRead == null) {
                             Files.readString(path);
                         } else {
                             Files.readString(path, csRead);
                         }
                     });
    }

    /**
     * Verifies that IOException is thrown when reading a file containing
     * illegal bytes
     *
     * @param data the data used for the test
     * @param csRead the Charset to use for reading the file
     * @param expected exception class
     * @throws IOException when the Charset used for reading the file is incorrect
     */
    @ParameterizedTest
    @MethodSource("getIllegalInputBytes")
    public void testMalformedReadBytes(byte[] data, Charset csRead, Class<CharacterCodingException> expected)
            throws IOException {
        Path path = Path.of("illegalInputBytes");
        Files.write(path, data);
        try {
            Files.readString(path, csRead);
        } catch (MalformedInputException e) {
            assertInstanceOf(MalformedInputException.class, e);
        } catch (UnmappableCharacterException e) {
            assertInstanceOf(UnmappableCharacterException.class, e);
        }
    }

    // Verify File.readString with UTF16 to confirm proper string length and contents.
    // A regression test for 8325590
    @Test
    public void testSingleUTF16() throws IOException {
        String original = "🤸";    // "\ud83e\udd38";
        Files.writeString(testFiles[0], original, UTF_16);
        String actual = Files.readString(testFiles[0], UTF_16);
        if (!original.equals(actual)) {
            System.out.printf("expected (%s), was (%s)\n", original, actual);
            System.out.printf("expected UTF_16 bytes: %s\n", Arrays.toString(original.getBytes(UTF_16)));
            System.out.printf("actual UTF_16 bytes: %s\n", Arrays.toString(actual.getBytes(UTF_16)));
        }
        assertEquals(original, actual, "Round trip string mismatch with multi-byte encoding");
    }

    private void checkNullPointerException(Callable<?> c) {
        assertThrows(NullPointerException.class, () -> c.call());
    }

    private void testReadWrite(int size, Charset cs, boolean append) throws IOException {
        String expected;
        String str = generateString(size);
        Path result;
        if (cs == null) {
            result = Files.writeString(testFiles[0], str);
        } else {
            result = Files.writeString(testFiles[0], str, cs);
        }

        //System.out.println(result.toUri().toASCIIString());
        assertSame(result, testFiles[0]);
        if (append) {
            if (cs == null) {
                Files.writeString(testFiles[0], str, APPEND);
            } else {
                Files.writeString(testFiles[0], str, cs, APPEND);
            }
            assertEquals(size * 2, Files.size(testFiles[0]));
        }


        if (append) {
            expected = str + str;
        } else {
            expected = str;
        }

        String read;
        if (cs == null) {
            read = Files.readString(result);
        } else {
            read = Files.readString(result, cs);
        }

        assertEquals(expected, read, "String read not the same as written");
    }

    static final char[] CHARS = "abcdefghijklmnopqrstuvwxyz \r\n".toCharArray();
    StringBuilder sb = new StringBuilder(1024 << 4);
    Random random = new Random();

    private String generateString(int size) {
        sb.setLength(0);
        for (int i = 0; i < size; i++) {
            char c = CHARS[random.nextInt(CHARS.length)];
            sb.append(c);
        }

        return sb.toString();
    }
}
