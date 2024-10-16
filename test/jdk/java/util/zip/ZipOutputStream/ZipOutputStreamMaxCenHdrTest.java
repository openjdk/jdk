/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8336025
 * @summary Verify that ZipOutputStream throws a ZipException when the
 * CEN header size + name length + comment length + extra length exceeds
 * 65,535 bytes
 * @run junit ZipOutputStreamMaxCenHdrTest
 */
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

public class ZipOutputStreamMaxCenHdrTest {

    // CEN header size + name length + comment length + extra length
    // should not exceed 65,535 bytes per the PKWare APP.NOTE
    // 4.4.10, 4.4.11, & 4.4.12.
    static final int MAX_COMBINED_CEN_HEADER_SIZE = 0xFFFF;

    // Maximum possible size of name length + comment length + extra length
    // for entries in order to not exceed 65,489 bytes minus 46 bytes for the CEN
    // header length
    static final int MAX_NAME_COMMENT_EXTRA_SIZE =
            MAX_COMBINED_CEN_HEADER_SIZE - ZipFile.CENHDR;

    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;

    // ZIP file to be used by the tests
    static final Path ZIP_FILE = Path.of("maxCENHdrTest.zip");

    /**
     * Clean up prior to test run
     *
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void startUp() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
    }

    /**
     * Validate a ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_COMBINED_CEN_HEADER_SIZE - 1,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE - 1})
    void setCommentTest(int length) throws IOException {
        boolean expectZipException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        ZipEntry zipEntry = new ZipEntry("");
        // The comment length will trigger the ZipException
        zipEntry.setComment(new String(bytes, StandardCharsets.UTF_8));
        boolean receivedException = writeZipEntry(zipEntry, expectZipException);
        assertEquals(receivedException, expectZipException);
    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_COMBINED_CEN_HEADER_SIZE - 1,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE - 1})
    void setNameTest(int length) throws IOException {
        boolean expectZipException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        // The name length will trigger the ZipException
        ZipEntry zipEntry = new ZipEntry(new String(bytes, StandardCharsets.UTF_8));
        boolean receivedException = writeZipEntry(zipEntry, expectZipException);
        assertEquals(receivedException, expectZipException);
    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_COMBINED_CEN_HEADER_SIZE - 1,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE - 1})
    void setExtraTest(int length) throws IOException {
        boolean expectZipException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        final byte[] bytes = new byte[length];
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);
        // Size of the actual (empty) data
        buffer.putShort((short) (length - 2 * Short.BYTES));
        ZipEntry zipEntry = new ZipEntry("");
        // The extra data length will trigger the ZipException
        zipEntry.setExtra(bytes);
        boolean receivedException = writeZipEntry(zipEntry, expectZipException);
        assertEquals(receivedException, expectZipException);
    }

    /**
     * Write a single Zip entry using ZipOutputStream
     * @param zipEntry the ZipEntry to write
     * @param expectZipException true if a ZipException is expected, false otherwse
     * @return true if a ZipException was thrown
     * @throws IOException if an error occurs
     */
    private static boolean writeZipEntry(ZipEntry zipEntry, boolean expectZipException)
            throws IOException {
        boolean receivedException = false;
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(ZIP_FILE)))) {
            zos.putNextEntry(zipEntry);
            if (expectZipException) {
                ZipException ex = assertThrows(ZipException.class, zos::close);
                assertTrue(ex.getMessage().matches(".*bad header size.*"),
                        "Unexpected ZipException message: " + ex.getMessage());
                receivedException = true;
            }
        } catch (Exception e) {
            throw new RuntimeException("Received Unexpected Exception", e);
        }
        return receivedException;
    }
}
