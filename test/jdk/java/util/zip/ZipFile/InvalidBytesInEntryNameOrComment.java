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
 *
 */
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Formatter;
import java.util.stream.Stream;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8301873 8321156
 * @summary Validate that a ZipException is thrown when a ZIP file with
 * invalid UTF-8 byte sequences in the name or comment fields is opened via
 * ZipFile or traversed via ZipInputStream.
 * Also validate that ZipFile::getComment will return null with invalid UTF-8
 * byte sequences in the ZIP file comment
 * @run junit InvalidBytesInEntryNameOrComment
 */
public class InvalidBytesInEntryNameOrComment {
    // Zip file that is created and used by the test
    public static final Path ZIP_FILE = Path.of("BadName.zip");
    // Example invalid UTF-8 byte sequence
    private static final byte[] INVALID_UTF8_BYTE_SEQUENCE = {(byte) 0xF0, (byte) 0xA4, (byte) 0xAD};
    // Expected error message when an invalid entry name or entry comment is
    // encountered when accessing a CEN Header
    private static final String CEN_BAD_ENTRY_NAME_OR_COMMENT = "invalid CEN header (bad entry name or comment)";

    // Expected error message when an invalid entry name is encountered when
    // accessing a LOC Header
    private static final String LOC_HEADER_BAD_ENTRY_NAME = "invalid LOC header (bad entry name)";
    // Zip file comment starting offset
    private static final int ZIP_FILE_COMMENT_OFFSET = 0x93;
    // CEN Header offset for the entry comment to be modified
    private static final int CEN_FILE_HEADER_FILE_COMMENT_STARTING_OFFSET = 0x6D;
    // CEN Header offset for the entry name to be modified
    private static final int CEN_FILE_HEADER_FILENAME_STARTING_OFFSET = 0x66;
    // LOC Header offset for the entry name to be modified
    private static final int LOC_FILE_HEADER_FILENAME_STARTING_OFFSET = 0x1e;
    // CEN Entry comment
    public static final String ENTRY_COMMENT = "entryComment";
    // Entry name to be modified/validated
    public static final String ENTRY_NAME = "entryName";
    // Zip file comment to be modified/validated
    public static final String ZIP_FILE_COMMENT = "ZipFileComment";
    // Buffer used to massage the byte array containing the Zip File
    private ByteBuffer buffer;
    // Array used to copy VALID_ZIP into prior to each test run
    private byte[] zipArray;

