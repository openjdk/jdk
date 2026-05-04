/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/*
 * @test
 * @bug 8355975
 * @summary verify that the internal ZIP structure caching in java.util.zip.ZipFile
 *          uses the correct Charset when parsing the ZIP structure of a ZIP file
 * @run junit ZipFileCharsetTest
 */
public class ZipFileCharsetTest {

    private static final String ISO_8859_15_NAME = "ISO-8859-15";

    /**
     * The internal implementation of java.util.zip.ZipFile maintains a cache
     * of the ZIP structure of each ZIP file that's currently open. This cache
     * helps prevent repeat parsing of the ZIP structure of the same underlying
     * ZIP file, every time a ZipFile instance is created for the same ZIP file.
     * The cache uses an internal key to map a ZIP file to the corresponding
     * ZIP structure that's cached.
     * A ZipFile can be constructed by passing a Charset which will be used to
     * decode the entry names (and comment) in a ZIP file.
     * The test verifies that when multiple ZipFile instances are
     * constructed using different Charsets but the same underlying ZIP file,
     * then the internal caching implementation of ZipFile doesn't end up using
     * a wrong Charset for parsing the ZIP structure of the ZIP file.
     */
    @Test
    void testCachedZipFileSource() throws Exception {
        // ISO-8859-15 is not a standard charset in Java. We skip this test
        // when it is unavailable
        assumeTrue(Charset.availableCharsets().containsKey(ISO_8859_15_NAME),
                "skipping test since " + ISO_8859_15_NAME + " charset isn't available");

        // We choose the byte 0xA4 for entry name in the ZIP file.
        // 0xA4 is "Euro sign" in ISO-8859-15 charset and
        // "Currency sign (generic)" in ISO-8859-1 charset.
        final byte[] entryNameBytes = new byte[]{(byte) 0xA4}; // intentional cast
        final Charset euroSignCharset = Charset.forName(ISO_8859_15_NAME);
        final Charset currencySignCharset = ISO_8859_1;

        final String euroSign = new String(entryNameBytes, euroSignCharset);
        final String currencySign = new String(entryNameBytes, currencySignCharset);

        // create a ZIP file whose entry name is encoded using ISO-8859-15 charset
        final Path zip = createZIP("euro", euroSignCharset, entryNameBytes);

        // Construct a ZipFile instance using the (incorrect) charset ISO-8859-1.
        // While that ZipFile instance is still open (and the ZIP file structure
        // still cached), construct another instance for the same ZIP file, using
        // the (correct) charset ISO-8859-15.
        try (ZipFile incorrect = new ZipFile(zip.toFile(), currencySignCharset);
             ZipFile correct = new ZipFile(zip.toFile(), euroSignCharset)) {

            // correct encoding should resolve the entry name to euro sign
            // and the entry should be thus be located
            assertNotNull(correct.getEntry(euroSign), "euro sign entry missing in " + correct);
            // correct encoding should not be able to find an entry name
            // with the currency sign
            assertNull(correct.getEntry(currencySign), "currency sign entry unexpectedly found in "
                    + correct);

            // incorrect encoding should resolve the entry name to currency sign
            // and the entry should be thus be located by the currency sign name
            assertNotNull(incorrect.getEntry(currencySign), "currency sign entry missing in "
                    + incorrect);
            // incorrect encoding should not be able to find an entry name
            // with the euro sign
            assertNull(incorrect.getEntry(euroSign), "euro sign entry unexpectedly found in "
                    + incorrect);
        }
    }

    /**
     * Creates and return ZIP file whose entry names are encoded using the given {@code charset}
     */
    private static Path createZIP(final String fileNamePrefix, final Charset charset,
                                  final byte[] entryNameBytes) throws IOException {
        final Path zip = Files.createTempFile(Path.of("."), fileNamePrefix, ".zip");
        // create a ZIP file whose entry name(s) use the given charset
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip), charset)) {
            zos.putNextEntry(new ZipEntry(new String(entryNameBytes, charset)));
            final byte[] entryContent = "doesnotmatter".getBytes(US_ASCII);
            zos.write(entryContent);
            zos.closeEntry();
        }
        return zip;
    }
}
