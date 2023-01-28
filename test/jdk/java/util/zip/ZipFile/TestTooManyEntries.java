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
 * @summary Verify that ZipFile rejects files with CEN sizes exceeding the limit
 * @run testng/othervm TestTooManyEntries
 */

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestTooManyEntries {

    private static final int ENDHDR = 22;          // End of central directory record size
    private static final int CEN_SIZE_OFFSET = 12; // Offset of CEN size field within ENDHDR
    private Path huge; // ZIP file with CEN size exceeding limit
    private Path big;  // ZIP file with CEN size exactly on the limit

    /**
     * Create ZIP files with CEN sizes exactly on and exceeding the CEN size limit
     */
    @BeforeTest
    public void setup() throws IOException {
        var limit = Integer.MAX_VALUE - ENDHDR - 1;
        var exceedingLimit = limit + 1;
        big = zipWithCenSize("bigZip.zip", limit);
        huge = zipWithCenSize("hugeZip.zip", exceedingLimit);
    }

    /**
     * Validates that an end of central directory record with
     * a CEN length exceeding the CEN limit is rejected
     */
    @Test
    public void shouldRejectTooLargeCenSize() throws IOException {
        assertRejected(huge, "invalid END header (central directory size too large)");
    }

    /**
     * Validate that an end of central directory record with a
     * valid CEN size is not rejected because of its CEN size
     */
    @Test
    public void shouldRejectInvalidCenSize() throws IOException {
        // Since the file has just a single entry, the CEN size in the END
        // record doesn not match the real CEN and file size.
        // Expect the CEN size should be rejected as invalid
        assertRejected(big, "invalid END header (bad central directory size)");
    }

    /**
     * Assert that opening a file with ZipFile throws with a ZipException
     * with the expected message
     */
    private void assertRejected(Path zip, String expectedMsg) throws IOException {
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            fail("Expected ZipFile to throw ZipException");
        } catch (ZipException e) {
            var actual = e.getMessage();
            assertTrue(expectedMsg.equals(actual),
                    "Expected ZipException message '%s', got '%s'".formatted(expectedMsg, actual));
        }
    }

    /**
     * Create an ZIP file with a single entry, then modify the CEN size
     * in the End of central directory record to the given size
     *
     * The resulting ZIP is technically not valid, but it does allow us
     * to test that the large CEN size is rejected
     */
    private Path zipWithCenSize(String name, int cenSize) throws IOException {
        Path z = Path.of(name);

        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        try (ZipOutputStream zout = new ZipOutputStream(bout)) {
            zout.putNextEntry(new ZipEntry("duke.txt"));
        }
        byte[] zipBytes = bout.toByteArray();

        ByteBuffer buffer = ByteBuffer.wrap(zipBytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(zipBytes.length - ENDHDR + CEN_SIZE_OFFSET, cenSize);
        Files.write(z, zipBytes);

        return z;
    }
}
