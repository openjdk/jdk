/*
 * Copyright (c) 2024, Red Hat and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Formatter;

public class BootClassPathZip64Creator {

    private static final String ZIP_FILE = "Zip64.zip";

    private static void createTestZip() throws IOException {
        /*
         * Intersperced comments are from the "zipdetails" utility,
         * provided by https://metacpan.org/release/IO-Compress,
         * invoked as follows:
         *
         *    $ zipdetails Zip64.zip
         */
        byte[] local_header_1 = {
            // 0000 LOCAL HEADER #1       04034B50
            (byte)0x50, (byte)0x4b, (byte)0x03, (byte)0x04,
            // 0004 Extract Zip Spec      2D '4.5'
            (byte)0x2d,
            // 0005 Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 0006 General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 0008 Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 000A Last Mod Time         58DCA017 'Fri Jun 28 16:00:46 2024'
            (byte)0x17, (byte)0xa0, (byte)0xdc, (byte)0x58,
            // 000E CRC                   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0012 Compressed Length     FFFFFFFF
            (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
            // 0016 Uncompressed Length   FFFFFFFF
            (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
            // 001A Filename Length       0001
            (byte)0x01, (byte)0x00,
            // 001C Extra Length          0014
            (byte)0x14, (byte)0x00,
            // 001E Filename              '-'
            (byte)0x2d,
            // 001F Extra ID #0001        0001 'ZIP64'
            (byte)0x01, (byte)0x00,
            // 0021   Length              0010
            (byte)0x10, (byte)0x00,
            // 0023   Uncompressed Size   0000000000000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 002B   Compressed Size     0000000000000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
        };
        byte[] local_header_2 = {
            // 0033 LOCAL HEADER #2       04034B50
            (byte)0x50, (byte)0x4b, (byte)0x03, (byte)0x04,
            // 0037 Extract Zip Spec      0A '1.0'
            (byte)0x0a,
            // 0038 Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 0039 General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 003B Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 003D Last Mod Time         58DCA00B 'Fri Jun 28 16:00:22 2024'
            (byte)0x0b, (byte)0xa0, (byte)0xdc, (byte)0x58,
            // 0041 CRC                   8D4791A0
            (byte)0xa0, (byte)0x91, (byte)0x47, (byte)0x8d,
            // 0045 Compressed Length     00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0049 Uncompressed Length   00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 004D Filename Length       0007
            (byte)0x07, (byte)0x00,
            // 004F Extra Length          001C
            (byte)0x1c, (byte)0x00,
            // 0051 Filename              'T.class'
            (byte)0x54, (byte)0x2e, (byte)0x63, (byte)0x6c,
            (byte)0x61, (byte)0x73, (byte)0x73,
            // 0058 Extra ID #0001        5455 'UT: Extended Timestamp'
            (byte)0x55, (byte)0x54,
            // 005A   Length              0009
            (byte)0x09, (byte)0x00,
            // 005C   Flags               '03 mod access'
            (byte)0x03,
            // 005D   Mod Time            667F4E95 'Fri Jun 28 20:00:21 2024'
            (byte)0x95, (byte)0x4e, (byte)0x7f, (byte)0x66,
            // 0061   Access Time         667F4E95 'Fri Jun 28 20:00:21 2024'
            (byte)0x95, (byte)0x4e, (byte)0x7f, (byte)0x66,
            // 0065 Extra ID #0002        7875 'ux: Unix Extra Type 3'
            (byte)0x75, (byte)0x78,
            // 0067   Length              000B
            (byte)0x0b, (byte)0x00,
            // 0069   Version             01
            (byte)0x01,
            // 006A   UID Size            04
            (byte)0x04,
            // 006B   UID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
            // 006F   GID Size            04
            (byte)0x04,
            // 0070   GID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00
        };
        byte[] payload = {
            // 0074 PAYLOAD   .......4................<init>...()V...C
            //                ode........T...java/lang/Object.
            //                ................................*.......
            //                ...
            (byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x34, (byte)0x00, (byte)0x0a,
            (byte)0x0a, (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x07,
            (byte)0x07, (byte)0x00, (byte)0x08, (byte)0x07, (byte)0x00,
            (byte)0x09, (byte)0x01, (byte)0x00, (byte)0x06, (byte)0x3c,
            (byte)0x69, (byte)0x6e, (byte)0x69, (byte)0x74, (byte)0x3e,
            (byte)0x01, (byte)0x00, (byte)0x03, (byte)0x28, (byte)0x29,
            (byte)0x56, (byte)0x01, (byte)0x00, (byte)0x04, (byte)0x43,
            (byte)0x6f, (byte)0x64, (byte)0x65, (byte)0x0c, (byte)0x00,
            (byte)0x04, (byte)0x00, (byte)0x05, (byte)0x01, (byte)0x00,
            (byte)0x01, (byte)0x54, (byte)0x01, (byte)0x00, (byte)0x10,
            (byte)0x6a, (byte)0x61, (byte)0x76, (byte)0x61, (byte)0x2f,
            (byte)0x6c, (byte)0x61, (byte)0x6e, (byte)0x67, (byte)0x2f,
            (byte)0x4f, (byte)0x62, (byte)0x6a, (byte)0x65, (byte)0x63,
            (byte)0x74, (byte)0x00, (byte)0x20, (byte)0x00, (byte)0x02,
            (byte)0x00, (byte)0x03, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x01, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x04, (byte)0x00, (byte)0x05, (byte)0x00,
            (byte)0x01, (byte)0x00, (byte)0x06, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x11, (byte)0x00, (byte)0x01, (byte)0x00,
            (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x05,
            (byte)0x2a, (byte)0xb7, (byte)0x00, (byte)0x01, (byte)0xb1,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00
        };
        byte[] central_header_1 = {
            // 00E8 CENTRAL HEADER #1     02014B50
            (byte)0x50, (byte)0x4b, (byte)0x01, (byte)0x02,
            // 00EC Created Zip Spec      1E '3.0'
            (byte)0x1e,
            // 00ED Created OS            03 'Unix'
            (byte)0x03,
            // 00EE Extract Zip Spec      2D '4.5'
            (byte)0x2d,
            // 00EF Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 00F0 General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 00F2 Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 00F4 Last Mod Time         58DCA017 'Fri Jun 28 16:00:46 2024'
            (byte)0x17, (byte)0xa0, (byte)0xdc, (byte)0x58,
            // 00F8 CRC                   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 00FC Compressed Length     00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0100 Uncompressed Length   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0104 Filename Length       0001
            (byte)0x01, (byte)0x00,
            // 0106 Extra Length          0000
            (byte)0x00, (byte)0x00,
            // 0108 Comment Length        0000
            (byte)0x00, (byte)0x00,
            // 010A Disk Start            0000
            (byte)0x00, (byte)0x00,
            // 010C Int File Attributes   0000
            //      [Bit 0]               0 'Binary Data'
            (byte)0x00, (byte)0x00,
            // 010E Ext File Attributes   11800000
            (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x11,
            // 0112 Local Header Offset   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0116 Filename              '-'
            (byte)0x2d
        };
        byte[] central_header_2 = {
            // 0117 CENTRAL HEADER #2     02014B50
            (byte)0x50, (byte)0x4b, (byte)0x01, (byte)0x02,
            // 011B Created Zip Spec      1E '3.0'
            (byte)0x1e,
            // 011C Created OS            03 'Unix'
            (byte)0x03,
            // 011D Extract Zip Spec      0A '1.0'
            (byte)0x0a,
            // 011E Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 011F General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 0121 Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 0123 Last Mod Time         58DCA00B 'Fri Jun 28 16:00:22 2024'
            (byte)0x0b, (byte)0xa0, (byte)0xdc, (byte)0x58,
            // 0127 CRC                   8D4791A0
            (byte)0xa0, (byte)0x91, (byte)0x47, (byte)0x8d,
            // 012B Compressed Length     00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 012F Uncompressed Length   00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0133 Filename Length       0007
            (byte)0x07, (byte)0x00,
            // 0135 Extra Length          0018
            (byte)0x18, (byte)0x00,
            // 0137 Comment Length        0000
            (byte)0x00, (byte)0x00,
            // 0139 Disk Start            0000
            (byte)0x00, (byte)0x00,
            // 013B Int File Attributes   0000
            //      [Bit 0]               0 'Binary Data'
            (byte)0x00, (byte)0x00,
            // 013D Ext File Attributes   81A40000
            (byte)0x00, (byte)0x00, (byte)0xa4, (byte)0x81,
            // 0141 Local Header Offset   00000033
            (byte)0x33, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0145 Filename              'T.class'
            (byte)0x54, (byte)0x2e, (byte)0x63, (byte)0x6c, (byte)0x61, (byte)0x73, (byte)0x73,
            // 014C Extra ID #0001        5455 'UT: Extended Timestamp'
            (byte)0x55, (byte)0x54,
            // 014E   Length              0005
            (byte)0x05, (byte)0x00,
            // 0150   Flags               '03 mod access'
            (byte)0x03,
            // 0151   Mod Time            667F4E95 'Fri Jun 28 20:00:21 2024'
            (byte)0x95, (byte)0x4e, (byte)0x7f, (byte)0x66,
            // 0155 Extra ID #0002        7875 'ux: Unix Extra Type 3'
            (byte)0x75, (byte)0x78,
            // 0157   Length              000B
            (byte)0x0b, (byte)0x00,
            // 0159   Version             01
            (byte)0x01,
            // 015A   UID Size            04
            (byte)0x04,
            // 015B   UID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
            // 015F   GID Size            04
            (byte)0x04,
            // 0160   GID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00
        };
        byte[] zip64_end_central_dir_record = {
            // 0164 ZIP64 END CENTRAL DIR 06064B50
            //      RECORD
            (byte)0x50, (byte)0x4b, (byte)0x06, (byte)0x06,
            // 0168 Size of record        000000000000002C
            (byte)0x2c, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0170 Created Zip Spec      1E '3.0'
            (byte)0x1e,
            // 0171 Created OS            03 'Unix'
            (byte)0x03,
            // 0172 Extract Zip Spec      2D '4.5'
            (byte)0x2d,
            // 0173 Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 0174 Number of this disk   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0178 Central Dir Disk no   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 017C Entries in this disk  0000000000000002
            (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0184 Total Entries         0000000000000002
            (byte)0x02, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 018C Size of Central Dir   000000000000007C
            (byte)0x7c, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0194 Offset to Central dir 00000000000000E8
            (byte)0xe8, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00
        };
        byte[] zip64_end_central_dir_locator = {
            // 019C ZIP64 END CENTRAL DIR 07064B50
            //      LOCATOR
            (byte)0x50, (byte)0x4b, (byte)0x06, (byte)0x07,
            // 01A0 Central Dir Disk no   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01A4 Offset to Central dir 0000000000000164
            (byte)0x64, (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01AC Total no of Disks     00000001
            (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00
        };
        byte[] end_central_header = {
            // 01B0 END CENTRAL HEADER    06054B50
            (byte)0x50, (byte)0x4b, (byte)0x05, (byte)0x06,
            // 01B4 Number of this disk   0000
            (byte)0x00, (byte)0x00,
            // 01B6 Central Dir Disk no   0000
            (byte)0x00, (byte)0x00,
            // 01B8 Entries in this disk  0002
            (byte)0x02, (byte)0x00,
            // 01BA Total Entries         0002
            (byte)0x02, (byte)0x00,
            // 01BC Size of Central Dir   0000007C
            (byte)0x7c, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01C0 Offset to Central Dir 000000E8
            (byte)0xe8, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01C4 Comment Length        0000
            (byte)0x00, (byte)0x00
            // Done
        };
        File f = new File(ZIP_FILE);
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(local_header_1);
        fos.write(local_header_2);
        fos.write(payload);
        fos.write(central_header_1);
        fos.write(central_header_2);
        fos.write(zip64_end_central_dir_record);
        fos.write(zip64_end_central_dir_locator);
        fos.write(end_central_header);
        fos.close();
    }

    static void tearDown() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE));
    }

    // Copied from
    // test/jdk/java/net/httpclient/ManyRequestsLegacy.java in lieu of
    // javax.xml.bind.DataTypeConverter which is not available here.
    static String bytesToHexString(byte[] bytes) {
        if (bytes == null)
            return "null";

        StringBuilder sb = new StringBuilder(bytes.length * 2);

        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }

        return sb.toString();
    }

    private static void setUp() throws IOException, InterruptedException,
                                       NoSuchAlgorithmException {
        tearDown();
        createTestZip();
        // 45fe0ab09482d6bce7e2e903c16af9d6
        byte[] expected = {
            (byte)0x45, (byte)0xfe, (byte)0x0a, (byte)0xb0,
            (byte)0x94, (byte)0x82, (byte)0xd6, (byte)0xbc,
            (byte)0xe7, (byte)0xe2, (byte)0xe9, (byte)0x03,
            (byte)0xc1, (byte)0x6a, (byte)0xf9, (byte)0xd6
        };
        byte[] b = Files.readAllBytes(Path.of(ZIP_FILE));
        byte[] hash = MessageDigest.getInstance("MD5").digest(b);
        if (!Arrays.equals(hash, expected)) {
            throw new RuntimeException("MD5 mismatch for " + ZIP_FILE + ";"
                                       + " expected: " + bytesToHexString(expected) + ";"
                                       + " actual: " + bytesToHexString(hash));
        }
    }

    public static void main(String[] args) throws Exception {
        setUp();
    }

}
