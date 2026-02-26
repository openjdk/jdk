/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Verify that ZipFile::size reports the actual number of entries
 *          even with an incorrect ENDTOT field
 * @run junit IncorrectEndTot
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Collections;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IncorrectEndTot {

    // File to use for this test
    File file = new File("incorrect-end-centot.zip");

    // Return scenarios for correct and incorrect ENDTOT fields
    public static Stream<Arguments> scenarios() {
        return Stream.of(
                Arguments.of(10, 10), // CEN agrees with ENDTOT
                Arguments.of(10, 11), // CEN has one less than ENDTOT
                Arguments.of(11, 10), // CEN has one more than ENDTOT
                Arguments.of(0, 0),   // Empty ZIP, correct ENDTOT
                Arguments.of(0, 10)   // Empty ZIP, incorrect ENDTOT
        );
    }

    /**
     * Delete the file used by this test
     */
    @AfterEach
    public void cleanup() {
        file.delete();
    }

    /**
     * Verify that ZipFile::size reports the actual number of CEN records,
     * regardless of the ENDTOT field.
     *
     * @param actual number of entries in the ZIP file
     * @param reported number reported in ENDTOT
     * @throws IOException if an unexpected error occurs
     */
    @ParameterizedTest
    @MethodSource("scenarios")
    public void shouldCountActualEntries(int actual, int reported) throws IOException {
        createZip(file, actual, reported);
        try (ZipFile zf = new ZipFile(file)) {
            assertEquals(actual, zf.size());
            assertEquals(actual, Collections.list(zf.entries()).size());
            assertEquals(actual, zf.stream().count());
        }
    }

    /**
     * Create a ZIP file with a number of entries, possibly reporting an incorrect number in
     * the ENDTOT field
     * @param file the file to write to
     * @param numEntries the number of entries to generate
     * @param reported the number of entries to report in the END header's ENDTOT field
     * @throws IOException
     */
    private static void createZip(File file, int numEntries, int reported) throws IOException {
        // Create a ZIP
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {
            for (int i = 0; i < numEntries; i++) {
                zo.putNextEntry(new ZipEntry("entry_" + i));
            }
        }
        byte[] bytes = out.toByteArray();

        // Update the ENDTOT field to report a possibly incorrect number
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort(bytes.length - ZipFile.ENDHDR + ZipFile.ENDTOT, (short) reported);

        // Write to disk
        Files.write(file.toPath(), bytes);
    }
}
