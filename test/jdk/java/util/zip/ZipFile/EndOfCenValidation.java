/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.util
 * @summary Verify that ZipFile rejects files with CEN sizes exceeding the implementation limit
 * @run junit/othervm EndOfCenValidation
 */

import jdk.internal.util.ArraysSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test augments {@link TestTooManyEntries}. It creates sparse ZIPs where
 * the CEN size is inflated to the desired value. This helps this test run
 * fast with much less resources.
 *
 * While the CEN in these files are zero-filled and the produced ZIPs are technically
 * invalid, the CEN is never actually read by ZipFile since it does
 * 'End of central directory record' (END header) validation before reading the CEN.
 */
public class EndOfCenValidation {

    // Zip files produced by this test
    public static final Path CEN_TOO_LARGE_ZIP = Path.of("cen-size-too-large.zip");
    public static final Path INVALID_CEN_SIZE = Path.of("invalid-zen-size.zip");
    public static final Path BAD_CEN_OFFSET_ZIP = Path.of("bad-cen-offset.zip");
    // Some ZipFile constants for manipulating the 'End of central directory record' (END header)
    private static final int ENDHDR = ZipFile.ENDHDR; // End of central directory record size
    private static final int ENDSIZ = ZipFile.ENDSIZ; // Offset of CEN size field within ENDHDR
    private static final int ENDOFF = ZipFile.ENDOFF; // Offset of CEN offset field within ENDHDR
    // Maximum allowed CEN size allowed by ZipFile
    private static final int MAX_CEN_SIZE = ArraysSupport.SOFT_MAX_ARRAY_LENGTH;

    // Expected message when CEN size does not match file size
    private static final String INVALID_CEN_BAD_SIZE = "invalid END header (bad central directory size)";
    // Expected message when CEN offset is too large
    private static final String INVALID_CEN_BAD_OFFSET = "invalid END header (bad central directory offset)";
    // Expected message when CEN size is too large
    private static final String INVALID_CEN_SIZE_TOO_LARGE = "invalid END header (central directory size too large)";
    // Expected message when total entry count is too large
    private static final String INVALID_BAD_ENTRY_COUNT = "invalid END header (total entries count too large)";

    // A valid ZIP file, used as a template
    private byte[] zipBytes;

    /**
     * Create a valid ZIP file, used as a template
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void setup() throws IOException {
        zipBytes = templateZip();
    }

    /**
     * Delete big files after test, in case the file system did not support sparse files.
     * @throws IOException if an error occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(CEN_TOO_LARGE_ZIP);
        Files.deleteIfExists(INVALID_CEN_SIZE);
        Files.deleteIfExists(BAD_CEN_OFFSET_ZIP);
    }

    /**
     * Validates that an 'End of central directory record' (END header) with a CEN
     * length exceeding {@link #MAX_CEN_SIZE} limit is rejected
     * @throws IOException if an error occurs
     */
    @Test
    public void shouldRejectTooLargeCenSize() throws IOException {
        int size = MAX_CEN_SIZE + 1;

        Path zip = zipWithModifiedEndRecord(size, true, 0, CEN_TOO_LARGE_ZIP);

        ZipException ex = assertThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(INVALID_CEN_SIZE_TOO_LARGE, ex.getMessage());
    }

    /**
     * Validate that an 'End of central directory record' (END header)
     * where the value of the CEN size field exceeds the position of
     * the END header is rejected.
     * @throws IOException if an error occurs
     */
    @Test
    public void shouldRejectInvalidCenSize() throws IOException {

        int size = MAX_CEN_SIZE;

        Path zip = zipWithModifiedEndRecord(size, false, 0, INVALID_CEN_SIZE);

        ZipException ex = assertThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(INVALID_CEN_BAD_SIZE, ex.getMessage());
    }

    /**
     * Validate that an 'End of central directory record' (the END header)
     * where the value of the CEN offset field is larger than the position
     * of the END header minus the CEN size is rejected
     * @throws IOException if an error occurs
     */
    @Test
    public void shouldRejectInvalidCenOffset() throws IOException {

        int size = MAX_CEN_SIZE;

        Path zip = zipWithModifiedEndRecord(size, true, 100, BAD_CEN_OFFSET_ZIP);

        ZipException ex = assertThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(INVALID_CEN_BAD_OFFSET, ex.getMessage());
    }

