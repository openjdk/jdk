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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/* @test
 * @bug 8314891
 * @summary Validate that a ZipException is thrown when the extra len is 0
 * and the CEN size, csize,LOC offset fields are set to 0xFFFFFFFF, the disk
 * starting number is set to 0xFFFF or when we have a valid Zip64 Extra header
 * size but missing the corresponding field.
 * @run junit MissingZIP64EntriesTest
 */
public class MissingZIP64EntriesTest {

    /*
     * Byte array holding a ZIP file which contains a
     * Zip64 Extra Header with only the size field.
     *
     *  ----------------#1--------------------
     *  [Central Directory Header]
     *    0x4d: Signature        : 0x02014b50
     *    0x51: Created Zip Spec :       0x2d [4.5]
     *    0x52: Created OS       :        0x0 [MS-DOS]
     *    0x53: VerMadeby        :       0x2d [0, 4.5]
     *    0x54: VerExtract       :       0x2d [4.5]
     *    0x55: Flag             :      0x808
     *    0x57: Method           :        0x8 [DEFLATED]
     *    0x59: Last Mod Time    : 0x57116922 [Thu Aug 17 13:09:04 EDT 2023]
     *    0x5d: CRC              : 0x57de98d2
     *    0x61: Compressed Size  :       0x16
     *    0x65: Uncompressed Size: 0xffffffff
     *    0x69: Name Length      :        0x9
     *    0x6b: Extra Length     :        0xc
	 *        Extra data:[01, 00, 08, 00, 14, 00, 00, 00, 00, 00, 00, 00]
	 *           [tag=0x0001, sz=8]
     *               ->ZIP64: size *0x14 *0x6054b50
     *           [data= 14 00 00 00 00 00 00 00 ]
     *    0x6d: Comment Length   :        0x0
     *    0x6f: Disk Start       :        0x0
     *    0x71: Attrs            :        0x0
     *    0x73: AttrsEx          :        0x0
     *    0x77: Loc Header Offset:        0x0
     *    0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_SINGLE_ZIP64_HEADER_ENTRY_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x22, (byte) 0x69, (byte) 0x11, (byte) 0x57, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x8, (byte) 0x49, (byte) 0xcd,
            (byte) 0xcb, (byte) 0xcb, (byte) 0x2c, (byte) 0x56, (byte) 0x8, (byte) 0xc8, (byte) 0x49, (byte) 0xac,
            (byte) 0x4c, (byte) 0x2d, (byte) 0x2a, (byte) 0x6, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xd2, (byte) 0x98, (byte) 0xde, (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x22, (byte) 0x69, (byte) 0x11, (byte) 0x57, (byte) 0xd2, (byte) 0x98, (byte) 0xde,
            (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0x9, (byte) 0x0, (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x8, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x43, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a Zip file with no extra header fields
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x2f: Signature        : 0x02014b50
     *       0x33: Created Zip Spec :       0x14 [2.0]
     *       0x34: Created OS       :        0x3 [UNIX]
     *       0x35: VerMadeby        :      0x314 [3, 2.0]
     *       0x36: VerExtract       :       0x14 [2.0]
     *       0x37: Flag             :        0x2
     *       0x39: Method           :        0x8 [DEFLATED]
     *       0x3b: Last Mod Time    : 0x57039c0d [Thu Aug 03 19:32:26 EDT 2023]
     *       0x3f: CRC              : 0x31963516
     *       0x43: Compressed Size  :        0x8
     *       0x47: Uncompressed Size:        0x6
     *       0x4b: Name Length      :        0x9
     *       0x4d: Extra Length     :        0x0
     *       0x4f: Comment Length   :        0x0
     *       0x51: Disk Start       :        0x0
     *       0x53: Attrs            :        0x1
     *       0x55: AttrsEx          : 0x81a40000
     *       0x59: Loc Header Offset:        0x0
     *       0x5d: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x2, (byte) 0x0,
            (byte) 0x8, (byte) 0x0, (byte) 0xd, (byte) 0x9c, (byte) 0x3, (byte) 0x57, (byte) 0x16, (byte) 0x35,
            (byte) 0x96, (byte) 0x31, (byte) 0x8, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0xe7, (byte) 0x2, (byte) 0x0, (byte) 0x50,
            (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x3, (byte) 0x14, (byte) 0x0, (byte) 0x2,
            (byte) 0x0, (byte) 0x8, (byte) 0x0, (byte) 0xd, (byte) 0x9c, (byte) 0x3, (byte) 0x57, (byte) 0x16,
            (byte) 0x35, (byte) 0x96, (byte) 0x31, (byte) 0x8, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xa4,
            (byte) 0x81, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c,
            (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x50, (byte) 0x4b,
            (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x37, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2f, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Enable debug output
     */
    private static final boolean DEBUG = false;

    /**
     * Name of the Zip file that we create from the byte array
     */
    public static final String ZIPFILE_NAME = "validZipFile.zip";

