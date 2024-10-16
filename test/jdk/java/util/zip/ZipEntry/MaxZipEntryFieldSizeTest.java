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
 * @bug 8340553
 * @summary Verify that ZipEntry(String), ZipEntry::setComment, and
 * ZipEntry::setExtra throws a IllegalArgumentException when the
 * length of the field exceeds 65,489 bytes
 * @run junit MaxZipEntryFieldSizeTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class MaxZipEntryFieldSizeTest {

    // CEN header size + name length + comment length + extra length
    // should not exceed 65,535 bytes per the PKWare APP.NOTE
    // 4.4.10, 4.4.11, & 4.4.12.
    static final int MAX_COMBINED_CEN_HEADER_SIZE = 0xFFFF;
    // Maximum possible size of name length + comment length + extra length
    // for entries in order to not exceed 65,489 bytes
    static final int MAX_NAME_COMMENT_EXTRA_SIZE =
            MAX_COMBINED_CEN_HEADER_SIZE - ZipFile.CENHDR;
    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;
    // ZIP file to be used by the tests
    static final Path ZIP_FILE = Path.of("ZipEntryFieldSize.zip");
    // Zip Entry name used by tests
    static final String ENTRY_NAME = "EntryName";
    // Max length minus the size of the ENTRY_NAME or ENTRY_COMMENT
    static final int MAX_FIElD_LEN_MINUS_ENTRY_NAME =
            MAX_NAME_COMMENT_EXTRA_SIZE - 9;

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
     * Validate an IllegalArgumentException is thrown when the comment
     * length exceeds 65,489 bytes.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE + 1,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME - 1})
    void setCommentLengthTest(int length) {
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        boolean expectException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        String comment = new String(bytes, StandardCharsets.UTF_8);
        System.out.printf("Comment Len= %s, exception: %s%n", comment.length(), expectException);
        // The comment length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () ->
                    zipEntry.setComment(comment));
        }
    }

    /**
     * Validate an IllegalArgumentException is thrown when the name
     * length exceeds 65,489 bytes.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE + 1,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME - 1})
    void setNameLengthTest(int length) {
        boolean expectException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        String name = new String(bytes, StandardCharsets.UTF_8);
        System.out.printf("name Len= %s, exception: %s%n", name.length(), expectException);
        // The name length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () -> new ZipEntry(name));
        }
    }

    /**
     * Validate an IllegalArgumentException is thrown when the extra data
     * length exceeds 65,489 bytes.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE + 1,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME,
            MAX_FIElD_LEN_MINUS_ENTRY_NAME - 1})
    void setExtraLengthTest(int length) {
        final byte[] bytes = new byte[length];
        boolean expectException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);
        // Size of the actual (empty) data
        buffer.putShort((short) (length - 2 * Short.BYTES));
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        System.out.printf("extra Len= %s, exception: %s%n", bytes.length, expectException);
        // The extra data length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () -> zipEntry.setExtra(bytes));
        }
    }
}