    /**
     * Validate that a 'Zip64 End of Central Directory' record (the END header)
     * where the value of the 'total entries' field is larger than what fits
     * in the CEN size is rejected.
     *
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @ValueSource(longs = {
            -1,                   // Negative
            Long.MIN_VALUE,       // Very negative
            0x3B / 3L - 1,        // Cannot fit in test ZIP's CEN
            MAX_CEN_SIZE / 3 + 1, // Too large to allocate int[] entries array
            Long.MAX_VALUE        // Unreasonably large
    })
    public void shouldRejectBadTotalEntries(long totalEntries) throws IOException {
        /**
         * A small ZIP using the ZIP64 format.
         *
         * ZIP created using: "echo -n hello | zip zip64.zip -"
         * Hex encoded using: "cat zip64.zip | xxd -ps"
         *
         * The file has the following structure:
         *
         * 0000 LOCAL HEADER #1       04034B50
         * 0004 Extract Zip Spec      2D '4.5'
         * 0005 Extract OS            00 'MS-DOS'
         * 0006 General Purpose Flag  0000
         * 0008 Compression Method    0000 'Stored'
         * 000A Last Mod Time         5947AB78 'Mon Oct  7 21:27:48 2024'
         * 000E CRC                   363A3020
         * 0012 Compressed Length     FFFFFFFF
         * 0016 Uncompressed Length   FFFFFFFF
         * 001A Filename Length       0001
         * 001C Extra Length          0014
         * 001E Filename              '-'
         * 001F Extra ID #0001        0001 'ZIP64'
         * 0021   Length              0010
         * 0023   Uncompressed Size   0000000000000006
         * 002B   Compressed Size     0000000000000006
         * 0033 PAYLOAD               hello.
         *
         * 0039 CENTRAL HEADER #1     02014B50
         * 003D Created Zip Spec      1E '3.0'
         * 003E Created OS            03 'Unix'
         * 003F Extract Zip Spec      2D '4.5'
         * 0040 Extract OS            00 'MS-DOS'
         * 0041 General Purpose Flag  0000
         * 0043 Compression Method    0000 'Stored'
         * 0045 Last Mod Time         5947AB78 'Mon Oct  7 21:27:48 2024'
         * 0049 CRC                   363A3020
         * 004D Compressed Length     00000006
         * 0051 Uncompressed Length   FFFFFFFF
         * 0055 Filename Length       0001
         * 0057 Extra Length          000C
         * 0059 Comment Length        0000
         * 005B Disk Start            0000
         * 005D Int File Attributes   0001
         *      [Bit 0]               1 Text Data
         * 005F Ext File Attributes   11B00000
         * 0063 Local Header Offset   00000000
         * 0067 Filename              '-'
         * 0068 Extra ID #0001        0001 'ZIP64'
         * 006A   Length              0008
         * 006C   Uncompressed Size   0000000000000006
         *
         * 0074 ZIP64 END CENTRAL DIR 06064B50
         *      RECORD
         * 0078 Size of record        000000000000002C
         * 0080 Created Zip Spec      1E '3.0'
         * 0081 Created OS            03 'Unix'
         * 0082 Extract Zip Spec      2D '4.5'
         * 0083 Extract OS            00 'MS-DOS'
         * 0084 Number of this disk   00000000
         * 0088 Central Dir Disk no   00000000
         * 008C Entries in this disk  0000000000000001
         * 0094 Total Entries         0000000000000001
         * 009C Size of Central Dir   000000000000003B
         * 00A4 Offset to Central dir 0000000000000039
         *
         * 00AC ZIP64 END CENTRAL DIR 07064B50
         *      LOCATOR
         * 00B0 Central Dir Disk no   00000000
         * 00B4 Offset to Central dir 0000000000000074
         * 00BC Total no of Disks     00000001
         *
         * 00C0 END CENTRAL HEADER    06054B50
         * 00C4 Number of this disk   0000
         * 00C6 Central Dir Disk no   0000
         * 00C8 Entries in this disk  0001
         * 00CA Total Entries         0001
         * 00CC Size of Central Dir   0000003B
         * 00D0 Offset to Central Dir FFFFFFFF
         * 00D4 Comment Length        0000
         */

