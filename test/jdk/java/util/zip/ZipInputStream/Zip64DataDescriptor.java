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

/*
 * @test
 * @bug 8303866
 * @summary ZipInputStream should read 8-byte data descriptors if the LOC has
 *   a ZIP64 extended information extra field
 * @run junit Zip64DataDescriptor
 */


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Zip64DataDescriptor {

    // A byte array holding a small-sized Zip64 ZIP file, described below
    private byte[] zip64File;

    // A byte array holding a ZIP used for testing invalid Zip64 extra fields
    private byte[] invalidZip64;

    @BeforeEach
    public void setup() throws IOException {
        /*
         * Structure of the ZIP64 file used below . Note the presence
         * of a Zip64 extended information extra field and the
         * Data Descriptor having 8-byte values for csize and size.
         *
         * The file was produced using the zip command on MacOS
         * (zip 3.0, by Info-ZIP), in streamming mode (to enable Zip64),
         * using the -fd option (to force the use of data descriptors)
         *
         * The following command was used:
         * <pre>echo hello | zip -fd > hello.zip</pre>
         *
         * ------  Local File Header  ------
         * 000000  signature          0x04034b50
         * 000004  version            45
         * 000006  flags              0x0008
         * 000008  method             8              Deflated
         * 000010  time               0xb180         22:12
         * 000012  date               0x565c         2023-02-28
         * 000014  crc                0x00000000
         * 000018  csize              -1
         * 000022  size               -1
         * 000026  nlen               1
         * 000028  elen               20
         * 000030  name               1 bytes        '-'
         * 000031  ext id             0x0001         Zip64 extended information extra field
         * 000033  ext size           16
         * 000035  z64 size           0
         * 000043  z64 csize          0
         *
         * ------  File Data  ------
         * 000051  data               8 bytes
         *
         * ------  Data Desciptor  ------
         * 000059  signature          0x08074b50
         * 000063  crc                0x363a3020
         * 000067  csize              8
         * 000075  size               6
         * 000083  ...
         */

        String hex = """
                504b03042d000800080080b15c5600000000ffffffffffffffff01001400
                2d0100100000000000000000000000000000000000cb48cdc9c9e7020050
                4b070820303a3608000000000000000600000000000000504b01021e032d
                000800080080b15c5620303a360800000006000000010000000000000001
                000000b011000000002d504b050600000000010001002f00000053000000
                0000""";

        zip64File = HexFormat.of().parseHex(hex.replaceAll("\n", ""));

        // Create the ZIP file used for testing that invalid Zip64 extra fields are ignored
        // This ZIP has the regular 4-bit data descriptor

        byte[] extra = new byte[Long.BYTES  + Long.BYTES + Short.BYTES * 2]; // Size of a regular Zip64 extra field
        ByteBuffer buffer = ByteBuffer.wrap(extra).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort(0, (short) 123); // Not processed by ZipEntry.setExtra
        buffer.putShort(Short.BYTES, (short) (extra.length - 4));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(baos)) {
            ZipEntry ze = new ZipEntry("hello");
            ze.setExtra(extra);
            zo.putNextEntry(ze);
            zo.write("hello\n".getBytes(StandardCharsets.UTF_8));
        }

        invalidZip64 = baos.toByteArray();

        // Set Zip64 magic values on compressed and uncompressed size fields
        ByteBuffer.wrap(invalidZip64).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ZipFile.LOCSIZ, 0xFFFFFFFF)
                .putInt(ZipFile.LOCLEN, 0xFFFFFFFF);

    }

    /*
     * Verify that small-sized Zip64 entries can be parsed by ZipInputStream
     */
    @Test
    public void shouldReadZip64Descriptor() throws IOException {
        readZipInputStream(zip64File);
    }

    /*
     * For maximal backward compatibility when reading Zip64 descriptors, invalid
     * Zip64 extra data sizes should be ignored
     */
    @Test
    public void shouldIgnoreInvalidExtraSize() throws IOException {

        setExtraSize((short) 42);


        readZipInputStream(invalidZip64);

    }

    /*
     * Validate that an extra data size exceeding the length of the extra field is ignored
     */
    @Test
    public void shouldIgnoreExcessiveExtraSize() throws IOException {

        setExtraSize(Short.MAX_VALUE);


        readZipInputStream(invalidZip64);
    }

    /*
     * Validate that the Data Descriptor is read with 32-bit fields if neither the
     * LOC's 'uncompressed size' or 'compressed size' fields have the Zip64 magic value,
     * even when there is a Zip64 field in the extra field.
     */
    @Test
    public void shouldIgnoreNoMagicMarkers() throws IOException {
        // Set compressed and uncompressed size fields to zero
        ByteBuffer.wrap(invalidZip64).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(ZipFile.LOCSIZ, 0)
                .putInt(ZipFile.LOCLEN, 0);


        readZipInputStream(invalidZip64);
    }

    /*
     * Validate that an extra data size exceeding the length of the extra field is ignored
     */
    @Test
    public void shouldIgnoreTrucatedZip64Extra() throws IOException {

        truncateZip64();

        readZipInputStream(invalidZip64);
    }

    /**
     * Updates the 16-bit 'data size' field of the Zip64 extended information field,
     * potentially to an invalid value.
     * @param size the value to set in the 'data size' field.
     */
    private void setExtraSize(short size) {
        ByteBuffer buffer = ByteBuffer.wrap(zip64File).order(ByteOrder.LITTLE_ENDIAN);
        // Compute the offset to the Zip64 data block size field
        short nlen = buffer.getShort(ZipFile.LOCNAM);
        int dataSizeOffset = ZipFile.LOCHDR + nlen + Short.BYTES;
        buffer.putShort(dataSizeOffset, size);
    }

    /**
     * Puts a truncated Zip64 field (just the tag) at the end of the LOC extra field.
     * The beginning of the extra field is filled with a generic extra field containing
     * just zeros.
     */
    private void truncateZip64() {
        ByteBuffer buffer = ByteBuffer.wrap(invalidZip64).order(ByteOrder.LITTLE_ENDIAN);
        // Get the LOC name and extra sizes
        short nlen = buffer.getShort(ZipFile.LOCNAM);
        short elen = buffer.getShort(ZipFile.LOCEXT);
        int cenOffset = ZipFile.LOCHDR + nlen + elen;

        // Zero out the extra field
        int estart = ZipFile.LOCHDR + nlen;
        buffer.put(estart, new byte[elen]);
        // Put a generic extra field in the start
        buffer.putShort(estart, (short) 42);
        buffer.putShort(estart + Short.BYTES, (short) (elen - 4 - 2));
        // Put a truncated (just the tag) Zip64 field at the end
        buffer.putShort(cenOffset - Short.BYTES, (short) 0x0001);
    }

    /*
     * Consume all entries in a ZipInputStream, possibly throwing a
     * ZipException if the reading of any entry stream fails.
     */
    private void readZipInputStream(byte[] zip) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ( (e = in.getNextEntry()) != null) {
                assertEquals("hello\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }
}
