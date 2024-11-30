/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_16BE;
import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_32;
import static java.nio.charset.StandardCharsets.UTF_32BE;
import static java.nio.charset.StandardCharsets.UTF_32LE;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.Callable;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/* @test
 * @bug 8201276 8205058 8209576 8287541 8288589 8325590
 * @build ReadWriteString PassThroughFileSystem
 * @run testng ReadWriteString
 * @summary Unit test for methods for Files readString and write methods.
 * @key randomness
 * @modules jdk.charsets
 */
@Test(groups = "readwrite")
public class ReadWriteString {

    // data for text files
    final String TEXT_UNICODE = "\u201CHello\u201D";
    final String TEXT_ASCII = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n abcdefghijklmnopqrstuvwxyz\n 1234567890\n";
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
            throw new RuntimeException(ex);
        }
    }

    // file used by testReadWrite, testReadString and testWriteString
    private Path[] testFiles = new Path[3];

    /*
     * DataProvider for malformed write test. Provides the following fields:
     * file path, malformed input string, charset
     */
    @DataProvider(name = "malformedWrite")
    public Object[][] getMalformedWrite() throws IOException {
        Path path = Files.createFile(Path.of("malformedWrite"));
        return new Object[][]{
            {path, "\ud800", null},  //the default Charset is UTF_8
            {path, "\u00A0\u00A1", US_ASCII},
            {path, "\ud800", UTF_8},
            {path, JA_STRING, ISO_8859_1},
            {path, "\u041e", WINDOWS_1252}, // cyrillic capital letter O
            {path, "\u091c", WINDOWS_31J}, // devanagari letter ja
        };
    }

    /*
     * DataProvider for illegal input test
     * Writes the data in ISO8859 and reads with UTF_8, expects MalformedInputException
     */
    @DataProvider(name = "illegalInput")
    public Object[][] getIllegalInput() throws IOException {
        Path path = Files.createFile(Path.of("illegalInput"));
        return new Object[][]{
            {path, data, ISO_8859_1, null},
            {path, data, ISO_8859_1, UTF_8}
        };
    }

    /*
     * DataProvider for illegal input bytes test
     */
    @DataProvider(name = "illegalInputBytes")
    public Object[][] getIllegalInputBytes() throws IOException {
        return new Object[][]{
            {new byte[] {(byte)0x00, (byte)0x20, (byte)0x00}, UTF_16, MalformedInputException.class},
            {new byte[] {-50}, UTF_16, MalformedInputException.class},
            {new byte[] {(byte)0x81}, WINDOWS_1252, UnmappableCharacterException.class}, // unused in Cp1252
            {new byte[] {(byte)0x81, (byte)0xff}, WINDOWS_31J, UnmappableCharacterException.class}, // invalid trailing byte
        };
    }

    /*
     * DataProvider for writeString test
     * Writes the data using both the existing and new method and compares the results.
     */
    @DataProvider(name = "testWriteString")
    public Object[][] getWriteString() {

        return new Object[][]{
            {testFiles[1], testFiles[2], TEXT_ASCII, US_ASCII, null},
            {testFiles[1], testFiles[2], TEXT_ASCII, US_ASCII, US_ASCII},
            {testFiles[1], testFiles[2], TEXT_UNICODE, UTF_8, null},
            {testFiles[1], testFiles[2], TEXT_UNICODE, UTF_8, UTF_8}
        };
    }

    /*
     * DataProvider for readString test
     * Reads the file using both the existing and new method and compares the results.
     */
    @DataProvider(name = "testReadString")
    public Object[][] getReadString() {
        return new Object[][]{
            {testFiles[1], TEXT_ASCII, US_ASCII, US_ASCII},
            {testFiles[1], TEXT_ASCII, US_ASCII, UTF_8},
            {testFiles[1], TEXT_UNICODE, UTF_8, null},
            {testFiles[1], TEXT_UNICODE, UTF_8, UTF_8},
            {testFiles[1], TEXT_ASCII, US_ASCII, ISO_8859_1},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16, UTF_16},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16BE, UTF_16BE},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_16LE, UTF_16LE},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32, UTF_32},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32BE, UTF_32BE},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, UTF_32LE, UTF_32LE},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, WINDOWS_1252, WINDOWS_1252},
            {testFiles[1], TEXT_PERSON_CART_WHEELING, WINDOWS_31J, WINDOWS_31J}
        };
    }

    @BeforeClass
    void setup() throws IOException {
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
    @Test(dataProvider = "testWriteString")
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
        assertTrue((Arrays.compare(bytes, bytes2) == 0), "The bytes should be the same");
    }

    /**
     * Verifies that the readString method added since 11 behaves the same as
     * constructing a string from the existing readAllBytes method.
     */
    @Test(dataProvider = "testReadString")
    public void testReadString(Path path, String text, Charset cs, Charset cs2) throws IOException {
        Files.write(path, text.getBytes(cs));
        String str = new String(Files.readAllBytes(path), cs);

        // readString @since 11
        String str2 = (cs2 == null) ? Files.readString(path) :
                                      Files.readString(path, cs2);
        assertTrue((str.equals(str2)), "The strings should be the same");
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
    @Test(dataProvider = "malformedWrite", expectedExceptions = UnmappableCharacterException.class)
    public void testMalformedWrite(Path path, String s, Charset cs) throws IOException {
        if (cs == null) {
            Files.writeString(path, s);
        } else {
            Files.writeString(path, s, cs);
        }
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
    @Test(dataProvider = "illegalInput", expectedExceptions = MalformedInputException.class)
    public void testMalformedRead(Path path, byte[] data, Charset csWrite, Charset csRead) throws IOException {
        String temp = new String(data, csWrite);
        Files.writeString(path, temp, csWrite);
        if (csRead == null) {
            Files.readString(path);
        } else {
            Files.readString(path, csRead);
        }
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
    @Test(dataProvider = "illegalInputBytes")
    public void testMalformedReadBytes(byte[] data, Charset csRead, Class<CharacterCodingException> expected)
            throws IOException {
        Path path = Path.of("illegalInputBytes");
        Files.write(path, data);
        try {
            Files.readString(path, csRead);
        } catch (MalformedInputException | UnmappableCharacterException e) {
            if (expected.isInstance(e)) {
                // success
                return;
            }
        }
        throw new RuntimeException("An instance of " + expected + " should be thrown");
    }

    // Verify File.readString with UTF16 to confirm proper string length and contents.
    // A regression test for 8325590
    @Test
    public void testSingleUTF16() throws IOException {
        String original = "🤸";    // "\ud83e\udd38";
        Files.writeString(testFiles[0], original, UTF_16);
        String actual = Files.readString(testFiles[0], UTF_16);
        if (!actual.equals(original)) {
            System.out.printf("expected (%s), was (%s)\n", original, actual);
            System.out.printf("expected UTF_16 bytes: %s\n", Arrays.toString(original.getBytes(UTF_16)));
            System.out.printf("actual UTF_16 bytes: %s\n", Arrays.toString(actual.getBytes(UTF_16)));
        }
        assertEquals(actual, original, "Round trip string mismatch with multi-byte encoding");
    }

    private void checkNullPointerException(Callable<?> c) {
        try {
            c.call();
            fail("NullPointerException expected");
        } catch (NullPointerException ignore) {
        } catch (Exception e) {
            fail(e + " not expected");
        }
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
        assertTrue(result == testFiles[0]);
        if (append) {
            if (cs == null) {
                Files.writeString(testFiles[0], str, APPEND);
            } else {
                Files.writeString(testFiles[0], str, cs, APPEND);
            }
            assertTrue(Files.size(testFiles[0]) == size * 2);
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

        assertTrue(read.equals(expected), "String read not the same as written");
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
