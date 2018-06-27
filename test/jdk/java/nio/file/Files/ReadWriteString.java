/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import java.util.Random;
import java.util.concurrent.Callable;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/* @test
 * @bug 8201276 8205058
 * @build ReadWriteString PassThroughFileSystem
 * @run testng ReadWriteString
 * @summary Unit test for methods for Files readString and write methods.
 * @key randomness
 */
@Test(groups = "readwrite")
public class ReadWriteString {

    // data for text files
    private static final String EN_STRING = "The quick brown fox jumps over the lazy dog";
    private static final String JA_STRING = "\u65e5\u672c\u8a9e\u6587\u5b57\u5217";
    // malformed input: a high surrogate without the low surrogate
    static char[] illChars = {
        '\u00fa', '\ud800'
    };

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

    // file used by most tests
    private Path tmpfile;


    /*
     * DataProvider for malformed write test. Provides the following fields:
     * file path, malformed input string, charset
     */
    @DataProvider(name = "malformedWrite")
    public Object[][] getMalformedWrite() throws IOException {
        Path path = Files.createTempFile("malformedWrite", null);
        return new Object[][]{
            {path, "\ud800", null},  //the default Charset is UTF_8
            {path, "\u00A0\u00A1", US_ASCII},
            {path, "\ud800", UTF_8},
            {path, JA_STRING, ISO_8859_1},
        };
    }

    /*
     * DataProvider for illegal input test
     * Writes the data in ISO8859 and reads with UTF_8, expects MalformedInputException
     */
    @DataProvider(name = "illegalInput")
    public Object[][] getIllegalInput() throws IOException {
        Path path = Files.createTempFile("illegalInput", null);
        return new Object[][]{
            {path, data, ISO_8859_1, null},
            {path, data, ISO_8859_1, UTF_8}
        };
    }

    @BeforeClass
    void setup() throws IOException {
        tmpfile = Files.createTempFile("readWriteString", null);
    }

    @AfterClass
    void cleanup() throws IOException {
        Files.deleteIfExists(tmpfile);
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
        path.toFile().deleteOnExit();
        if (cs == null) {
            Files.writeString(path, s, CREATE);
        } else {
            Files.writeString(path, s, cs, CREATE);
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
        path.toFile().deleteOnExit();
        String temp = new String(data, csWrite);
        Files.writeString(path, temp, csWrite, CREATE);
        String s;
        if (csRead == null) {
            s = Files.readString(path);
        } else {
            s = Files.readString(path, csRead);
        }
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
            result = Files.writeString(tmpfile, str);
        } else {
            result = Files.writeString(tmpfile, str, cs);
        }

        //System.out.println(result.toUri().toASCIIString());
        assertTrue(result == tmpfile);
        if (append) {
            if (cs == null) {
                Files.writeString(tmpfile, str, APPEND);
            } else {
                Files.writeString(tmpfile, str, cs, APPEND);
            }
            assertTrue(Files.size(tmpfile) == size * 2);
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
