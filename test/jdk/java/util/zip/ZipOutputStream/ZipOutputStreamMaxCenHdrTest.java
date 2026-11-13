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
 * @bug 8396845
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.stream.Stream;
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
    // Tag for the 'unknown' field type, specified in APPNOTE.txt 'Third party mappings'
    static final short UNKNOWN_ZIP_TAG = (short) 0x9902;
    // ZIP file to be used by the tests
    static final Path ZIP_FILE = Path.of("maxCENHdrTest.zip");
    // Zip Entry name used by tests
    static final String ENTRY_NAME = "EntryName";
    // CEN Extra field info-zip extended timestamp MTIME size
    static final int MTIME_EXTRA_SIZE = 9;

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
     * @param  cenSize size of the combined CEN header to test
     * @param  setLastModifiedTime true if ZipEntry::setLastModifiedTime called;false otherwise
     */
    @ParameterizedTest
    @MethodSource("CEN_header_test_params")
    void setNameExtraSizeTest(int cenSize, boolean setLastModifiedTime) {
        int timestampSize = setLastModifiedTime ? MTIME_EXTRA_SIZE : 0;
        int length = cenSize - ZipEntry.CENHDR - timestampSize;
        boolean expectException = cenSize > MAX_COMBINED_CEN_HEADER_SIZE;
        ZipEntry zipEntry = new ZipEntry("a".repeat(length));
        if (setLastModifiedTime) {
            zipEntry.setLastModifiedTime(FileTime.fromMillis(0));
        }
        writeZipEntry(zipEntry, expectException);
    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     * @param  cenSize size of the combined CEN header to test
     * @param  setLastModifiedTime true if ZipEntry::setLastModifiedTime called;false otherwise
     */
    @ParameterizedTest
    @MethodSource("CEN_header_test_params")
    void setCommentExtraSizeTest(int cenSize, boolean setLastModifiedTime) {
        int timestampSize = setLastModifiedTime ? MTIME_EXTRA_SIZE : 0;
        int length = cenSize - ZipEntry.CENHDR - ENTRY_NAME.length() - timestampSize;
        boolean expectException = cenSize > MAX_COMBINED_CEN_HEADER_SIZE;
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        zipEntry.setComment("a".repeat(length));
        if (setLastModifiedTime) {
            zipEntry.setLastModifiedTime(FileTime.fromMillis(0));
        }
        writeZipEntry(zipEntry, expectException);
    }

    /**
     * Validate an ZipException is thrown when the combined CEN Header, name
     * length, comment length, and extra data length exceeds 65,535 bytes when
     * the ZipOutputStream is closed.
     * @param  cenSize size of the combined CEN header to test
     * @param  setLastModifiedTime true if ZipEntry::setLastModifiedTime called;false otherwise
     */
    @ParameterizedTest
    @MethodSource("CEN_header_test_params")
    void setExtraExtraSizeTest(int cenSize, boolean setLastModifiedTime) {
        int timestampSize = setLastModifiedTime ? MTIME_EXTRA_SIZE : 0;
        int length = cenSize - ZipEntry.CENHDR - ENTRY_NAME.length() - timestampSize;
        boolean expectException = cenSize > MAX_COMBINED_CEN_HEADER_SIZE;
        final byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'a');
        // Little-endian ByteBuffer for updating the header fields
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        // We use the 'unknown' tag, specified in APPNOTE.TXT, 4.6.1 Third party mappings'
        buffer.putShort(UNKNOWN_ZIP_TAG);
        // Size of the actual data
        buffer.putShort((short) (length - 2 * Short.BYTES));
        ZipEntry zipEntry = new ZipEntry(ENTRY_NAME);
        System.out.printf("extra Len= %s, exception: %s%n", bytes.length, expectException);
        zipEntry.setExtra(bytes);
        if (setLastModifiedTime) {
            zipEntry.setLastModifiedTime(FileTime.fromMillis(0));
        }
        writeZipEntry(zipEntry, expectException);
    }

    /**
     * Method used to provide arguments for testing the combined CEN size limit.
     * A ZipException is expected when the stream is closed when the CEN Header,
     * name length, comment length, and extra data length exceeds 65,535 bytes
     * <p>
     * Each argument pair contains the final combined CEN size and a boolean
     * indicating whether to set the last modification time.
     *
     * @return stream of test arguments
     */
    private static Stream<Arguments> CEN_header_test_params() {
        return Stream.of(
                Arguments.of(65_534, false),
                Arguments.of(65_535, false),
                Arguments.of(65_534, true),
                Arguments.of(65_535, true),
                Arguments.of(65_536, true)
        );
    }

    /**
     * Write a single Zip entry using ZipOutputStream
     *
     * @param zipEntry the ZipEntry to write
     * @param expectedException true if a ZipException should be thrown; false otherwise
     */
    private static void writeZipEntry(ZipEntry zipEntry, boolean expectedException) {
        boolean receivedException = false;
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(ZIP_FILE)))) {
            assertDoesNotThrow(() -> zos.putNextEntry(zipEntry));
        } catch (Exception e) {
            assertInstanceOf(ZipException.class, e);
            String emsg = e.getMessage();
            assertNotNull(emsg, "Excpected ZipException message!");
            assertTrue(emsg.contains("bad header size"), "Unexpected ZipException message: " + emsg);
            receivedException = true;
            System.out.printf("writeZipEntry: exception: %s%n", e.getMessage());
        }
        assertEquals(expectedException, receivedException);
        // If we do not expect an Exception, sanity check we can open the ZIP file
        if (!expectedException) {
            assertDoesNotThrow(() -> {
                try (ZipFile zip = new ZipFile(ZIP_FILE.toFile())) {
                    assertEquals(1, zip.size());
                    ZipEntry actual = zip.getEntry(zipEntry.getName());
                    assertNotNull(actual);
                    assertEquals(zipEntry.getComment(), actual.getComment());
                }
            });
        }
    }
}
