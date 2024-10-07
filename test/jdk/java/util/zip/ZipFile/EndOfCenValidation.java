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
 * @run testng/othervm EndOfCenValidation
 */

import jdk.internal.util.ArraysSupport;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

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

import static org.testng.Assert.*;

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
    private static int MAX_CEN_SIZE = ArraysSupport.SOFT_MAX_ARRAY_LENGTH;

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
    @BeforeTest
    public void setup() throws IOException {
        zipBytes = templateZip();
    }

    /**
     * Delete big files after test, in case the file system did not support sparse files.
     * @throws IOException if an error occurs
     */
    @AfterTest
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

        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(ex.getMessage(), INVALID_CEN_SIZE_TOO_LARGE);
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

        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(ex.getMessage(), INVALID_CEN_BAD_SIZE);
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

        ZipException ex = expectThrows(ZipException.class, () -> {
            new ZipFile(zip.toFile());
        });

        assertEquals(ex.getMessage(), INVALID_CEN_BAD_OFFSET);
    }

    /**
     * Validate that an 'End of central directory record' (the END header)
     * where the value of the 'total entries' field is larger than what fits
     * in the CEN size is rejected.
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void shouldRejectBadTotalEntries() throws IOException {
        /*
         * A small ZIP using ZIP64. Since such a small ZIP64 file cannot
         * be produced using ZipOutputStream, it is included inline here.
         *
         * The file has the following structure:
         *
         * ------  Local File Header  ------
         * 000000  signature          0x04034b50
         * 000004  version            45
         * 000006  flags              0x0808
         * 000008  method             8              Deflated
         * 000010  time               0x542c         10:33:24
         * 000012  date               0x5947         2024-10-07
         * 000014  crc                0x00000000
         * 000018  csize              4294967295
         * 000022  size               4294967295
         * 000026  nlen               5
         * 000028  elen               20
         * 000030  name               5 bytes        'entry'
         * 000035  ext id             0x0001         Zip64 extended information extra field
         * 000037  ext size           16
         * 000039  z64 size           0
         * 000047  z64 csize          0
         *
         * ------  File Data  ------
         * 000055  data               7 bytes
         *
         * ------  Data Descriptor  ------
         * 000062  signature          0x08074b50
         * 000066  crc                0x3610a686
         * 000070  csize              7
         * 000078  size               5
         *
         * ------  Central Directory File Header  ------
         * 000086  signature          0x02014b50
         * 000090  made by version    45
         * 000092  extract version    45
         * 000094  flags              0x0808
         * 000096  method             8              Deflated
         * 000098  time               0x542c         10:33:24
         * 000100  date               0x5947         2024-10-07
         * 000102  crc                0x3610a686
         * 000106  csize              4294967295
         * 000110  size               4294967295
         * 000114  diskstart          65535
         * 000116  nlen               5
         * 000118  elen               32
         * 000120  clen               9
         * 000122  iattr              0x00
         * 000124  eattr              0x0000
         * 000128  loc offset         4294967295
         * 000132  name               5 bytes        'entry'
         * 000137  ext id             0x0001         Zip64 extended information extra field
         * 000139  ext size           28
         * 000141  z64 size           5
         * 000149  z64 csize          7
         * 000157  z64 locoff         0
         * 000165  z64 diskStart      0
         * 000169  comment            9 bytes        'A comment'
         *
         * ------  Zip64 End of Central Directory Record  ------
         * 000178  signature          0x06064b50
         * 000182  record size        44
         * 000190  made by version    45
         * 000192  extract version    45
         * 000194  this disk          0
         * 000198  cen disk           0
         * 000202  entries            1
         * 000210  total entries      1
         * 000218  cen size           92
         * 000226  cen offset         86
         *
         * ------  Zip64 End of Central Directory Locator  ------
         * 000234  signature          0x07064b50
         * 000238  eoc disk           0
         * 000242  eoc offset         178
         * 000250  total disks        1
         *
         * ------  End of Central Directory  ------
         * 000254  signature          0x06054b50
         * 000258  this disk          0
         * 000260  cen disk           0
         * 000262  entries disk       65535
         * 000264  entries total      65535
         * 000266  cen size           4294967295
         * 000270  cen offset         4294967295
         * 000274  clen               0
         */

        byte[] zipBytes = HexFormat.of().parseHex("""
               504b03042d00080808002c54475900000000ffffffffffffffff05001400
               656e7472790100100000000000000000000000000000000000cb48cdc9c9
               0700504b070886a6103607000000000000000500000000000000504b0102
               2d002d00080808002c54475986a61036ffffffffffffffff050020000900
               ffff000000000000ffffffff656e74727901001c00050000000000000007
               000000000000000000000000000000000000004120636f6d6d656e74504b
               06062c000000000000002d002d0000000000000000000100000000000000
               01000000000000005c000000000000005600000000000000504b06070000
               0000b20000000000000001000000504b050600000000ffffffffffffffff
               ffffffff0000
               """.replaceAll("\n",""));

        // Buffer to manipulate the above ZIP
        ByteBuffer buf = ByteBuffer.wrap(zipBytes).order(ByteOrder.LITTLE_ENDIAN);
        // Offset of the 'total entries' in the 'Zip64 End of Central Directory' record
        int totOffset = 210;
        // Update entry count to a value which cannot possibly fit in the small CEN
        buf.putLong(totOffset, MAX_CEN_SIZE / 3);

        // Write the ZIP to disk
        Path zipFile = Path.of("bad-entry-count.zip");
        Files.write(zipFile, zipBytes);

        // Verify that the END header is rejected
        ZipException ex = expectThrows(ZipException.class, () -> {
            try (var zf = new ZipFile(zipFile.toFile())) {
            }
        });

        assertEquals(ex.getMessage(), INVALID_BAD_ENTRY_COUNT);
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
