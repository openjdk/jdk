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

    // Golden ZIP
    private byte[] good;
    // Copy of the template for modification in tests
    private byte[] bad;
    // Some well-known locations in the golden ZIP
    private int endpos, cenpos, locpos;

    @BeforeTest
    public void setup() throws IOException {
        // Make a ZIP with a single entry
        Path zip = Path.of("x.zip");
        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zip))) {
            ZipEntry e = new ZipEntry("x");
            zos.putNextEntry(e);
            zos.write((int)'x');
        }

        // Read the contents of the file
        good = Files.readAllBytes(zip);
        Files.delete(zip);

        // Set up some well-known offsets
        endpos = good.length - ENDHDR;
        cenpos = u16(good, endpos+ENDOFF);
        locpos = u16(good, cenpos + CENOFF);

        // Run some sanity checks:
        assertEquals(u32(good, endpos), ENDSIG, "Where's ENDSIG?");
        assertEquals(u32(good, cenpos), CENSIG, "Where's CENSIG?");
        assertEquals(u32(good, locpos), LOCSIG, "Where's LOCSIG?");
        assertEquals(u16(good, locpos+LOCNAM), u16(good,cenpos+CENNAM),
            "Name field length mismatch");
        assertEquals(u16(good, locpos+LOCEXT), u16(good,cenpos+CENEXT),
            "Extra field length mismatch");
    }

    /**
     * Make a copy safe to modify by each test
     */
    @BeforeMethod
    public void setUp() {
        bad = Arrays.copyOf(good, good.length);
    }

    @Test
    public void corruptedENDSIZ() {
        bad[endpos+ENDSIZ]=(byte)0xff;
        checkZipException(bad, ".*bad central directory size.*");
    }

    @Test
    public void corruptedENDOFF() {
        bad[endpos+ENDOFF]=(byte)0xff;
        checkZipException(bad, ".*bad central directory offset.*");
    }

    @Test
    public void corruptedCENSIG() {
        bad[cenpos]++;
        checkZipException(bad, ".*bad signature.*");
    }

    @Test
    public void corruptedCENFLG() {
        bad[cenpos+CENFLG] |= 1;
        checkZipException(bad, ".*encrypted entry.*");

    }

    @Test
    public void corruptedCENNAM1() {
        bad[cenpos+CENNAM]++;
        checkZipException(bad, ".*bad header size.*");
    }

    @Test
    public void corruptedCENNAM2() {
        bad[cenpos+CENNAM]--;
        checkZipException(bad, ".*bad header size.*");

    }
    @Test
    public void corruptedCENNAM3() {
        bad[cenpos+CENNAM]   = (byte)0xfd;
        bad[cenpos+CENNAM+1] = (byte)0xfd;
        checkZipException(bad, ".*bad header size.*");
    }
    @Test
    public void corruptedCENEXT1() {
        bad[cenpos+CENEXT]++;
        checkZipException(bad, ".*bad header size.*");
    }
    @Test
    public void corruptedCENEXT2() {
        bad[cenpos+CENEXT]   = (byte)0xfd;
        bad[cenpos+CENEXT+1] = (byte)0xfd;
        checkZipException(bad, ".*bad header size.*");
    }

    @Test
    public void corruptedCENCOM() {
        bad[cenpos+CENCOM]++;
        checkZipException(bad, ".*bad header size.*");
    }
    @Test
    public void corruptedCENHOW() {
        bad[cenpos+CENHOW] = 2;
        checkZipException(bad, ".*bad compression method.*");
    }

    @Test
    public void corruptedLOCSIG() {
        bad[locpos]++;
        checkZipExceptionInGetInputStream(bad, ".*bad signature.*");
    }
    static int uniquifier = 432;

    static void checkZipExceptionImpl(byte[] data,
                                      String msgPattern,
                                      boolean getInputStream) {
        Path zip = Path.of("bad" + (uniquifier++) + ".zip");
        try {
            Files.write(zip, data);

            ZipException ex = expectThrows(ZipException.class, () -> {
                try (ZipFile zf = new ZipFile(zip.toFile())) {
                    if (getInputStream) {
                        try (InputStream is = zf.getInputStream(new ZipEntry("x"))) {
                            is.read();
                        }
                    }
                }
            });
            assertTrue(ex.getMessage().matches(msgPattern),
                    "Unexpected ZipException message: " + ex.getMessage());

        } catch (IOException e) {
            fail("Unexcpected IOEception writing test ZIP", e);
        } finally {
            try {
                Files.delete(zip);
            } catch (IOException e) {
                fail("Unexcpected exception deleting test ZIP", e);
            }
        }
    }

    static void checkZipException(byte[] data, String msgPattern) {
        checkZipExceptionImpl(data, msgPattern, false);
    }

    static void checkZipExceptionInGetInputStream(byte[] data, String msgPattern) {
        checkZipExceptionImpl(data, msgPattern, true);
    }

    static int u8(byte[] data, int offset) {
        return data[offset]&0xff;
    }

    static int u16(byte[] data, int offset) {
        return u8(data,offset) + (u8(data,offset+1)<<8);
    }

    static int u32(byte[] data, int offset) {
        return u16(data,offset) + (u16(data,offset+2)<<16);
    }
}
