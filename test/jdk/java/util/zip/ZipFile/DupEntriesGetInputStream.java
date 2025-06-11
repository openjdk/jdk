/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @bug 8349914
 * @summary Validate ZipFile::getFileInputStream returns the correct data when
 *          there are multiple entries with the same filename
 * @run junit DupEntriesGetInputStream
 */
class DupEntriesGetInputStream {

    // created through a call to createNormalZIP()
    private static final String NORMAL_ZIP_CONTENT_HEX = """
            504b03041400080808009195c35a00000000000000000000000006000000456e747279310bc9c8
            2c560022d7bc92a24a054300504b07089bc0e55b0f0000000f000000504b030414000808080091
            95c35a00000000000000000000000006000000456e747279320bc9c82c560022d7bc92a24a85bc
            d2dca4d4228590f27c00504b0708ebda8deb1800000018000000504b03041400080808009195c3
            5a00000000000000000000000006000000456e747279330bc9c82c560022d7bc92a24a05650563
            00504b0708d1eafe7d1100000011000000504b010214001400080808009195c35a9bc0e55b0f00
            00000f000000060000000000000000000000000000000000456e74727931504b01021400140008
            0808009195c35aebda8deb1800000018000000060000000000000000000000000043000000456e
            74727932504b010214001400080808009195c35ad1eafe7d110000001100000006000000000000
            000000000000008f000000456e74727933504b050600000000030003009c000000d40000000000
            """;

    // intentionally unused but left here to allow for constructing newer/updated
    // NORMAL_ZIP_CONTENT_HEX, when necessary
    private static String createNormalZIP() throws IOException {
        final ByteArrayOutputStream zipContent = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipContent)) {
            zos.putNextEntry(new ZipEntry(ENTRY1_NAME));
            zos.write(ENTRY1.getBytes(US_ASCII));
            zos.putNextEntry(new ZipEntry(ENTRY2_NAME));
            zos.write(ENTRY2.getBytes(US_ASCII));
            zos.putNextEntry(new ZipEntry(ENTRY3_NAME));
            zos.write(ENTRY3.getBytes(US_ASCII));
        }
        return HexFormat.of().formatHex(zipContent.toByteArray());
    }

    // Entry Names and their data to be added to the ZIP File
    private static final String ENTRY1_NAME = "Entry1";
    private static final String ENTRY1 = "This is Entry 1";

    private static final String ENTRY2_NAME = "Entry2";
    private static final String ENTRY2 = "This is Entry number Two";

    private static final String ENTRY3_NAME = "Entry3";
    private static final String ENTRY3 = "This is Entry # 3";

    // ZIP entry and its expected data
    record ZIP_ENTRY(String entryName, String data) {
    }

    private static final ZIP_ENTRY[] ZIP_ENTRIES = new ZIP_ENTRY[]{
            new ZIP_ENTRY(ENTRY1_NAME, ENTRY1),
            new ZIP_ENTRY(ENTRY2_NAME, ENTRY2),
            new ZIP_ENTRY(ENTRY1_NAME, ENTRY3)
    };

    private static Path dupEntriesZipFile;

    // 008F LOCAL HEADER #3       04034B50
    // ...
    // 00A1 Compressed Length     00000000
    // 00A5 Uncompressed Length   00000000
    // 00A9 Filename Length       0006
    // ...
    // 00AD Filename              'Entry3'
    private static final int ENTRY3_FILENAME_LOC_OFFSET = 0x00AD;

    // 013C CENTRAL HEADER #3     02014B50
    // ...
    // 0150 Compressed Length     00000011
    // 0154 Uncompressed Length   00000011
    // 0158 Filename Length       0006
    // ...
    // 016A Filename              'Entry3'
    private static final int ENTRY3_FILENAME_CEN_OFFSET = 0x016A;

    @BeforeAll
    static void createDupEntriesZIP() throws Exception {
        final byte[] originalZIPContent = HexFormat.of().parseHex(
                NORMAL_ZIP_CONTENT_HEX.replace("\n", ""));
        final ByteBuffer buf = ByteBuffer.wrap(originalZIPContent).order(LITTLE_ENDIAN);
        // replace the file name literal "Entry3" with the literal "Entry1", both in the
        // LOC header and the CEN of Entry3
        final int locEntry3LastCharOffset = ENTRY3_FILENAME_LOC_OFFSET + ENTRY3_NAME.length() - 1;
        buf.put(locEntry3LastCharOffset, (byte) 49); // 49 represents the character "1"
        final int cenEntry3LastCharOffset = ENTRY3_FILENAME_CEN_OFFSET + ENTRY3_NAME.length() - 1;
        buf.put(cenEntry3LastCharOffset, (byte) 49); // 49 represents the character "1"
        buf.rewind();
        // write out the manipulated ZIP content, containing duplicate entries, into a file
        // so that it can be read using ZipFile
        dupEntriesZipFile = Files.createTempFile(Path.of("."), "8349914-", ".zip");
        Files.write(dupEntriesZipFile, buf.array());
        System.out.println("created ZIP file with duplicate entries at " + dupEntriesZipFile);
    }

    /*
     * Validate that the correct ZipEntry data is returned when a List is used
     * to access entries returned from ZipFile::entries
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void testUsingListEntries() throws IOException {
        System.out.println("Processing entries via Collections.list()");
        try (ZipFile zf = new ZipFile(dupEntriesZipFile.toFile())) {
            var entryNumber = 0;
            for (var e : Collections.list(zf.entries())) {
                verifyEntry(entryNumber++, zf, e);
            }
        }
    }

    /*
     * Validate that the correct ZipEntry data is returned when a ZipEntryIterator
     * is used to access entries returned from ZipFile::entries
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void testUsingZipEntryIterator() throws IOException {
        System.out.println("Processing entries via a ZipEntryIterator");
        try (ZipFile zf = new ZipFile(dupEntriesZipFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = zf.entries();
            var entryNumber = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                verifyEntry(entryNumber++, zf, entry);
            }
        }
    }

    /*
     * Validate that the correct ZipEntry data is returned when a EntrySpliterator
     * is used to access entries returned from ZipFile::stream
     *
     * @throws IOException if an error occurs
     */
    @Test
    public void testUsingEntrySpliterator() throws IOException {
        System.out.println("Processing entries via a EntrySpliterator");
        AtomicInteger eNumber = new AtomicInteger(0);
        try (ZipFile zf = new ZipFile(dupEntriesZipFile.toFile())) {
            zf.stream().forEach(e -> {
                try {
                    verifyEntry(eNumber.getAndIncrement(), zf, e);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        }
    }

    /*
     * Verify the ZipEntry returned matches what is expected
     *
     * @param entryNumber offset into ZIP_ENTRIES containing the expected value
     *                    to be returned
     * @param zf          ZipFile containing the entry
     * @param e           ZipEntry to validate
     * @throws IOException
     */
    private static void verifyEntry(int entryNumber, ZipFile zf, ZipEntry e) throws IOException {
        System.out.println("Validating Entry: " + entryNumber);
        assertEquals(ZIP_ENTRIES[entryNumber].entryName(), e.getName());
        try (var in = zf.getInputStream(e)) {
            var entryData = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(ZIP_ENTRIES[entryNumber].data(), entryData);
        }
    }
}
