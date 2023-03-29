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
 * @bug 4770745 6218846 6218848 6237956
 * @summary test for correct detection and reporting of corrupted zip files
 * @author Martin Buchholz
 * @run junit CorruptedZipFiles
 */


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.zip.ZipFile.*;
import static org.junit.jupiter.api.Assertions.*;

public class CorruptedZipFiles {

    // Byte array holding a valid template ZIP
    private static byte[] template;

    // Copy of the template ZIP for modification by each test
    private byte[] copy;

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
    }

    /*
     * Delete the ZIP file produced after each test method
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /*
     * An End of Central Directory header with a CEN size exceeding
     * past the offset of the End record itself should be rejected with
     * a ZipException.
     */
    @Test
    public void excessiveCENSize() throws IOException {
        copy[endpos+ENDSIZ]=(byte)0xff;
        assertZipException(".*bad central directory size.*");
    }

    /*
     * An End of Central Directory header with a CEN offset incoherent
     * with the position calculated by subtracting the CEN size from
     * the End position should be rejected with a ZipException.
     */
    @Test
    public void excessiveCENOffset() throws IOException {
        copy[endpos+ENDOFF]=(byte)0xff;
        assertZipException(".*bad central directory offset.*");
    }

    /*
     * A CEN header with an unexpected signature should be rejected
     * with a ZipException.
     */
    @Test
    public void invalidCENSignature() throws IOException {
        copy[cenpos]++;
        assertZipException(".*bad signature.*");
    }

    /*
     * A CEN header where the general purpose bit flag 0 ('encrypted')
     * is set should be rejected with a ZipException
     */
    @Test
    public void encryptedEntry() throws IOException {
        copy[cenpos+CENFLG] |= 1;
        assertZipException(".*encrypted entry.*");
    }

    /*
     * A File name length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveFileNameLength() throws IOException {
        copy[cenpos+CENNAM]++;
        assertZipException(".*bad header size.*");
    }

    /*
     * A File name length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveFileNameLength2() throws IOException {
        copy[cenpos+CENNAM]   = (byte)0xfd;
        copy[cenpos+CENNAM+1] = (byte)0xfd;
        assertZipException(".*bad header size.*");
    }

    /*
     * If the last CEN header is not immediatly followed by the start
     * of the End record, this should be rejected with a ZipException.
     */
    @Test
    public void insufficientFilenameLength() throws IOException {
        copy[cenpos+CENNAM]--;
        assertZipException(".*bad header size.*");
    }

    /*
     * An Extra field length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveExtraFieldLength() throws IOException {
        copy[cenpos+CENEXT]++;
        assertZipException(".*bad header size.*");
    }

    /*
     * An Extra field length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveExtraFieldLength2() throws IOException {
        copy[cenpos+CENEXT]   = (byte)0xfd;
        copy[cenpos+CENEXT+1] = (byte)0xfd;
        assertZipException(".*bad header size.*");
    }

    /*
     * A File comment length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveCommentLength() throws IOException {
        copy[cenpos+CENCOM]++;
        assertZipException(".*bad header size.*");
    }

    /*
     * A CEN header with an unsupported compression method should be rejected
     * with a ZipException.
     */
    @Test
    public void unsupportedCompressionMethod() throws IOException {
        copy[cenpos+CENHOW] = 2;
        assertZipException(".*bad compression method.*");
    }

    /*
     * A LOC header with an unexpected signature should be rejected
     * with a ZipException.
     */
    @Test
    public void invalidLOCSignature() throws IOException {
        copy[locpos]++;
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
