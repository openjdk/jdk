/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @modules java.base/jdk.internal.util
 * @summary Verify that ZipFileSystem rejects files with CEN sizes exceeding the implementation limit
 * @library /test/lib
 * @build jdk.test.lib.util.ZipUtils
 * @run junit/othervm EndOfCenValidation
 */

import jdk.internal.util.ArraysSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipException;

import static jdk.test.lib.util.ZipUtils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * This test augments {@link TestTooManyEntries}. It creates sparse ZIPs where
 * the CEN size is inflated to the desired value. This helps this test run
 * fast with much less resources.
 *
 * While the CEN in these files are zero-filled and the produced ZIPs are technically
 * invalid, the CEN is never actually read by ZipFileSystem since it does
 * 'End of central directory record' (END header) validation before reading the CEN.
 */
public class EndOfCenValidation {

    // Zip files produced by this test
    static final Path CEN_TOO_LARGE_ZIP = Path.of("cen-size-too-large.zip");
    static final Path INVALID_CEN_SIZE = Path.of("invalid-zen-size.zip");
    static final Path BAD_CEN_OFFSET_ZIP = Path.of("bad-cen-offset.zip");
    static final Path BAD_ENTRY_COUNT_ZIP = Path.of("bad-entry-count.zip");

    // Maximum allowed CEN size allowed by ZipFileSystem
    static final int MAX_CEN_SIZE = ArraysSupport.SOFT_MAX_ARRAY_LENGTH;

    /**
     * Delete big files after test, in case the file system did not support sparse files.
     * @throws IOException if an error occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(CEN_TOO_LARGE_ZIP);
        Files.deleteIfExists(INVALID_CEN_SIZE);
        Files.deleteIfExists(BAD_CEN_OFFSET_ZIP);
        Files.deleteIfExists(BAD_ENTRY_COUNT_ZIP);
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
        verifyRejection(zip, INVALID_CEN_SIZE_TOO_LARGE);
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
        verifyRejection(zip, INVALID_CEN_BAD_SIZE);
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
        verifyRejection(zip, INVALID_CEN_BAD_OFFSET);
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
        Path zip = zip64WithModifiedTotalEntries(BAD_ENTRY_COUNT_ZIP, totalEntries);
        verifyRejection(zip, INVALID_BAD_ENTRY_COUNT);
    }

    /**
     * Verify that ZipFileSystem.newFileSystem rejects the ZIP file with a ZipException
     * with the given message
     * @param zip ZIP file to open
     * @param msg exception message to expect
     */
    private static void verifyRejection(Path zip, String msg) {
        ZipException ex = assertThrows(ZipException.class, () -> {
            FileSystems.newFileSystem(zip);
        });
        assertEquals(msg, ex.getMessage());
    }
}
