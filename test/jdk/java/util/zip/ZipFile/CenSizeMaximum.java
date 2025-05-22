/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8341595
 * @modules java.base/jdk.internal.util
 * @summary Verify that ZipFile can read from a ZIP file with a maximally large CEN size
 * @run junit/othervm/manual -Xmx2500M CenSizeMaximum
 */

import jdk.internal.util.ArraysSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class CenSizeMaximum {

    // Maximum allowed CEN size allowed by the ZipFile implementation
    static final int MAX_CEN_SIZE = ArraysSupport.SOFT_MAX_ARRAY_LENGTH;

    /**
     * From the APPNOTE.txt specification:
     *    4.4.10 file name length: (2 bytes)
     *    4.4.11 extra field length: (2 bytes)
     *    4.4.12 file comment length: (2 bytes)
     *
     *        The length of the file name, extra field, and comment
     *        fields respectively.  The combined length of any
     *        directory record and these three fields SHOULD NOT
     *        generally exceed 65,535 bytes.
     *.
     * Create a maximum extra field which does not exceed 65,535 bytes
     */
    static final int MAX_EXTRA_FIELD_SIZE = 65_535 - ZipFile.CENHDR;

    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;

    // The size of one CEN header, including the name and the extra field
    static final int CEN_HEADER_SIZE = ZipFile.CENHDR + MAX_EXTRA_FIELD_SIZE;

    // The size of the extra data field header (tag id + data block length)
    static final int EXTRA_FIELD_HEADER_SIZE = 2 * Short.BYTES;

    // Zip file to create for testing
    private Path hugeZipFile = Path.of("cen-size-on-limit.zip");

    /**
     * Clean up ZIP file created in this test
     */
    @AfterEach
    public void cleanup() throws IOException {
        //Files.deleteIfExists(hugeZipFile);
    }

    /**
     * Validates that ZipFile opens a ZIP file with a CEN size close
     * to the {@link #MAX_CEN_SIZE} implementation limit.
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    public void maximumCenSize() throws IOException {
        int numCenHeaders = zipWithWithExactCenSize(MAX_CEN_SIZE, true, false);
        try (var zf = new ZipFile(hugeZipFile.toFile())) {
            assertEquals(numCenHeaders, zf.size());
        }
    }

    /**
     * Validates that ZipFile rejects a ZIP where the last CEN record
     * overflows the CEN size and the END header CENTOT field is smaller
     * than the actual number of headers
     *
     * @throws IOException if an unexpected IO error occurs
     */
    @Test
    public void lastCENHeaderBadSize() throws IOException {
        int numCenHeaders = zipWithWithExactCenSize(1024, true, true);
        ZipException zipException = assertThrows(ZipException.class, () -> {
            try (var zf = new ZipFile(hugeZipFile.toFile())) {
                assertEquals(numCenHeaders, zf.size());
            }
        });
        assertEquals("invalid CEN header (bad header size)", zipException.getMessage());

    }

    /**
     * Produce a ZIP file with an exact CEN size. To minimize the number of CEN headers
     * written, maximally large, empty extra data blocks are written sparsely.
     *
     * @param cenSize the exact CEN size of the ZIP file to produce
     * @param invalidateEndTotal whether to decrement the END header's TOT field by one
     * @return the number of CEN headers produced
     * @throws IOException if an unexpected IO error occurs
     */
    private int zipWithWithExactCenSize(long cenSize, boolean invalidateEndTotal, boolean overflowLastCEN)
            throws IOException {
        // Sanity check
        assertTrue(cenSize <= MAX_CEN_SIZE);

        // The number of CEN headers we need to write
        int numCenHeaders = (int) (cenSize / CEN_HEADER_SIZE) + 1;
        // Size if all extra data fields were of maximum size
        long overSized = numCenHeaders * (long) CEN_HEADER_SIZE;
        // Length to trim from the first CEN's extra data
        int negativPadding = (int) (overSized - cenSize);
        int firstExtraSize = MAX_EXTRA_FIELD_SIZE - negativPadding;

        // Sanity check
        long computedCenSize = (numCenHeaders -1L ) * CEN_HEADER_SIZE + ZipEntry.CENHDR + firstExtraSize;
        assertEquals(computedCenSize, cenSize);

        // A CEN header, followed by the four-bytes extra data header
        ByteBuffer cenHeader = createCENHeader();
        // An END header
        ByteBuffer endHeader = createENDHeader();
        // Update the END header
        if (invalidateEndTotal) {
            // To trigger countCENHeaders
            endHeader.putShort(ZipEntry.ENDTOT, (short) (numCenHeaders -1 & 0xFFFF));
        } else {
            endHeader.putShort(ZipEntry.ENDTOT, (short) (numCenHeaders & 0xFFFF));
        }
        // Update CEN size and offset fields
        endHeader.putInt(ZipEntry.ENDSIZ, (int) (cenSize & 0xFFFFFFFFL));
        endHeader.putInt(ZipEntry.ENDOFF, 0);

        // When creating a sparse file, the file must not already exit
        Files.deleteIfExists(hugeZipFile);

        // Open a FileChannel for writing a sparse file
        EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.SPARSE);

        try (FileChannel channel = FileChannel.open(hugeZipFile, options)) {
            // Write CEN headers
            for (int i = 0; i < numCenHeaders; i++) {
                // The first CEN header has trimmed extra data
                int extraSize = i == 0 ? firstExtraSize : MAX_EXTRA_FIELD_SIZE;
                if (overflowLastCEN && i == numCenHeaders - 1) {
                    // make last CEN header overflow the CEN size
                    cenHeader.putShort(ZipEntry.CENNAM, Short.MAX_VALUE);
                }
                // update elen field
                cenHeader.putShort(ZipEntry.CENEXT, (short) (extraSize & 0xFFFF));
                // update data block len of the extra field header
                short dlen = (short) ((extraSize - EXTRA_FIELD_HEADER_SIZE) & 0xFFFF);
                cenHeader.putShort(ZipEntry.CENHDR + Short.BYTES, dlen);
                // Write the CEN header plus the four-byte extra header
                channel.write(cenHeader.rewind());
                // Sparse "write" of the extra data block
                channel.position(channel.position() + extraSize - EXTRA_FIELD_HEADER_SIZE);
            }
            // Sanity check
            assertEquals(cenSize,  channel.position());
            // Write the END header
            channel.write(endHeader.rewind());
        }
        return numCenHeaders;
    }

    // Creates a ByteBuffer representing a CEN header with a trailing extra field header
    private ByteBuffer createCENHeader() throws IOException {
        byte[] bytes = smallZipfile();
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int endOff = bytes.length - ZipEntry.ENDHDR;
        int cenSize = buf.getInt(endOff + ZipEntry.ENDSIZ);
        int cenOff = buf.getInt(endOff + ZipEntry.ENDOFF);
        return ByteBuffer.wrap(
                Arrays.copyOfRange(bytes, cenOff, cenOff + ZipEntry.CENHDR + EXTRA_FIELD_HEADER_SIZE)
        ).order(ByteOrder.LITTLE_ENDIAN);
    }

    // Creates a ByteBuffer representing an END header
    private ByteBuffer createENDHeader() throws IOException {
        byte[] bytes = smallZipfile();
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int endOff = bytes.length - ZipEntry.ENDHDR;
        return ByteBuffer.wrap(
                        Arrays.copyOfRange(bytes, endOff, endOff + ZipEntry.ENDHDR)
        ).order(ByteOrder.LITTLE_ENDIAN);
    }

    // Create a byte array with a minimal ZIP file
    private static byte[] smallZipfile() throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zo = new ZipOutputStream(out)) {
            ZipEntry entry = new ZipEntry("");
            entry.setExtra(makeDummyExtraField());
            zo.putNextEntry(entry);
        }
        return out.toByteArray();
    }

    // Create a minimally sized extra field
    private static byte[] makeDummyExtraField() {
        byte[] extra = new byte[EXTRA_FIELD_HEADER_SIZE];
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);

        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);

        // Size of the actual (empty) data
        buffer.putShort((short) 0);
        return extra;
    }
}
