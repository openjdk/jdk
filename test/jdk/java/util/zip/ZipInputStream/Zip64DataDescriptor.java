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
 * @summary ZipInputStream should read 8-byte data descriptors if the LOC has
 *   a ZIP64 extended information extra field
 * @run testng Zip64DataDescriptor
 */


import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

public class Zip64DataDescriptor {

    // A byte array holding a small-sized Zip64 ZIP file, described below
    private byte[] zip64File;

    @BeforeMethod
    public void setup() {
        /**
         * Structure of the ZIP64 file used below . Note the precense
         * of a Zip64 extended information extra field and the
         * Data Descriptor having 8-byte values for csize and size.
         *
         * The file was produced using the following command:
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
    }

    /**
     * Verify that small-sized Zip64 entries can be parsed by ZipInputStream
     */
    @Test
    public void shouldReadZip64Descriptor() throws IOException {
        readZipInputStream();
    }

    /**
     * For maximal backward compatibility when reading Zip64 descriptors, invalid
     * Zip64 extra data sizes should be ignored
     */
    @Test
    public void shouldIgnoreInvalidExtraSize() {

        setExtraSize((short) 42);

        ZipException ex = expectThrows(ZipException.class, () -> {
            readZipInputStream();
        });

        assertEquals(ex.getMessage(), "invalid entry size (expected 0 but got 6 bytes)");
    }

    /**
     * Validate that an extra data size exceeding the length of the extra field is ignored
     */
    @Test
    public void shouldIgnoreExcessiveExtraSize() {

        setExtraSize(Short.MAX_VALUE);

        ZipException ex = expectThrows(ZipException.class, () -> {
            readZipInputStream();
        });

        assertEquals(ex.getMessage(), "invalid entry size (expected 0 but got 6 bytes)");
    }

    /**
     * Updates the 16-bit 'data size' field of the Zip64 extended information field,
     * potentially to an invalid value.
     * @param size the value to set in the 'data size' field.
     */
    private void setExtraSize(short size) {
        int extSizeOffset = 33;
        ByteBuffer.wrap(zip64File).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(extSizeOffset, size);
    }

    /**
     * Consume all entries in a ZipInputStream, possibly throwing a
     * ZipException if the reading of any entry stream fails.
     */
    private void readZipInputStream() throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip64File))) {
            ZipEntry e;
            while ( (e = in.getNextEntry()) != null) {
                in.transferTo(OutputStream.nullOutputStream());
            }
        }
    }
}
