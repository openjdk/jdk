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
 * combined length of the fields, including the size of the CEN Header,
 * exceeds 65,535 bytes
 * @run junit MaxZipEntryFieldSizeTest
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
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
    // Zip Entry name used by tests
    static final String ENTRY_NAME = "EntryName";
    // Max length minus the size of the ENTRY_NAME or ENTRY_COMMENT
    static final int MAX_FIELD_LEN_MINUS_ENTRY_NAME =
            MAX_NAME_COMMENT_EXTRA_SIZE - 9;

    /**
     * Validate an IllegalArgumentException is thrown when the
     * combined length of the entry name, entry comment, entry extra data,
     * and CEN Header size exceeds 65,535 bytes.
     */
    @ParameterizedTest
    @ValueSource(ints = {30000, 35000})
    void combinedLengthTest(int length) {
        String comment = "a".repeat(length);
        byte[] bytes = creatExtraData(length);
        int combinedLength = ENTRY_NAME.length() + comment.length() + bytes.length;
        boolean expectException = combinedLength > MAX_COMBINED_CEN_HEADER_SIZE;
        System.out.printf("Combined Len= %s, exception: %s%n", combinedLength, expectException);
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        zipEntry.setComment(comment);
        // The extra data length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () ->
                    zipEntry.setExtra(bytes));
        } else {
            zipEntry.setExtra(bytes);
        }
    }

    /**
     * Validate an IllegalArgumentException is thrown when the comment
     * length exceeds 65,489 bytes.
     */
    @ParameterizedTest
    @ValueSource(ints = {MAX_COMBINED_CEN_HEADER_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE,
            MAX_NAME_COMMENT_EXTRA_SIZE + 1,
            MAX_FIELD_LEN_MINUS_ENTRY_NAME,
            MAX_FIELD_LEN_MINUS_ENTRY_NAME - 1})
    void setCommentLengthTest(int length) {
        boolean expectException = length >= MAX_NAME_COMMENT_EXTRA_SIZE;
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        String comment = "a".repeat(length);
        System.out.printf("Comment Len= %s, exception: %s%n", comment.length(), expectException);
        // The comment length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () ->
                    zipEntry.setComment(comment));
        } else {
            zipEntry.setComment(comment);
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
            MAX_FIELD_LEN_MINUS_ENTRY_NAME,
            MAX_FIELD_LEN_MINUS_ENTRY_NAME - 1})
    void nameLengthTest(int length) {
        boolean expectException = length > MAX_NAME_COMMENT_EXTRA_SIZE;
        String name = "a".repeat(length);
        System.out.printf("name Len= %s, exception: %s%n", name.length(), expectException);
        // The name length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () -> new ZipEntry(name));
        } else {
            new ZipEntry(name);
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
            MAX_FIELD_LEN_MINUS_ENTRY_NAME,
            MAX_FIELD_LEN_MINUS_ENTRY_NAME - 1})
    void setExtraLengthTest(int length) {
        boolean expectException = length >= MAX_NAME_COMMENT_EXTRA_SIZE;
        byte[] bytes = creatExtraData(length);
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        System.out.printf("extra Len= %s, exception: %s%n", bytes.length, expectException);
        // The extra data length will trigger the IllegalArgumentException
        if (expectException) {
            assertThrows(IllegalArgumentException.class, () -> zipEntry.setExtra(bytes));
        } else {
            zipEntry.setExtra(bytes);
        }
    }

    /**
     * Create the extra field data which will be passed to ZipEntry::setExtra
     * @param length size of the extra data
     * @return byte array containing the extra data
     */
    private static byte[] creatExtraData(int length) {
        byte[] bytes = new byte[length];
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);
        // Size of the actual (empty) data
        buffer.putShort((short) (length - 2 * Short.BYTES));
        return bytes;
    }
}
