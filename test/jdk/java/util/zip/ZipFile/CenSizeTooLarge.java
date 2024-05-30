/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8272746
 * @summary Verify that ZipFile rejects a ZIP with a CEN size which does not fit in a Java byte array
 * @run junit CenSizeTooLarge
 */

import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CenSizeTooLarge {
    // Maximum allowed CEN size allowed by the ZipFile implementation
    static final int MAX_CEN_SIZE = Integer.MAX_VALUE - ZipFile.ENDHDR - 1;

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
     *
     *  Since ZipOutputStream does not enforce the 'combined length' clause,
     *  we simply use 65,535 (0xFFFF) for the purpose of this test.
     */
    static final int MAX_EXTRA_FIELD_SIZE = 65_535;

    // Data size (unsigned short)
    // Field size minus the leading header 'tag' and 'data size' fields (2 bytes each)
    static final short MAX_DATA_SIZE = (short) (MAX_EXTRA_FIELD_SIZE - 2 * Short.BYTES);

    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;

    // Entry names produced in this test are fixed-length
    public static final int NAME_LENGTH = 10;

    // Use a shared LocalDateTime on all entries to save processing time
    static final LocalDateTime TIME_LOCAL = LocalDateTime.now();

    // The size of one CEN header, including the name and the extra field
    static final int CEN_HEADER_SIZE = ZipFile.CENHDR + NAME_LENGTH + MAX_EXTRA_FIELD_SIZE;

    // The number of entries needed to exceed the MAX_CEN_SIZE
    static final int NUM_ENTRIES = (MAX_CEN_SIZE / CEN_HEADER_SIZE) + 1;

    // Helps SparseOutputStream detect write of the last CEN entry
    private static final String LAST_CEN_COMMENT = "LastCEN";
    private static final byte[] LAST_CEN_COMMENT_BYTES = LAST_CEN_COMMENT.getBytes(StandardCharsets.UTF_8);

    // Expected ZipException message when the CEN does not fit in a Java byte array
    private static final String CEN_TOO_LARGE_MESSAGE = "invalid END header (central directory size too large)";

    // Zip file to create for testing
    private File hugeZipFile;

    /**
     * Create a zip file with a CEN size which does not fit within a Java byte array
     */
    @Before
    public void setup() throws IOException {
        hugeZipFile = new File("cen-too-large.zip");
        hugeZipFile.deleteOnExit();

        try (OutputStream out = new SparseOutputStream(new FileOutputStream(hugeZipFile));
             ZipOutputStream zip = new ZipOutputStream(out)) {

            // Keep track of entries so we can update extra data before the CEN is written
            ZipEntry[] entries = new ZipEntry[NUM_ENTRIES];

            // Add entries until MAX_CEN_SIZE is reached
            for (int i = 0; i < NUM_ENTRIES; i++) {
                // Create a fixed-length name for the entry
                String name = Integer.toString(i);
                name = "0".repeat(NAME_LENGTH - name.length()) + name;

                // Create and track the entry
                ZipEntry entry = entries[i] = new ZipEntry(name);

                // Use STORED for faster processing
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(0);
                entry.setCrc(0);

                // Set the time/date field for faster processing
                entry.setTimeLocal(TIME_LOCAL);

                if (i == NUM_ENTRIES -1) {
                    // Help SparseOutputStream detect the last CEN entry write
                    entry.setComment(LAST_CEN_COMMENT);
                }
                // Add the entry
                zip.putNextEntry(entry);


            }
            // Finish writing the last entry
            zip.closeEntry();

            // Before the CEN headers are written, set the extra data on each entry
            byte[] extra = makeLargeExtraField();
            for (ZipEntry entry : entries) {
                entry.setExtra(extra);
            }
        }
    }

    /**
     * Validates that a ZipException is thrown with the expected message when
     * the ZipFile is initialized with a ZIP whose CEN exeeds {@link #MAX_CEN_SIZE}
     */
    @Test
    public void centralDirectoryTooLargeToFitInByteArray() {
        ZipException ex = assertThrows(ZipException.class, () -> new ZipFile(hugeZipFile));
        assertEquals(CEN_TOO_LARGE_MESSAGE, ex.getMessage());
    }

    /**
     * We can reduce the number of written CEN headers by making each CEN header maximally large.
     * We do this by adding the extra field produced by this method to each CEN header.
     * <p>
     * The structure of an extra field is as follows:
     * <p>
     * Header ID  (Two bytes, describes the type of the field, also called 'tag')
     * Data Size  (Two byte short)
     * Data Block (Contents depend on field type)
     */
    private byte[] makeLargeExtraField() {
        // Make a maximally sized extra field
        byte[] extra = new byte[MAX_EXTRA_FIELD_SIZE];
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);

        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);

        // Size of the actual (empty) data
        buffer.putShort(MAX_DATA_SIZE);
        return extra;
    }

    /**
     * By writing sparse 'holes' until the last CEN is detected, we can save disk space
     * used by this test from ~2GB to ~4K. Instances of this class should be passed
     * directly to the ZipOutputStream constructor, without any buffering. Otherwise,
     * writes from ZipOutputStream may not be detected correctly.
     */
    private static class SparseOutputStream extends FilterOutputStream {
        private final FileChannel channel;
        private boolean sparse = true; // True until the last CEN is written
        private long position = 0;

        public SparseOutputStream(FileOutputStream fos) {
            super(fos);
            this.channel = fos.getChannel();
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            position += len;
            if (sparse) {
                // Until finding the last CEN, we don't actually write anything,
                // but instead simply advance the position, creating a sparse file
                channel.position(position);
                // Check for last CEN record
                if (Arrays.equals(LAST_CEN_COMMENT_BYTES, 0, LAST_CEN_COMMENT_BYTES.length, b, off, len)) {
                    // From here on, write actual bytes
                    sparse = false;
                }
            } else {
                // Regular write
                out.write(b, off, len);
            }
        }

        @Override
        public void write(int b) throws IOException {
            position++;
            if (sparse) {
                channel.position(position);
            } else {
                out.write(b);
            }
        }
    }
}