    /**
     * Name of the Zip file that we modify/corrupt
     */
    public static final String BAD_ZIP_NAME = "zipWithInvalidZip64ExtraField.zip";
    /**
     * Zip file entry that will be accessed by some the tests
     */
    private static final String ZIP_FILE_ENTRY_NAME = "Hello.txt";

    /**
     * Expected Error messages
     */
     private static final String MISSING_ZIP64_COMPRESSED_SIZE =
            "Invalid Zip64 extra block, missing compressed size";
     private static final String MISSING_ZIP64_LOC_OFFSET =
             "Invalid Zip64 extra block, missing LOC offset value";
     private static final String INVALID_EXTRA_LENGTH =
     "Invalid CEN header (invalid zip64 extra len size)";

    /**
     * Disk starting number offset for the Zip file created from the
     * ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY array
     */
    private static final int DISKNO_OFFSET_ZIP_NO_EXTRA_LEN = 0x51;

    /**
     * Value to set the size, csize, or LOC offset CEN fields to when their
     * actual value is stored in the Zip64 Extra Header
     */
    private static final long ZIP64_MAGICVAL = 0xFFFFFFFFL;

    /**
     * Value to set the Disk Start number offset CEN field to when the
     * actual value is stored in the Zip64 Extra Header
     */
    private static final int  ZIP64_MAGICCOUNT = 0xFFFF;

    /**
     * Copy of the byte array for the ZIP to be modified by a given test run
     */
    private byte[] zipArrayCopy;

    /**
     * Little-endian ByteBuffer for manipulating the ZIP copy
     */
    private ByteBuffer buffer;

    /**
     * The DataProvider of offsets to set to 0xFFFFFFFF and the expected
     * ZipException error message when there are missing Zip64 Extra header fields
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> InvalidZip64MagicValues() {
        return Stream.of(
                // Compressed Size offset and expected ZipException Message
                Arguments.of(0x61, MISSING_ZIP64_COMPRESSED_SIZE),
                // LOC offset and expected ZipException Message
                Arguments.of(0x77, MISSING_ZIP64_LOC_OFFSET)
        );
    }

    /**
     * The DataProvider of offsets to set to 0xFFFFFFFF or 0xFFFF when the Extra Length
     * size is 0 for the Zip file created using ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> MissingZip64ExtraFieldEntries() {
        return Stream.of(
                // Compressed size offset
                Arguments.of(0x43),
                // Size offset
                Arguments.of(0x47),
                // Disk start number offset
                Arguments.of(DISKNO_OFFSET_ZIP_NO_EXTRA_LEN),
                // LOC offset
                Arguments.of(0x59)
        );
    }

    /**
     * The DataProvider which will return a byte array representing a
     * valid Zip file and the expected content for the Zip file entry 'Hello.txt'.
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> validZipFiles() {
        return Stream.of(
                Arguments.of(ZIP_WITH_SINGLE_ZIP64_HEADER_ENTRY_BYTEARRAY,
                        "Hello Tennis Players"),
                Arguments.of(ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY,
                        "Hello\n")
        );
    }

    /**
     * Initial test setup
     * @throws IOException if an error occurs
     */
    @BeforeAll
    public static void setup() throws IOException {
        Files.deleteIfExists(Path.of(ZIPFILE_NAME));
        Files.deleteIfExists(Path.of(BAD_ZIP_NAME));
    }

    /**
     * Delete the Zip file that will be modified by each test
     * @throws IOException if an error occurs
     */
    @BeforeEach
    public void beforeEachTestRun() throws IOException {
        Files.deleteIfExists(Path.of(ZIPFILE_NAME));
        Files.deleteIfExists(Path.of(BAD_ZIP_NAME));
    }

