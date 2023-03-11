/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify that ZipFile rejects files with CEN sizes exceeding the limit
 * @run testng/othervm EndOfCenValidation
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

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

import static org.testng.Assert.*;

/**
 * This test augments {@link TestTooManyEntries}. It creates sparse ZIPs where the
 * the CEN size is inflated to the desired value. This helps this test run
 * fast with much less resources.
 *
 * While the CEN in these files are zero-filled and the produced ZIPs are technically
 * invalid, the CEN is never actually read by ZipFile since it does END
 * record validation before reading the CEN.
 */
public class EndOfCenValidation {

    private static final int ENDHDR = 22;          // End of central directory record size
    private static final int CEN_SIZE_OFFSET = 12; // Offset of CEN size field within ENDHDR
    private static final int CEN_OFF_OFFSET = 16; // Offset of CEN offset field within ENDHDR
    // Maximum allowed CEN size allowed by ZipFile
    private static int MAX_CEN_SIZE = Integer.MAX_VALUE - ENDHDR -1;

    // Expected message when CEN size does not match file size
    private static final String INVALID_CEN_BAD_SIZE = "invalid END header (bad central directory size)";
    // Expected message when CEN offset is too large
    private static final String INVALID_CEN_BAD_OFFSET = "invalid END header (bad central directory offset)";
    // Expected message when CEN size is too large
    private static final String INVALID_CEN_SIZE_TOO_LARGE = "invalid END header (central directory size too large)";

    // A valid ZIP file, used as a template
    private byte[] zipBytes;

    /**
     * Create a valid ZIP file, used as a template
     */
    @BeforeTest
    public void setup() throws IOException {
        zipBytes = templateZip();
    }

    /**
     * Validates that an end of central directory record with
     * a CEN length exceeding {@link #MAX_CEN_SIZE} limit is rejected
     */
    @Test
    public void shouldRejectTooLargeCenSize() throws IOException {
        int size = MAX_CEN_SIZE +1;

        Path zip = zipWithModifiedEndRecord(size, true, 0, "cen-size-too-large.zip");

        ZipException ex = expectThrows(ZipException.class, () -> {
            openZip(zip);
        });

        assertEquals(ex.getMessage(), INVALID_CEN_SIZE_TOO_LARGE);
    }

    /**
     * Validate that an end of central directory record with a
     * CEN size which exceeds the position of the EOC record is rejected.
     */
    @Test
    public void shouldRejectInvalidCenSize() throws IOException {

        int size = MAX_CEN_SIZE;

        Path zip = zipWithModifiedEndRecord(size, false, 0, "invalid-zen-size.zip");

        ZipException ex = expectThrows(ZipException.class, () -> {
            openZip(zip);
        });

        assertEquals(ex.getMessage(), INVALID_CEN_BAD_SIZE);
    }

    /**
     * Validate that an end of central directory record with a CEN offset which
     * is larger than the EOC position minus the CEN size is rejected
     * @throws IOException
     */
    @Test
    public void shouldRejectInvalidCenOffset() throws IOException {

        int size = MAX_CEN_SIZE;

        Path zip = zipWithModifiedEndRecord(size, true, 100, "bad-cen-offset.zip");

        ZipException ex = expectThrows(ZipException.class, () -> {
            openZip(zip);
        });

        assertEquals(ex.getMessage(), INVALID_CEN_BAD_OFFSET);
    }

    /**
     * Create an ZIP file with a single entry, then modify the CEN size
     * in the End of central directory record to the given size.
     *
     * The CEN is optionally "inflated" with trailing zero bytes such that
     * its actual size matches the one stated in the Eoc record.
     *
     * The CEN offset is optiontially adjusted by the given amount
     *
     * The resulting ZIP is technically not valid, but it does allow us
     * to test that large or invalid CEN sizes are rejected
     * @param cenSize the CEN size to put in the END record
     * @param inflateCen if true, zero-pad the CEN to the desired size
     * @param cenOffAdjust Adjust the CEN offset field of the END record with this amount
     */
    private Path zipWithModifiedEndRecord(int cenSize,
                                          boolean inflateCen,
                                          int cenOffAdjust,
                                          String name) throws IOException {
        Path zip = Path.of(name);
        Files.deleteIfExists(zip);

        // A byte buffer for reading the EOC
        ByteBuffer buffer = ByteBuffer.wrap(Arrays.copyOf(zipBytes, zipBytes.length))
                .order(ByteOrder.LITTLE_ENDIAN);

        // Offset of the EOC record
        int eocOff = buffer.limit() - ENDHDR;

        // Modify the CEN size
        int sizeOffset = eocOff + CEN_SIZE_OFFSET;
        int currentCenSize = buffer.getInt(sizeOffset);
        buffer.putInt(sizeOffset, cenSize);

        // Optionally modify the CEN offset
        if (cenOffAdjust != 0) {
            int offOffset = eocOff + CEN_OFF_OFFSET;
            int currentCenOff = buffer.getInt(offOffset);
            buffer.putInt(offOffset, currentCenOff + cenOffAdjust);
        }

        // Open a FileChannel for writing a sparse file
        EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.SPARSE);

        try (FileChannel channel = FileChannel.open(zip, options)) {

            // Write everything up to EOC
            channel.write(buffer.slice(0, buffer.limit() - ENDHDR));

            if (inflateCen) {
                // Inject "empty bytes" to make the actual CEN size match the EOC
                int injectBytes = cenSize - currentCenSize;
                channel.position(channel.position() + injectBytes);
            }
            // Write the modified EOC
            channel.write(buffer.slice(buffer.limit()-ENDHDR, ENDHDR));
        }
        return zip;
    }

    // Produce a byte array of a ZIP with a single entry
    private byte[] templateZip() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(bout)) {
            zout.putNextEntry(new ZipEntry("duke.txt"));
        }
        byte[] zipBytes = bout.toByteArray();
        return zipBytes;
    }
    private static void openZip(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
        }
    }
}
