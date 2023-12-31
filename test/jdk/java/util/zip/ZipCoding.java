/*
 * Copyright (c) 2009, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4244499 4532049 4700978 4820807 4980042 7009069
 * @summary Test ZipInputStream, ZipOutputStream and ZipFile with non-UTF8 encoding
 * @modules jdk.charsets
 * @run junit ZipCoding
 */

import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class ZipCoding {

    /**
     * Test ZIP file name and comment encoding using the MS code page 932 for the Japanese language
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void MS932() throws IOException {
        test("MS932",
                "\u4e00\u4e01", "\uff67\uff68\uff69\uff6a\uff6b\uff6c");
    }

    /**
     * Test ZIP file name and comment encoding using the code page for the IBM PC
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void ibm437() throws IOException {
        test("ibm437",
             "\u00e4\u00fc", "German Umlaut \u00fc in comment");
    }

    /**
     * Test ZIP file name and comment encoding using UTF-8 with Japanese characters
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void utf8Japanese() throws IOException {
        test("utf-8",
                "\u4e00\u4e01", "\uff67\uff68\uff69\uff6a\uff6b\uff6c");
    }

    /**
     * Test ZIP file name and comment encoding using UTF-8 with characters in the Latin1 range
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void utf8InLat1Range() throws IOException {
        test("utf-8",
                "\u00e4\u00fc", "German Umlaut \u00fc in comment");
    }

    /**
     * Test ZIP file name and comment encoding using UTF-8 with surrogate pairs
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void utf8Surrogate() throws IOException {
        test("utf-8",
             "Surrogate\ud801\udc01", "Surrogates \ud800\udc00 in comment");
    }

    /**
     * Verify that a ZIP entry with the given name and comment can be found
     * when opening the given ZIP file using ZipInputStream with the given charset
     *
     * @param zip the ZIP file to open
     * @param openCharset the Charset to pass to the ZipInputStream constructor
     * @param expectedName the expected name of the ZIP entry
     * @param expectedComment the expected comment of ZIP entry
     * @param expectedContent the expected contents of the ZIP entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    static void testZipInputStream(byte[] zip,
                                   Charset openCharset,
                                   String expectedName,
                                   String expectedComment,
                                   byte[] expectedContent) throws IOException
    {

        try (InputStream in = new ByteArrayInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in, openCharset)) {
            ZipEntry e = zis.getNextEntry();
            assertNotNull(e);
            assertEquals(expectedName, e.getName());
            byte[] content = zis.readAllBytes();
            assertArrayEquals(expectedContent, content, "ZipIS content doesn't match!");
        }
    }

    /**
     * Verify that a ZIP entry with the given name and comment can be found
     * when opening the given ZIP file using ZipFile with the given charset
     *
     * @param zip the ZIP file to open
     * @param openCharset the Charset to pass to the ZipInputStream constructor
     * @param expectedName the expected name of the ZIP entry
     * @param expectedComment the expected comment of ZIP entry
     * @param expectedContent the expected contents of the ZIP entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    static void testZipFile(File zip,
                            Charset openCharset,
                            String expectedName,
                            String expectedComment,
                            byte[] expectedContent)
        throws IOException
    {
        try (ZipFile zf = new ZipFile(zip, openCharset)) {
            // Test using ZipFile.entries
            Enumeration<? extends ZipEntry> zes = zf.entries();
            ZipEntry e = (ZipEntry)zes.nextElement();
            assertNotNull(e);
            assertEquals(expectedName, e.getName(), "ZipFile.entries(): name doesn't match!");
            assertEquals(expectedComment, e.getComment(), "ZipFile.entries(): comment doesn't match!");

            try (InputStream is = zf.getInputStream(e)) {
                assertNotNull(is);
                byte[] actualContent = is.readAllBytes();
                assertArrayEquals(expectedContent, actualContent, "ZipFile content doesn't match!");
            }
        }
    }

    /**
     * Verify that a ZIP file with a written using ZipOutputStream with an entry
     * with the given name and comment can be read back using the same charset
     * using both the ZipFile and ZipInputStream APIs.
     *
     * If the charset is "utf-8", also test that the entry is read with the expected
     * name when opened using a non-utf-8 charset.
     * @param csn the charset used when writing and opening the ZIP file
     * @param name the name of the entry to write and find
     * @param comment the comment to add to the entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    static void test(String csn, String name, String comment) throws IOException {
        byte[] entryData = "This is the content of the zipfile".getBytes("ISO-8859-1");
        Charset cs = Charset.forName(csn);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, cs)) {
            ZipEntry e = new ZipEntry(name);
            e.setComment(comment);
            zos.putNextEntry(e);
            zos.write(entryData, 0, entryData.length);
            zos.closeEntry();
        }

        byte[] zip = baos.toByteArray();

        testZipInputStream(zip, cs, name, comment, entryData);

        if ("utf-8".equals(csn)) {
            // USE_UTF8 should be set
            testZipInputStream(zip, Charset.forName("MS932"), name, comment, entryData);
        }

        File f = new File(new File(System.getProperty("test.dir", ".")),
                          "zfcoding.zip");
        try (FileOutputStream fos = new FileOutputStream(f)) {
            baos.writeTo(fos);
        }
        testZipFile(f, cs, name, comment, entryData);
        if ("utf-8".equals(csn)) {
            testZipFile(f, Charset.forName("MS932"), name, comment, entryData);
        }
        f.delete();
    }
}