        byte[] zipBytes = HexFormat.of().parseHex("""
                504b03042d000000000078ab475920303a36ffffffffffffffff01001400
                2d010010000600000000000000060000000000000068656c6c6f0a504b01
                021e032d000000000078ab475920303a3606000000ffffffff01000c0000
                00000001000000b011000000002d010008000600000000000000504b0606
                2c000000000000001e032d00000000000000000001000000000000000100
                0000000000003b000000000000003900000000000000504b060700000000
                740000000000000001000000504b050600000000010001003b000000ffff
                ffff0000
                """.replaceAll("\n",""));

        // Buffer to manipulate the above ZIP
        ByteBuffer buf = ByteBuffer.wrap(zipBytes).order(ByteOrder.LITTLE_ENDIAN);
        // Offset of the 'total entries' in the 'ZIP64 END CENTRAL DIR' record
        // Update ZIP64 entry count to a value which cannot possibly fit in the small CEN
        buf.putLong(0x94, totalEntries);
        // The corresponding END field needs the ZIP64 magic value
        buf.putShort(0xCA, (short) 0xFFFF);
        // Write the ZIP to disk
        Path zipFile = Path.of("bad-entry-count.zip");
        Files.write(zipFile, zipBytes);

        // Verify that the END header is rejected
        ZipException ex = assertThrows(ZipException.class, () -> {
            try (var zf = new ZipFile(zipFile.toFile())) {
            }
        });

        assertEquals(INVALID_BAD_ENTRY_COUNT, ex.getMessage());
    }

    /**
     * Create an ZIP file with a single entry, then modify the CEN size
     * in the 'End of central directory record' (END header)  to the given size.
     *
     * The CEN is optionally "inflated" with trailing zero bytes such that
     * its actual size matches the one stated in the END header.
     *
     * The CEN offset is optiontially adjusted by the given amount
     *
     * The resulting ZIP is technically not valid, but it does allow us
     * to test that large or invalid CEN sizes are rejected
     * @param cenSize the CEN size to put in the END record
     * @param inflateCen if true, zero-pad the CEN to the desired size
     * @param cenOffAdjust Adjust the CEN offset field of the END record with this amount
     * @throws IOException if an error occurs
     */
    private Path zipWithModifiedEndRecord(int cenSize,
                                          boolean inflateCen,
                                          int cenOffAdjust,
                                          Path zip) throws IOException {

        // A byte buffer for reading the END
        ByteBuffer buffer = ByteBuffer.wrap(zipBytes.clone()).order(ByteOrder.LITTLE_ENDIAN);

        // Offset of the END header
        int endOffset = buffer.limit() - ENDHDR;

        // Modify the CEN size
        int sizeOffset = endOffset + ENDSIZ;
        int currentCenSize = buffer.getInt(sizeOffset);
        buffer.putInt(sizeOffset, cenSize);

        // Optionally modify the CEN offset
        if (cenOffAdjust != 0) {
            int offOffset = endOffset + ENDOFF;
            int currentCenOff = buffer.getInt(offOffset);
            buffer.putInt(offOffset, currentCenOff + cenOffAdjust);
        }

        // When creating a sparse file, the file must not already exit
        Files.deleteIfExists(zip);

        // Open a FileChannel for writing a sparse file
        EnumSet<StandardOpenOption> options = EnumSet.of(StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE,
                StandardOpenOption.SPARSE);

        try (FileChannel channel = FileChannel.open(zip, options)) {

            // Write everything up to END
            channel.write(buffer.slice(0, buffer.limit() - ENDHDR));

            if (inflateCen) {
                // Inject "empty bytes" to make the actual CEN size match the END
                int injectBytes = cenSize - currentCenSize;
                channel.position(channel.position() + injectBytes);
            }
            // Write the modified END
            channel.write(buffer.slice(buffer.limit() - ENDHDR, ENDHDR));
        }
        return zip;
    }

    /**
     * Produce a byte array of a ZIP with a single entry
     *
     * @throws IOException if an error occurs
     */
    private byte[] templateZip() throws IOException {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(bout)) {
            ZipEntry entry = new ZipEntry("duke.txt");
            zo.putNextEntry(entry);
            zo.write("duke".getBytes(StandardCharsets.UTF_8));
        }
        return bout.toByteArray();
    }
}
