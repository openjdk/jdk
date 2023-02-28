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
 * @run testng CorruptedZipFiles
 */

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.util.zip.ZipFile.*;
import static org.testng.Assert.*;

public class CorruptedZipFiles {

    // Byte array holding a valid template ZIP
    private byte[] template;

    // Copy of the template ZIP for modification by each test
    private byte[] copy;

    // Some well-known locations in the ZIP
    private int endpos, cenpos, locpos;

    // The path used when reading/writing the corrupted ZIP to disk
    private Path zip = Path.of("corrupted.zip");

    /**
     * Make a sample ZIP and calculate some known offsets into this ZIP
     */
    @BeforeTest
    public void setup() throws IOException {
        // Make a ZIP with a single entry
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry e = new ZipEntry("x");
            zos.putNextEntry(e);
            zos.write((int)'x');
        }
        template = out.toByteArray();

        // Calculate the offset of the End of central directory record
        endpos = template.length - ENDHDR;
        // Look up the offet of the Central directory header
        cenpos = u16(template, endpos+ENDOFF);
        // Look up the offset of the corresponding Local file header
        locpos = u16(template, cenpos + CENOFF);

        // Run some sanity checks on the valid ZIP:
        assertEquals(u32(template, endpos), ENDSIG, "Where's ENDSIG?");
        assertEquals(u32(template, cenpos), CENSIG, "Where's CENSIG?");
        assertEquals(u32(template, locpos), LOCSIG, "Where's LOCSIG?");
        assertEquals(u16(template, locpos+LOCNAM), u16(template,cenpos+CENNAM),
            "Name field length mismatch");
        assertEquals(u16(template, locpos+LOCEXT), u16(template,cenpos+CENEXT),
            "Extra field length mismatch");
    }

    /**
     * Make a copy safe to modify by each test
     */
    @BeforeMethod
    public void makeCopy() {
        copy = Arrays.copyOf(template, template.length);
    }

    /**
     * Delete the ZIP file produced after each test method
     */
    @AfterMethod
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * An End of Central Directory header with a CEN size exceeding
     * past the offset of the End record itself should be rejected with
     * a ZipException.
     */
    @Test
    public void excessiveCENSize() throws IOException {
        copy[endpos+ENDSIZ]=(byte)0xff;
        checkZipException(".*bad central directory size.*");
    }

    /**
     * An End of Central Directory header with a CEN offset incoherent
     * with the position calculated by subtracting the CEN size from
     * the End position should be rejected with a ZipException.
     */
    @Test
    public void excessiveCENOffset() throws IOException {
        copy[endpos+ENDOFF]=(byte)0xff;
        checkZipException(".*bad central directory offset.*");
    }

    /**
     * A CEN header with an unexpected signature should be rejected
     * with a ZipException.
     */
    @Test
    public void invalidCENSignature() throws IOException {
        copy[cenpos]++;
        checkZipException(".*bad signature.*");
    }

    /**
     * A CEN header where the general purpose bit flag 0 ('encrypted')
     * is set should be rejected with a ZipException
     */
    @Test
    public void encryptedEntry() throws IOException {
        copy[cenpos+CENFLG] |= 1;
        checkZipException(".*encrypted entry.*");
    }

    /**
     * A File name length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveFileNameLength() throws IOException {
        copy[cenpos+CENNAM]++;
        checkZipException(".*bad header size.*");
    }

    /**
     * A File name length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveFileNameLength2() throws IOException {
        copy[cenpos+CENNAM]   = (byte)0xfd;
        copy[cenpos+CENNAM+1] = (byte)0xfd;
        checkZipException(".*bad header size.*");
    }

    /**
     * If the last CEN header is not immediatly followed by the start
     * of the End record, this should be rejected with a ZipException.
     */
    @Test
    public void insufficientFilenameLength() throws IOException {
        copy[cenpos+CENNAM]--;
        checkZipException(".*bad header size.*");
    }

    /**
     * An Extra field length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveExtraFieldLength() throws IOException {
        copy[cenpos+CENEXT]++;
        checkZipException(".*bad header size.*");
    }

    /**
     * An Extra field length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveExtraFieldLength2() throws IOException {
        copy[cenpos+CENEXT]   = (byte)0xfd;
        copy[cenpos+CENEXT+1] = (byte)0xfd;
        checkZipException(".*bad header size.*");
    }

    /**
     * A File comment length which makes the CEN header overflow into the
     * End of central directory record should be rejected with a ZipException.
     */
    @Test
    public void excessiveCommentLength() throws IOException {
        copy[cenpos+CENCOM]++;
        checkZipException(".*bad header size.*");
    }

    /**
     * A CEN header with an unsupported compression method should be rejected
     * with a ZipException.
     */
    @Test
    public void unsupportedCompressionMethod() throws IOException {
        copy[cenpos+CENHOW] = 2;
        checkZipException(".*bad compression method.*");
    }

    /**
     * A LOC header with an unexpected signature should be rejected
     * with a ZipException.
     */
    @Test
    public void invalidLOCSignature() throws IOException {
        copy[locpos]++;
        checkZipExceptionInGetInputStream(".*bad signature.*");
    }

    void checkZipExceptionImpl(String msgPattern,
                               boolean getInputStream) throws IOException {

        Files.write(zip, copy);

        ZipException ex = expectThrows(ZipException.class, () -> {
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                if (getInputStream) {
                    try (InputStream is = zf.getInputStream(new ZipEntry("x"))) {
                        is.transferTo(OutputStream.nullOutputStream());
                    }
                }
            }
        });
        assertTrue(ex.getMessage().matches(msgPattern),
                "Unexpected ZipException message: " + ex.getMessage());

    }

    void checkZipException(String msgPattern) throws IOException {
        checkZipExceptionImpl(msgPattern, false);
    }

    void checkZipExceptionInGetInputStream(String msgPattern) throws IOException {
        checkZipExceptionImpl(msgPattern, true);
    }

    static int u8(byte[] data, int offset) {
        return data[offset] & 0xff;
    }

    static int u16(byte[] data, int offset) {
        return u8(data,offset) + (u8(data,offset+1) << 8);
    }

    static int u32(byte[] data, int offset) {
        return u16(data,offset) + (u16(data,offset+2) << 16);
    }
}
