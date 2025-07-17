/*
 * Copyright (c) 2009, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4244499 4532049 4700978 4820807 4980042 7009069 8322802
 * @summary Test ZipInputStream, ZipOutputStream and ZipFile with non-UTF8 encoding
 * @modules jdk.charsets
 * @run junit ZipCoding
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class ZipCoding {

    // The data to write to ZIP entries in this test
    private static byte[] ENTRY_DATA = "German Umlaut \u00fc in entry data"
            .getBytes(StandardCharsets.ISO_8859_1);

    /**
     * Provide arguments used for parameterized tests
     * @return a stream of argument lists
     */
    public static Stream<Arguments> charsetsAndNames() {
        // Arguments are: Write charset, read charset, entry name, comment
        return Stream.of(
                // MS code page 932 for the Japanese language
                Arguments.of("MS932", "MS932",
                        "\u4e00\u4e01",
                        "\uff67\uff68\uff69\uff6a\uff6b\uff6c"),

                // Code page for the IBM PC
                Arguments.of("ibm437", "ibm437",
                        "\u00e4\u00fc",
                        "German Umlaut \u00fc in comment"),

                // UTF-8 with Japanese characters
                Arguments.of("utf-8", "utf-8",
                        "\u4e00\u4e01",
                        "\uff67\uff68\uff69\uff6a\uff6b\uff6c"),

                // UTF-8 with characters in the Latin1 range
                Arguments.of("utf-8", "utf-8",
                        "\u00e4\u00fc",
                        "German Umlaut \u00fc in comment"),

                // UTF-8 with surrogate pairs
                Arguments.of("utf-8", "utf-8",
                        "Surrogate\ud801\udc01",
                        "Surrogates \ud800\udc00 in comment"),

                // ZipOutputStream sets the 'Language encoding flag' when writing using UTF-8
                // UTF-8 should be used for decoding, regardless of the opening charset

                // UTF-8 with Japanese characters, opened with MS932
                Arguments.of("utf-8", "MS932",
                        "\u4e00\u4e01",
                        "\uff67\uff68\uff69\uff6a\uff6b\uff6c"),

                // UTF-8 with characters in latin1 range, opened with iso-8859-1
                Arguments.of("utf-8", "iso-8859-1",
                        "\u00e4\u00fc",
                        "German Umlaut \u00fc in comment"),
                // UTF-8 with surrogate pairs, opened with MS932
                Arguments.of("utf-8", "MS932",
                        "Surrogate\ud801\udc01",
                        "Surrogates \ud800\udc00 in comment")
        );
    }

    /**
     * Verify that ZipInputStream decodes entry names and comments
     * using the charset provided to its constructor, or that it decodes
     * using UTF-8 when the 'Language encoding flag' is set
     *
     * @param writeCharset the charset to use for ZipOutputStream when producing the ZIP
     * @param readCharset the charset to use when opening the ZipInputStream
     * @param name the entry name
     * @param comment the entry comment (not read by ZipInputStream)
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @ParameterizedTest
    @MethodSource("charsetsAndNames")
    public void testZipInputStream(String writeCharset,
                                   String readCharset,
                                   String name,
                                   String comment) throws IOException {

        byte[] zip = createZIP(writeCharset, name, comment);

        try (InputStream in = new ByteArrayInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in, Charset.forName(readCharset))) {
            ZipEntry e = zis.getNextEntry();
            assertNotNull(e);
            assertEquals(name, e.getName(),
                    "ZipInputStream.getNextEntry() returned unexpected entry name");
            assertNull(e.getComment()); // No comment in the LOC header
            assertArrayEquals(ENTRY_DATA, zis.readAllBytes(), "Unexpected ZIP entry data");
        }
    }

    /**
     * Verify that ZipFile decodes entry names and comments
     * using the charset provided to its constructor, or that it decodes
     * using UTF-8 when the 'Language encoding flag' is set
     *
     * @param writeCharset the charset to use for ZipOutputStream when producing the ZIP
     * @param readCharset the charset to use when opening the ZipFile
     * @param name the name of the entry
     * @param comment the comment of the entry
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @ParameterizedTest
    @MethodSource("charsetsAndNames")
    public void testZipFile(String writeCharset,
                            String readCharset,
                            String name,
                            String comment) throws IOException {

        byte[] zip = createZIP(writeCharset, name, comment);

        Path f = Path.of("zfcoding.zip");
        Files.write(f, zip);

        try (ZipFile zf = new ZipFile(f.toFile(), Charset.forName(readCharset))) {
            // Test using ZipFile.entries
            Enumeration<? extends ZipEntry> zes = zf.entries();
            ZipEntry e = (ZipEntry)zes.nextElement();
            assertNotNull(e);
            assertEquals(name, e.getName(), "ZipFile.entries() returned unexpected entry name");
            assertEquals(comment, e.getComment(), "ZipFile.entries() returned unexpected entry comment");

            // Test using ZipFile.getEntry
            e = zf.getEntry(name);
            assertNotNull(e,
                    String.format("Entry lookup failed on ZIP encoded with %s and opened with %s",
                            writeCharset, readCharset));
            assertEquals(name, e.getName(), "ZipFile.getEntry() returned unexpected entry name");
            assertEquals(comment, e.getComment(), "ZipFile.getEntry() returned unexpected entry comment");
            try (InputStream is = zf.getInputStream(e)) {
                assertNotNull(is);
                assertArrayEquals(ENTRY_DATA, is.readAllBytes(), "Unexpected ZIP entry data");
            }
        }

        Files.deleteIfExists(f);
    }

    /**
     * Create a ZIP file containing an entry with the given name
     * and comment, encoded using the given charset.
     * Note that if the charset is UTF-8, ZipOutputStream will
     * set the 'Language encoding flag' for the entry.
     *
     * @param charset the charset passed to the ZipOutputStream constructor
     * @param name the name of the entry to add
     * @param comment the comment of the entry to add
     * @return a byte array containing the ZIP file
     *
     * @throws IOException if an unexpected IOException occurs
     */
    private byte[] createZIP(String charset, String name, String comment) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, Charset.forName(charset))) {
            ZipEntry e = new ZipEntry(name);
            e.setComment(comment);
            zos.putNextEntry(e);
            zos.write(ENTRY_DATA);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
