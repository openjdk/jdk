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
 * @summary Verify that ZipFile rejects ZIP64 files with CEN sizes exceeding the CEN size limit
 * @run testng/othervm TestTooManyEntries
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Collections;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestTooManyEntries {

    private static int ENDSIG = 0x06054b50;               // "PK\005\006"
    private static final int ZIP64_ENDSIG = 0x06064b50;   // "PK\006\006"
    private static final int ZIP64_LOCSIG = 0x07064b50;   // "PK\006\007"
    private static final int ENDHDR = 22;                 // End of central directory record size
    private static final int  ZIP64_ENDHDR = 56;          // ZIP64 end header size
    private static final int  ZIP64_LOCHDR = 20;          // ZIP64 end loc header size

    private static final int ZIP64_MAGICVAL = 0xFFFFFFFF; // Magic vals for ZIP64 CEN length and offset


    private Path huge64; // ZIP64 file with CEN size exceeding limit
    private Path big64;  // ZIP64 file with CEN size exactly on the limit
    private Path huge;   // ZIP file with CEN size  exceeding limit
    private Path big;    // ZIP file with CEN size exactly on the limit

    /**
     * Create ZIP64 and regular ZIP files with CEN sizes exactly on and exceeding the CEN size limit
     */
    @BeforeTest
    public void setup() throws IOException {
        var limit = Integer.MAX_VALUE - ENDHDR - 1;
        var exceedingLimit = limit + 1;

        big64 = zipWithCenSize("big64Zip.zip", limit, true);
        huge64 = zipWithCenSize("huge64Zip.zip", exceedingLimit, true);

        big = zipWithCenSize("bigZip.zip", limit, false);
        huge = zipWithCenSize("hugeZip.zip", exceedingLimit, false);
    }

    /**
     * Validates that a ZIP64 end of central directory record with
     * a CEN length exceeding the CEN limit is rejected
     */
    @Test
    public void shouldRejectHuge64() throws IOException {
        assertTooLarge(huge64);
    }

    /**
     * Validates that a regular end of central directory record with
     * a CEN length exceeding the CEN limit is rejected
     */
    @Test
    public void shouldRejectHuge() throws IOException {
        assertTooLarge(huge);
    }

    /**
     * Validate that a ZIP64 end of central directory record with a
     * valid CEN size is not rejected
     */
    @Test
    public void shouldAcceptBig64() throws IOException {
        assertEmptyZip(big64);
    }

    /**
     * Validate that a regular end of central directory record with a
     * valid CEN size is not rejected
     */
    @Test
    public void shouldAcceptBig() throws IOException {
        assertEmptyZip(big);
    }


    /**
     * Assert that ZipFile can open the file without exceptions
     * and that the file has zero entries.
     *
     * Note: Even if files created by this test are technically invalid ZIPs,
     * ZipFile will accept them as empty because the initCEN method short-circuit
     * when it detects that the end of central directory record is at
     * offset 0 (meaning it will treat the ZIP as having zero entries)
     */
    private void assertEmptyZip(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            assertEquals( zf.size(), 0);
        }
    }

    /**
     * Assert that opening a file with ZipFile throws with a ZipException
     * with the message "invalid END header (central directory size too large)"
     */
    private void assertTooLarge(Path zip) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            fail("Expected ZipFile to throw ZipException");
        } catch (ZipException e) {
            var expected = "invalid END header (central directory size too large)";
            var actual = e.getMessage();
            assertTrue(expected.equals(actual),
                    "Expected ZipException message '%s', got '%s'".formatted(expected, actual));
        }
    }

    /**
     * Create an "empty" ZIP, containing only the required end
     * of central directory record(s) with the specified CEN size.
     *
     * If {@code zip64} is true, ZIP64 end of central directory record
     * and locator are output first, followed by the regular end of
     * central directory record with ZIP64 magic values
     */
    private Path zipWithCenSize(String name, int cenSize, boolean zip64) throws IOException {
        Path z = Path.of(name);

        try (FileOutputStream out  = new FileOutputStream(z.toFile())) {

            if(zip64) {
                // Write the Zip64 end of central directory record and locator
                writeZip64End(out, cenSize, zip64);
            }

            // Write a regular End of central directory with Zip64 magic values
            writeEndOfCen(out, zip64, zip64 ? ZIP64_MAGICVAL : cenSize);
        }

        return z;
    }

    /**
     * Writes the ZIP64 "end of central directory record" and
     * "end of central directory locator" records.
     */
    private void writeZip64End(FileOutputStream out, int cenSize, boolean zip64) throws IOException {

        ByteBuffer e64 = ByteBuffer.allocate(ZIP64_ENDHDR + ZIP64_LOCHDR).order(ByteOrder.LITTLE_ENDIAN);

        //zip64 end of central directory record
        e64.putInt(ZIP64_ENDSIG);                // zip64 END record signature
        e64.putLong(ZIP64_ENDHDR - 12);    // size of zip64 end
        e64.putShort((short) 45);                // version made by
        e64.putShort((short) 45);                // version needed to extract
        e64.putInt(0);                     // number of this disk
        e64.putInt(0);                     // central directory start disk
        e64.putLong(1);                    // number of directory entries on disk
        e64.putLong(1);                    // number of directory entries
        e64.putLong(cenSize);                    // length of central directory
        e64.putLong(0);                    // offset of central directory

        //zip64 end of central directory locator
        e64.putInt(ZIP64_LOCSIG);                // zip64 END locator signature
        e64.putInt(0);                     // zip64 END start disk
        e64.putLong(0);                    // offset of zip64 END
        e64.putInt(1);                     // total number of disks

        e64.flip();
        out.getChannel().write(e64);

    }

    /**
     * Write a regular "end of central directory" record with a given CEN size.
     *
     * If {@code zip64} is true, write ZIP64 magic values instead
     */
    private void writeEndOfCen(FileOutputStream out, boolean zip64, int cenLen) throws IOException {
        ByteBuffer ecen = ByteBuffer.allocate(ENDHDR).order(ByteOrder.LITTLE_ENDIAN);
        ecen.putInt(ENDSIG);                  // END record signature
        ecen.putShort((short) 0);             // number of this disk
        ecen.putShort((short) 0);             // central directory start disk
        ecen.putShort((short) 1);             // number of directory entries on disk
        ecen.putShort((short) 1);             // total number of directory entries
        ecen.putInt(cenLen);                  // length of central directory
        ecen.putInt(ZIP64_MAGICVAL);          // offset of central directory
        ecen.putShort((short) 0);             // zip file comment

        ecen.flip();
        out.getChannel().write(ecen);
    }
}
