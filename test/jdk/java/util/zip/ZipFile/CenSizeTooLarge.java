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
 * @run testng/othervm CenSizeTooLarge
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

/**
 * This test augments {@link TestTooManyEntries}. It creates small ZIPs where the
 * the CEN size is manipulated directly to the desired value. While this means the ZIP files
 * produced are technically invalid, it helps this test run fast with much less resources
 */
public class CenSizeTooLarge {

    private static final int ENDHDR = 22;          // End of central directory record size
    private static final int CEN_SIZE_OFFSET = 12; // Offset of CEN size field within ENDHDR
    // Maximum allowed CEN size allowed by ZipFile
    private static int MAX_CEN_SIZE = Integer.MAX_VALUE - ENDHDR -1;

    // Expected message when CEN size is too large
    private static final String INVALID_CEN_BAD_SIZE = "invalid END header \\(bad central directory size\\)";
    // Expected message when CEN size does not match file size
    private static final String INVALID_CEN_SIZE_TOO_LARGE = "invalid END header \\(central directory size too large\\)";

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
     * a CEN length exceeding the CEN limit is rejected
     */
    @Test(expectedExceptions = ZipException.class,
            expectedExceptionsMessageRegExp = INVALID_CEN_SIZE_TOO_LARGE)
    public void shouldRejectTooLargeCenSize() throws IOException {
        int size = MAX_CEN_SIZE +1;

        Path zip = zipWithCenSize(size, "cen-size-too-large.zip");

        try (ZipFile zf = new ZipFile(zip.toFile())) {
        }
    }

    /**
     * Validate that an end of central directory record with a
     * CEN size within the limit is not rejected because of its CEN size
     *
     * Note: Since this is just a small file with a single entry, the
     * CEN size in the END record will not match the actual CEN
     * and file size. ZipFile should detect this and reject
     * as invalid (but not because the size is > limit)
     */
    @Test(expectedExceptions = ZipException.class,
            expectedExceptionsMessageRegExp = INVALID_CEN_BAD_SIZE)
    public void shouldRejectInvalidCenSize() throws IOException {

        int size = MAX_CEN_SIZE;

        Path zip = zipWithCenSize(size, "cen-size-on-limit.zip");

        try (ZipFile zf = new ZipFile(zip.toFile())) {
        }
    }

    /**
     * Create an ZIP file with a single entry, then modify the CEN size
     * in the End of central directory record to the given size
     *
     * The resulting ZIP is technically not valid, but it does allow us
     * to test that the large CEN size is rejected
     */
    private Path zipWithCenSize(int cenSize, String name) throws IOException {
        Path z = Path.of(name);

        // Change the "Central directory size" field of the
        // "End of central directory" record
        int offset = zipBytes.length - ENDHDR + CEN_SIZE_OFFSET;
        ByteBuffer.wrap(zipBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(offset, cenSize);

        Files.write(z, zipBytes);
        return z;
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
}
