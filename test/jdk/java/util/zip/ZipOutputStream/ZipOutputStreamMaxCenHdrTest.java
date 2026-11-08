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
 * @summary Verify that ZipOutputStream throws a ZipException when the
 * CEN header size + name length + comment length + extra length exceeds
 * 65,535 bytes
 * @run junit ZipOutputStreamMaxCenHdrTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ZipOutputStreamMaxCenHdrTest {

    // CEN header size + name length + comment length + extra length
    // should not exceed 65,535 bytes per the PKWare APP.NOTE
    // 4.4.10, 4.4.11, & 4.4.12.
    static final int MAX_COMBINED_CEN_HEADER_SIZE = 0xFFFF;

    // Maximum possible size of name length + comment length + extra length
    // for entries in order to not exceed 65,489 bytes minus 46 bytes for the CEN
    // header length
    static final int MAX_NAME_COMMENT_EXTRA_SIZE = MAX_COMBINED_CEN_HEADER_SIZE - ZipFile.CENHDR;

    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;
    // ZIP file to be used by the tests
    static final Path ZIP_FILE = Path.of("maxCENHdrTest.zip");
    // Zip Entry name used by tests
    static final String ENTRY_NAME = "EntryName";

    // Max length minus the size of the ENTRY_NAME
    static final int MAX_FIELD_LEN_MINUS_ENTRY_NAME = MAX_NAME_COMMENT_EXTRA_SIZE - ENTRY_NAME.length();
    // CEN Extra field info-zip extended timestamp MTIME size
    static int  MTIME_EXTRA_SIZE = 9;

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
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @MethodSource("CEN_NAME_LEN")
    void setNameExtraSizeTest(int length, boolean setLastModifiedTime) {

        long len = setLastModifiedTime ? length + MTIME_EXTRA_SIZE + ZipEntry.CENHDR : length ;
       System.out.printf("setLastModifiedTime: %b, length: %d%n", setLastModifiedTime, len);
        boolean expectException = len > MAX_COMBINED_CEN_HEADER_SIZE;

        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        String name = new String(bytes, StandardCharsets.UTF_8);
        System.out.printf("name len= %s%n", length);
        ZipEntry zipEntry = new ZipEntry(name);
        if (setLastModifiedTime) {
            zipEntry.setLastAccessTime(FileTime.fromMillis(0));
        }
        System.out.printf("name + comment Len= %s, exception: %s%n", len, expectException);
        assertEquals(writeZipEntry(zipEntry), expectException);
    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @MethodSource("CEN_NO_NAME_LEN")
    void setCommentExtraSizeTest(int length, boolean setLastModifiedTime) {

        long len = setLastModifiedTime ?
                length + MTIME_EXTRA_SIZE + ZipEntry.CENHDR  + ENTRY_NAME.length() : length ;
        System.out.printf("setLastModifiedTime: %b, length: %d%n", setLastModifiedTime, len);
        boolean expectException = len > MAX_COMBINED_CEN_HEADER_SIZE;
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        String comment = new String(bytes, StandardCharsets.UTF_8);
        System.out.printf("Comment Len= %s, exception: %s%n", comment.length(), expectException);
        zipEntry.setComment(new String(bytes, StandardCharsets.UTF_8));
        if (setLastModifiedTime) {
            zipEntry.setLastAccessTime(FileTime.fromMillis(0));
        }
        System.out.printf("name + comment Len= %s, exception: %s%n", len, expectException);
        assertEquals(writeZipEntry(zipEntry), expectException);

    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     */
    @ParameterizedTest
    @MethodSource("CEN_NO_NAME_LEN")
    void setExtraExtraSizeTest(int length, boolean setLastModifiedTime) {

        long len = setLastModifiedTime ?
                length + MTIME_EXTRA_SIZE + ZipEntry.CENHDR  + ENTRY_NAME.length() : length ;
        System.out.printf("setLastModifiedTime: %b, length: %d%n", setLastModifiedTime, len);
        boolean expectException = len > MAX_COMBINED_CEN_HEADER_SIZE;

        final byte[] bytes = new byte[length];
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);
        // Size of the actual (empty) data
        buffer.putShort((short) (length - 2 * Short.BYTES));
        Arrays.fill(bytes, (byte) 'a');
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        System.out.printf("extra Len= %s, exception: %s%n", bytes.length, expectException);
        zipEntry.setExtra(bytes);
        if (setLastModifiedTime) {
            zipEntry.setLastAccessTime(FileTime.fromMillis(0));
        }
        System.out.printf("name + comment Len= %s, exception: %s%n", len, expectException);
        assertEquals(writeZipEntry(zipEntry), expectException);
    }

    private static Stream<Arguments> CEN_NO_NAME_LEN() {
        return Stream.of(
                Arguments.of(MAX_FIELD_LEN_MINUS_ENTRY_NAME, false),
                Arguments.of(MAX_FIELD_LEN_MINUS_ENTRY_NAME, true)
        );
    }

    private static Stream<Arguments> CEN_NAME_LEN() {
        return Stream.of(
                Arguments.of(MAX_NAME_COMMENT_EXTRA_SIZE, false),
                Arguments.of(MAX_NAME_COMMENT_EXTRA_SIZE, true)
        );
    }

    /**
     * Write a single Zip entry using ZipOutputStream
     * @param zipEntry the ZipEntry to write
     * @return true if a ZipException was thrown
     */
    private static boolean writeZipEntry(ZipEntry zipEntry) {
        boolean receivedException = false;
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(ZIP_FILE)))) {
            zos.putNextEntry(zipEntry);
        } catch (Exception e) {
            assertTrue(e.getMessage().matches(".*bad header size.*"), "Unexpected ZipException message: " + e.getMessage());
            receivedException = true;
            System.out.printf("writeZipEntry: exception: %s%n", e.getMessage());
        }
        return receivedException;
    }
}
