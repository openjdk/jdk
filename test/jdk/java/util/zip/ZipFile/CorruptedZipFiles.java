/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4770745 6218846 6218848 6237956 8313765 8316141
 * @summary test for correct detection and reporting of corrupted zip files
 * @author Martin Buchholz
 * @run junit CorruptedZipFiles
 */


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.zip.ZipFile.*;
import static org.junit.jupiter.api.Assertions.*;

public class CorruptedZipFiles {

    /*
     * Byte array holding a valid template ZIP.
     *
     * The 'good' ZIP file has the following structure:
     *
     * 0000 LOCAL HEADER #1       04034B50
     * 0004 Extract Zip Spec      14 '2.0'
     * 0005 Extract OS            00 'MS-DOS'
     * 0006 General Purpose Flag  0808
     *      [Bits 1-2]            0 'Normal Compression'
     *      [Bit  3]              1 'Streamed'
     *      [Bit 11]              1 'Language Encoding'
     * 0008 Compression Method    0008 'Deflated'
     * 000A Last Mod Time         567F7D07 'Fri Mar 31 15:40:14 2023'
     * 000E CRC                   00000000
     * 0012 Compressed Length     00000000
     * 0016 Uncompressed Length   00000000
     * 001A Filename Length       0001
     * 001C Extra Length          0000
     * 001E Filename              'x'
     * 001F PAYLOAD               ...
     *
     * 0022 STREAMING DATA HEADER 08074B50
     * 0026 CRC                   8CDC1683
     * 002A Compressed Length     00000003
     * 002E Uncompressed Length   00000001
     *
     * 0032 CENTRAL HEADER #1     02014B50
     * 0036 Created Zip Spec      14 '2.0'
     * 0037 Created OS            00 'MS-DOS'
     * 0038 Extract Zip Spec      14 '2.0'
     * 0039 Extract OS            00 'MS-DOS'
     * 003A General Purpose Flag  0808
     *      [Bits 1-2]            0 'Normal Compression'
     *      [Bit  3]              1 'Streamed'
     *      [Bit 11]              1 'Language Encoding'
     * 003C Compression Method    0008 'Deflated'
     * 003E Last Mod Time         567F7D07 'Fri Mar 31 15:40:14 2023'
     * 0042 CRC                   8CDC1683
     * 0046 Compressed Length     00000003
     * 004A Uncompressed Length   00000001
     * 004E Filename Length       0001
     * 0050 Extra Length          0000
     * 0052 Comment Length        0000
     * 0054 Disk Start            0000
     * 0056 Int File Attributes   0000
     *      [Bit 0]               0 'Binary Data'
     * 0058 Ext File Attributes   00000000
     * 005C Local Header Offset   00000000
     * 0060 Filename              'x'
     *
     * 0061 END CENTRAL HEADER    06054B50
     * 0065 Number of this disk   0000
     * 0067 Central Dir Disk no   0000
     * 0069 Entries in this disk  0001
     * 006B Total Entries         0001
     * 006D Size of Central Dir   0000002F
     * 0071 Offset to Central Dir 00000032
     * 0075 Comment Length        0000
     *
     */
    private static byte[] template;

    // Copy of the template ZIP for modification by each test
    private byte[] copy;

    // Litte-endian ByteBuffer for manipulating the ZIP copy
    private ByteBuffer buffer;

    // Some well-known locations in the ZIP
    private static int endpos, cenpos, locpos;

    // The path used when reading/writing the corrupted ZIP to disk
    private Path zip = Path.of("corrupted.zip");

    /*
     * Make a sample ZIP and calculate some known offsets into this ZIP
     */
    @BeforeAll
    public static void setup() throws IOException {
        // Make a ZIP with a single entry
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry e = new ZipEntry("x");
            zos.putNextEntry(e);
            zos.write((int)'x');
        }
        template = out.toByteArray();
        // ByteBuffer for reading fields from the ZIP
        ByteBuffer buffer = ByteBuffer.wrap(template).order(ByteOrder.LITTLE_ENDIAN);

        // Calculate the offset of the End of central directory record
        endpos = template.length - ENDHDR;
        // Look up the offet of the Central directory header
        cenpos = buffer.getShort(endpos + ENDOFF);
        // Look up the offset of the corresponding Local file header
        locpos = buffer.getShort(cenpos + CENOFF);