    /**
     * Verify that a ZipException is thrown by ZipFile if the Zip64 header
     * does not contain the required field
     * @param offset Offset of the CEN Header field to set to 0xFFFFFFFF
     * @param errorMessage Expected ZipException error message
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("InvalidZip64MagicValues")
    public void invalidZip64ExtraHeaderZipFileTest(int offset, String errorMessage)
            throws IOException {
        // Set the CEN csize or LOC offset field to 0xFFFFFFFF.  There will not
        // be the expected Zip64 Extra Header field resulting in a ZipException
        // being thrown
        zipArrayCopy = ZIP_WITH_SINGLE_ZIP64_HEADER_ENTRY_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(BAD_ZIP_NAME)) {
                ZipEntry ze = zf.getEntry(ZIP_FILE_ENTRY_NAME);
                try (InputStream is = zf.getInputStream(ze)) {
                    String result = new String(is.readAllBytes());
                    if (DEBUG) {
                        var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                        System.out.printf("Error: Zip File read :%s%n[%s]%n", result,
                                hx.formatHex(result.getBytes()));
                    }
                }
            }
        });
        assertTrue(ex.getMessage().matches(errorMessage),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that a ZipException is thrown by Zip FS if the Zip64 header
     * does not contain the required field
     * @param offset Offset of the CEN Header field to set to 0xFFFFFFFF
     * @param errorMessage Expected ZipException error message
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("InvalidZip64MagicValues")
    public void invalidZip64ExtraHeaderZipFSTest(int offset, String errorMessage) throws IOException {
        // Set the CEN csize or LOC offset field to 0xFFFFFFFF.  There will not
        // be the expected Zip64 Extra Header field resulting in a ZipException
        // being thrown
        zipArrayCopy = ZIP_WITH_SINGLE_ZIP64_HEADER_ENTRY_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int)ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            try (FileSystem fs = FileSystems.newFileSystem(
                    Path.of(BAD_ZIP_NAME), Map.of())) {
                Path p = fs.getPath(ZIP_FILE_ENTRY_NAME);
                String result = new String(Files.readAllBytes(p));
                if (DEBUG) {
                    var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                    System.out.printf("Error: Zip FS read :%s%n[%s]%n", result,
                            hx.formatHex(result.getBytes()));
                }
            }
        });
        assertTrue(ex.getMessage().matches(errorMessage),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Validate that ZipFile will throw a ZipException if the
     * Extra length is 0 and the size, csize, LOC offset field is set to
     * 0xFFFFFFFF or the disk starting number is set to 0xFFFF
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("MissingZip64ExtraFieldEntries")
    public void zipFileBadExtraLength(int offset) throws IOException {
        zipArrayCopy = ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        if (offset == DISKNO_OFFSET_ZIP_NO_EXTRA_LEN) {
            buffer.putShort(offset, (short) ZIP64_MAGICCOUNT);
        } else {
            buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        }
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(BAD_ZIP_NAME)) {
                ZipEntry ze = zf.getEntry(ZIP_FILE_ENTRY_NAME);
                try (InputStream is = zf.getInputStream(ze)) {
                    String result = new String(is.readAllBytes());
                    if (DEBUG) {
                        var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                        System.out.printf("Error: Zip File read :%s%n[%s]%n", result,
                                hx.formatHex(result.getBytes()));
                    }
                }
            }
        });
        assertTrue(ex.getMessage().equals(INVALID_EXTRA_LENGTH),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Validate that ZipFS will throw a ZipException if the
     * Extra length is 0 and the size, csize, LOC offset field is set to
     * 0xFFFFFFFF or the disk starting number is set to 0xFFFF
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("MissingZip64ExtraFieldEntries")
    public void zipFSBadExtraLength(int offset) throws IOException {
        zipArrayCopy = ZIP_WITH_NO_EXTRA_LEN_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        if (offset == DISKNO_OFFSET_ZIP_NO_EXTRA_LEN) {
            buffer.putShort(offset, (short) ZIP64_MAGICCOUNT);
        } else {
            buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        }
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            try (FileSystem fs = FileSystems.newFileSystem(
                    Path.of(BAD_ZIP_NAME), Map.of())) {
                Path p = fs.getPath(ZIP_FILE_ENTRY_NAME);
                String result = new String(Files.readAllBytes(p));
                if (DEBUG) {
                    var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                    System.out.printf("Error: Zip FS read :%s%n[%s]%n", result,
                            hx.formatHex(result.getBytes()));
                }
            }
        });
        assertTrue(ex.getMessage().equals(INVALID_EXTRA_LENGTH),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Validate that ZipFile will read the Zip files created from the
     * byte arrays prior to modifying the arrays to check that the
     * expected ZipException is thrown.
     * @param  zipFile the byte array which represents the Zip file that should
     *                 be opened and read successfully.
     * @param message the expected text contained within the Zip entry
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("validZipFiles")
    public void readValidZipFile(byte[] zipFile, String message) throws IOException {
        // Write out the Zip file from the byte array
        Files.write(Path.of(ZIPFILE_NAME), zipFile);

        try (ZipFile zip = new ZipFile(ZIPFILE_NAME)) {
            ZipEntry ze = zip.getEntry(ZIP_FILE_ENTRY_NAME);
            try (InputStream is = zip.getInputStream(ze)) {
                String result = new String(is.readAllBytes());
                if (DEBUG) {
                    var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                    System.out.printf("ZipFile read :%s%n[%s]%n", result,
                            hx.formatHex(result.getBytes()));
                }
                assertEquals(message, result);
            }
        }
    }

    /**
     * Validate that ZipFS will read the Zip files created from the
     * byte arrays prior to modifying the arrays to check that the
     * expected ZipException is thrown.
     * @param  zipFile the byte array which represents the Zip file that should
     *                 be opened and read successfully.
     * @param message the expected text contained within the Zip entry
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("validZipFiles")
    public void readValidZipFileWithZipFs(byte[] zipFile, String message)
            throws IOException {
        // Write out the Zip file from the byte array
        Files.write(Path.of(ZIPFILE_NAME), zipFile);

        try (FileSystem fs = FileSystems.newFileSystem(
                Path.of(ZIPFILE_NAME), Map.of())) {
            Path p = fs.getPath(ZIP_FILE_ENTRY_NAME);
            String result = new String(Files.readAllBytes(p));
            if (DEBUG) {
                var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                System.out.printf("Zip FS read :%s%n[%s]%n", result,
                        hx.formatHex(result.getBytes()));
            }
            assertEquals(message, result);
        }
    }
}