    /**
     * Byte array representing a valid Zip file prior modifying the CEN/LOC
     * entry name, CEN entry comment or Zip file comment with an invalid
     * UTF-8 byte sequence.
     * See the createZipByteArray method which was used to create the original
     * Zip file
     * ----------------#1--------------------
     * [Central Directory Header]
     * 0x3a: Signature        : 0x02014b50
     * 0x3e: Created Zip Spec :       0x14 [2.0]
     * 0x3f: Created OS       :        0x0 [MS-DOS]
     * 0x40: VerMadeby        :       0x14 [0, 2.0]
     * 0x41: VerExtract       :       0x14 [2.0]
     * 0x42: Flag             :      0x808
     * 0x44: Method           :        0x8 [DEFLATED]
     * 0x46: Last Mod Time    : 0x58506664 [Fri Feb 16 12:51:08 EST 2024]
     * 0x4a: CRC              : 0xd202ef8d
     * 0x4e: Compressed Size  :        0x3
     * 0x52: Uncompressed Size:        0x1
     * 0x56: Name Length      :        0x9
     * 0x58: Extra Length     :        0x0
     * 0x5a: Comment Length   :        0xc
     * 0x5c: Disk Start       :        0x0
     * 0x5e: Attrs            :        0x0
     * 0x60: AttrsEx          :        0x0
     * 0x64: Loc Header Offset:        0x0
     * 0x68: File Name        :  entryName
     * 0x71: Comment          : [entryComment]
     * [Local File Header]
     * 0x0: Signature   :   0x04034b50
     * 0x4: Version     :         0x14    [2.0]
     * 0x6: Flag        :        0x808
     * 0x8: Method      :          0x8    [DEFLATED]
     * 0xa: LastMTime   :   0x58506664    [Fri Feb 16 12:51:08 EST 2024]
     * 0xe: CRC         :          0x0
     * 0x12: CSize       :          0x0
     * 0x16: Size        :          0x0
     * 0x1a: Name Length :          0x9    [entryName]
     * 0x1c: ExtraLength :          0x0
     * 0x1e: File Name   : [entryName]
     * [End Central Directory Header]
     * 0x7d: Signature   :   0x06054b50
     * 0x85: Disk Entries:          0x1
     * 0x87: Total Entries:         0x1
     * 0x89: CEN Size    :         0x43
     * 0x8d: Offset CEN  :         0x3a
     * 0x91: Comment Len :          0xe
     * 0x93: Comment     :   [ZipFileComment]
     */
    public static byte[] VALID_ZIP = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x7d, (byte) 0x6f, (byte) 0x50, (byte) 0x58, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x65, (byte) 0x6e,
            (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x4e, (byte) 0x61, (byte) 0x6d, (byte) 0x65, (byte) 0x63,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8, (byte) 0x8d, (byte) 0xef,
            (byte) 0x2, (byte) 0xd2, (byte) 0x3, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x7d, (byte) 0x6f,
            (byte) 0x50, (byte) 0x58, (byte) 0x8d, (byte) 0xef, (byte) 0x2, (byte) 0xd2, (byte) 0x3, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x65, (byte) 0x6e, (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x4e, (byte) 0x61, (byte) 0x6d,
            (byte) 0x65, (byte) 0x65, (byte) 0x6e, (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x43, (byte) 0x6f,
            (byte) 0x6d, (byte) 0x6d, (byte) 0x65, (byte) 0x6e, (byte) 0x74, (byte) 0x50, (byte) 0x4b, (byte) 0x5,
            (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x1,
            (byte) 0x0, (byte) 0x43, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x3a, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0xe, (byte) 0x0, (byte) 0x5a, (byte) 0x69, (byte) 0x70, (byte) 0x46, (byte) 0x69,
            (byte) 0x6c, (byte) 0x65, (byte) 0x43, (byte) 0x6f, (byte) 0x6d, (byte) 0x6d, (byte) 0x65, (byte) 0x6e,
            (byte) 0x74,
    };

    /**
     * Delete the Zip file if it exists prior to each run and create a copy
     * of the byte array representing a valid ZIP file to be used by each test run
     *
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void setupTest() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
        zipArray = Arrays.copyOf(VALID_ZIP, VALID_ZIP.length);
        buffer = ByteBuffer.wrap(zipArray).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * The MethodSource of CEN offsets to modify with an invalid UTF-8 byte
     * sequence
     *
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> CENCommentOffsets() {
        return Stream.of(
                // Entry's name starting offset
                Arguments.of(CEN_FILE_HEADER_FILENAME_STARTING_OFFSET),
                // Entry's comment starting offset
                Arguments.of(CEN_FILE_HEADER_FILE_COMMENT_STARTING_OFFSET)
        );
    }

    /**
     * Validate that the original Zip file can be opened via ZipFile.
     * @throws IOException if an error occurs
     */
    @Test
    public void testValidEntryNameAndComment() throws IOException {
        Files.write(ZIP_FILE, zipArray);
        try (ZipFile zf = new ZipFile(ZIP_FILE.toFile())) {
            var comment = zf.getComment();
            assertEquals(ZIP_FILE_COMMENT, comment);
        }
    }

    /**
     * Validate that the original Zip file can be opened and traversed via
     * ZipinputStream::getNextEntry.
     * @throws IOException if an error occurs
     */
    @Test
    public void traverseZipWithZipInputStreamTest() throws IOException {
        Files.write(ZIP_FILE, zipArray);
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(ZIP_FILE.toFile()))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                assertEquals(ENTRY_NAME, ze.getName());
            }
        }
    }