        // Run some sanity checks on the valid ZIP:
        assertEquals(ENDSIG, buffer.getInt(endpos),"Where's ENDSIG?");
        assertEquals(CENSIG, buffer.getInt(cenpos),"Where's CENSIG?");
        assertEquals(LOCSIG, buffer.getInt(locpos),"Where's LOCSIG?");
        assertEquals(buffer.getShort(cenpos+CENNAM),
                buffer.getShort(locpos+LOCNAM),
                "Name field length mismatch");
        assertEquals(buffer.getShort(cenpos+CENEXT),
                buffer.getShort( locpos+LOCEXT),
                "Extra field length mismatch");
    }

    /*
     * Make a copy safe to modify by each test
     */
    @BeforeEach
    public void makeCopy() {
        copy = template.clone();
        buffer = ByteBuffer.wrap(copy).order(ByteOrder.LITTLE_ENDIAN);
    }

    /*
     * Delete the ZIP file produced after each test method
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /*
     * A ZipException is thrown when the 'End of Central Directory'
     * (END) header has a CEN size exceeding past the offset of the END record
     */
    @Test
    public void excessiveCENSize() throws IOException {
        buffer.putInt(endpos+ENDSIZ, 0xff000000);
        assertZipException(".*bad central directory size.*");
    }

    /*
     * A ZipException is thrown when the 'End of Central Directory'
     * (END) header has a CEN offset with an invalid value.
     */
    @Test
    public void excessiveCENOffset() throws IOException {
        buffer.putInt(endpos+ENDOFF, 0xff000000);
        assertZipException(".*bad central directory offset.*");
    }

    /*
     * A ZipException is thrown when a CEN header has an unexpected signature
     */
    @Test
    public void invalidCENSignature() throws IOException {
        int existingSignature = buffer.getInt(cenpos);
        buffer.putInt(cenpos, existingSignature +1);
        assertZipException(".*bad signature.*");
    }

    /*
     * A ZipException is thrown when a CEN header has the
     * 'general purpose bit flag 0' ('encrypted') set.
     */
    @Test
    public void encryptedEntry() throws IOException {
        copy[cenpos+CENFLG] |= 1;
        assertZipException(".*encrypted entry.*");
    }

    /*
     * A ZipException is thrown when a CEN header has a file name
     *  length which makes the CEN header overflow into the
     * 'End of central directory' record.
     */
    @Test
    public void excessiveFileNameLength() throws IOException {
        short existingNameLength = buffer.getShort(cenpos + CENNAM);
        buffer.putShort(cenpos+CENNAM, (short) (existingNameLength + 1));
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown when a CEN header has a
     * file name length which makes the CEN header overflow into the
     * 'End of central directory' record.
     */
    @Test
    public void excessiveFileNameLength2() throws IOException {
        buffer.putShort(cenpos + CENNAM, (short) 0xfdfd);
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown if the last CEN header is not immediately
     * followed by the start of the 'End of central directory' record
     */
    @Test
    public void insufficientFilenameLength() throws IOException {
        short existingNameLength = buffer.getShort(cenpos + CENNAM);
        buffer.putShort(cenpos+CENNAM, (short) (existingNameLength - 1));
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown if a CEN header has an
     * extra field length which makes the CEN header overflow into the
     * End of central directory record.
     */
    @Test
    public void excessiveExtraFieldLength() throws IOException {
        buffer.put(cenpos+CENEXT, (byte) 0xff);
        buffer.put(cenpos+CENEXT+1, (byte) 0xff);
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown if a CEN header has an
     * extra field length which makes the CEN header overflow into the
     * End of central directory record.
     */
    @Test
    public void excessiveExtraFieldLength2() throws IOException {
        buffer.putShort(cenpos+CENEXT, (short) 0xfdfd);
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown when a CEN header has a comment length
     * which overflows into the 'End of central directory' record
     */
    @Test
    public void excessiveCommentLength() throws IOException {
        short existingCommentLength = buffer.getShort(cenpos + CENCOM);
        buffer.putShort(cenpos+CENCOM, (short) (existingCommentLength + 1));
        assertZipException(".*bad header size.*");
    }

    /*
     * A ZipException is thrown when a CEN header has a
     * compression method field which is unsupported by the implementation
     */
    @Test
    public void unsupportedCompressionMethod() throws IOException {
        copy[cenpos+CENHOW] = 2;
        assertZipException(".*bad compression method.*");
    }

    /*
     * A ZipException is thrown when a LOC header has an unexpected signature
     */
    @Test
    public void invalidLOCSignature() throws IOException {
        int existingSignatur = buffer.getInt(locpos);
        buffer.putInt(locpos, existingSignatur +1);
        assertZipException(".*bad signature.*");
    }

    /*
     * Assert that opening a ZIP file and consuming the entry's
     * InputStream using the ZipFile API fails with a ZipException
     * with a message matching the given pattern.
     *
     * The ZIP file opened is the contents of the 'copy' byte array.
     */
    void assertZipException(String msgPattern) throws IOException {

        Files.write(zip, copy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                try (InputStream is = zf.getInputStream(new ZipEntry("x"))) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
            }
        });
        assertTrue(ex.getMessage().matches(msgPattern),
                "Unexpected ZipException message: " + ex.getMessage());

    }
}
