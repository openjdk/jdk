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
 *
 */

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/*
 * @test
 * @bug 8358456
 * @summary verify that ZipFile.getInputStream(ZipFile) doesn't throw an unspecified exception
 *          for invalid compressed size of an entry
 * @run junit InvalidCompressedSizeTest
 */
class InvalidCompressedSizeTest {

    private static final String ENTRY_NAME = "foo-bar";
    private static final byte[] ENTRY_CONTENT = new byte[]{0x42, 0x42};

    // created through a call to createZIPContent()
    private static final String ZIP_CONTENT_HEX = """
            504b03041400080808005053c35a00000000000000000000000007000000666f6f2d6261727
            3720200504b0708c41f441b0400000002000000504b010214001400080808005053c35ac41f
            441b0400000002000000070000000000000000000000000000000000666f6f2d626172504b0
            506000000000100010035000000390000000000
            """;


    //    0039 CENTRAL HEADER #1     02014B50
    //    ...
    //    0043 Compression Method    0008 'Deflated'
    //    ...
    //    004D Compressed Length     00000004
    //    0051 Uncompressed Length   00000002
    //    ...
    //    0067 Filename              'foo-bar'
    // this is the offset in the ZIP content stream for the compressed size field
    // for the entry of interest
    private static final int COMP_SIZE_OFFSET = 0x004D;

    // intentionally unused but left here to allow for constructing newer/updated
    // ZIP_CONTENT_HEX, when necessary
    private static String createZIPContent() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final ZipOutputStream zos = new ZipOutputStream(baos)) {
            final ZipEntry ze = new ZipEntry(ENTRY_NAME);
            zos.putNextEntry(ze);
            zos.write(ENTRY_CONTENT);
            zos.closeEntry();
        }
        return HexFormat.of().formatHex(baos.toByteArray());
    }

    /*
     * Calls ZipFile.getInputStream(ZipEntry) on a ZIP entry whose compressed size is
     * intentionally set to 0. The test then verifies that the call to getInputStream()
     * doesn't throw an unspecified exception.
     */
    @Test
    void testInvalidCompressedSize() throws Exception {
        final byte[] originalZIPContent = HexFormat.of().parseHex(ZIP_CONTENT_HEX.replace("\n", ""));
        final ByteBuffer zipContent = ByteBuffer.wrap(originalZIPContent).order(LITTLE_ENDIAN);

        // overwrite the compressed size value in the entry's CEN to an invalid value of 0
        zipContent.position(COMP_SIZE_OFFSET);
        final int invalidCompressedSize = 0;
        zipContent.putInt(invalidCompressedSize);
        zipContent.rewind();

        // write out the ZIP content so that it can be read through ZipFile
        final Path zip = Files.createTempFile(Path.of("."), "8358456-", ".zip");
        Files.write(zip, zipContent.array());
        System.out.println("created ZIP " + zip + " with an invalid compressed size for entry");

        try (final ZipFile zf = new ZipFile(zip.toFile())) {
            final ZipEntry entry = zf.getEntry(ENTRY_NAME);
            assertNotNull(entry, "missing entry " + ENTRY_NAME + " in ZIP file " + zip);
            // verify that we are indeed testing a ZIP file with an invalid
            // compressed size for the entry
            assertEquals(0, entry.getCompressedSize(), "unexpected compressed size");
            // merely open and close the InputStream to exercise the code which
            // would incorrectly raise an exception. we don't read the contents
            // of the stream because we have (intentionally) corrupted the metadata
            // of the ZIP and that will cause the reading to fail.
            try (final InputStream is = zf.getInputStream(entry)) {
                System.out.println("successfully opened input stream " + is
                        + " for entry " + entry.getName());
            }
        }
    }
}