    /**
     * Validate that a ZipException is thrown when an entry name or entry comment
     * within a CEN file header contains an invalid UTF-8 byte sequence.
     *
     * @param offset the offset to the file name or file comment within the CEN
     *               file header
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("CENCommentOffsets")
    public void testInValidEntryNameOrComment(int offset)
            throws IOException {
        createInvalidUTFEntryInZipFile(offset);
        Throwable ex = assertThrows(ZipException.class, () ->
                {
                    try (ZipFile zf = new ZipFile(ZIP_FILE.toFile())) {};
                }
        );
        assertEquals(CEN_BAD_ENTRY_NAME_OR_COMMENT, ex.getMessage());
    }
    /**
     * Validate that a null is returned from ZipFile::getComment when the
     * comment contains an invalid UTF-8 byte sequence.
     * @throws IOException if an error occurs
     */
    @Test
    public void testInValidZipFileComment() throws IOException {
        createInvalidUTFEntryInZipFile(ZIP_FILE_COMMENT_OFFSET);
        try (ZipFile zf = new ZipFile(ZIP_FILE.toFile())) {
            assertNull(zf.getComment());
        }
    }

    /**
     * Validate that a ZipException is thrown when an entry name
     * within a LOC file header contains an invalid UTF-8 byte sequence.
     * @throws IOException if an error occurs
     */
    @Test
    public void invalidZipInputStreamTest() throws IOException {
        createInvalidUTFEntryInZipFile(LOC_FILE_HEADER_FILENAME_STARTING_OFFSET);
        Throwable ex = assertThrows(ZipException.class, () ->
                {
                    try (ZipInputStream zis =
                                 new ZipInputStream(new FileInputStream(ZIP_FILE.toFile()))) {
                        zis.getNextEntry();
                    };
                });
        assertEquals(LOC_HEADER_BAD_ENTRY_NAME, ex.getMessage());
    }

    /**
     * Utility method which modifies a Zip file starting at the specified
     * offset to include an invalid UTF-8 byte sequence.
     *
     * @param offset starting offset within the Zip file to modify
     * @throws IOException if an error occurs
     */
    private void createInvalidUTFEntryInZipFile(int offset) throws IOException {
        buffer.put(offset, INVALID_UTF8_BYTE_SEQUENCE, 0,
                INVALID_UTF8_BYTE_SEQUENCE.length);
        Files.write(ZIP_FILE, zipArray);
    }

    /**
     * Utility method which creates the Zip file used by the tests and
     * converts Zip file to byte array declaration.
     *
     * @throws IOException if an error occurs
     */
    private void createZipByteArray() throws IOException {
        ZipOutputStream zos = new ZipOutputStream(
                new FileOutputStream(ZIP_FILE.toFile()));
        zos.setComment(ZIP_FILE_COMMENT);
        ZipEntry entry = new ZipEntry(ENTRY_NAME);
        entry.setComment(ENTRY_COMMENT);
        zos.putNextEntry(entry);
        zos.write(new byte[1]);
        zos.closeEntry();
        zos.close();
        // Now create the byte array entry declaration
        var fooJar = Files.readAllBytes(ZIP_FILE);
        var result = createByteArray(fooJar, "VALID_ZIP");
        System.out.println(result);
    }

    /**
     * Utility method which takes a byte array and converts to byte array
     * declaration.  For example:
     * {@snippet :
     * var fooJar = Files.readAllBytes(Path.of("foo.jar"));
     * var result = createByteArray(fooJar,"FOOBYTES");
     * System.out.println(result);
     * }
     *
     * @param bytes A byte array used to create a byte array declaration
     * @param name  Name to be used in the byte array declaration
     * @return The formatted byte array declaration
     */
    public static String createByteArray(byte[] bytes, String name) {
        StringBuilder sb = new StringBuilder(bytes.length * 5);
        Formatter fmt = new Formatter(sb);
        fmt.format("    public static byte[] %s = {", name);
        final int linelen = 8;
        for (int i = 0; i < bytes.length; i++) {
            if (i % linelen == 0) {
                fmt.format("%n        ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");
        return sb.toString();
    }
}
