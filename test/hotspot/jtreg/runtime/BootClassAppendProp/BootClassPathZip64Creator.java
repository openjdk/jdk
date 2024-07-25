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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.Process;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Formatter;

/*
 * The BootClassPathZip64Creator driver outputs a minimal reproducer ZIP file.
 *
 * The "payload" class file was generated with these commands:
 *
 *    $ javac -version
 *    javac 1.8.0_402
 *    $ echo -n "class T{}" > T.java
 *    $ javac -g:none T.java
 *    $ sha512sum T.class
 *    3aa854429abde2aeb5e629f69e072254\
 *    662ded28d180d8b26d702655af262682\
 *    5282ea0c052e73c027b1fd6155052229\
 *    7d42d6b35fcc180c8c3124542212d381  T.class
 *
 * The class file is included in the ZIP file without compression so that it is
 * easily identifiable in the hexadecimal listing.
 *
 * Setting timestamps of the standard input file '-' reproducibly is not
 * possible, so this class overwrites those bytes in the ZIP file.
 *
 * A best effort is made to run Info-ZIP to generate the test ZIP file, but
 * failing that for whatever reason, a ZIP file is written from inline byte
 * arrays.
 *
 * Within those arrays, intersperced comments are from the "zipdetails" utility,
 * provided by https://metacpan.org/release/IO-Compress, invoked as follows:
 *
 *    $ zipdetails Z64.zip
 *
 * This implementation forgoes modern conveniences like java.util.HexFormat so
 * that it runs unmodified on OpenJDK 8.
 */
public class BootClassPathZip64Creator {

    private static final String CLASS_FILE =
        BootClassPathZip64Test.CLASS_NAME + ".class";
    private static final Path CLASS_FILE_PATH =
        Paths.get(System.getProperty("user.dir"), CLASS_FILE);
    private static final Path ZIP_PATH = BootClassPathZip64Test.ZIP_PATH;
    private static final byte[] class_bytes = {
        // 0041 PAYLOAD   .......4................<init>...()V...C
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
    // 3aa854429abde2aeb5e629f69e072254662ded28d180d8b26d702655af262682
    // 5282ea0c052e73c027b1fd61550522297d42d6b35fcc180c8c3124542212d381  T.class
    private static final byte[] class_sha512_digest = {
        (byte)0x3a, (byte)0xa8, (byte)0x54, (byte)0x42,
        (byte)0x9a, (byte)0xbd, (byte)0xe2, (byte)0xae,
        (byte)0xb5, (byte)0xe6, (byte)0x29, (byte)0xf6,
        (byte)0x9e, (byte)0x07, (byte)0x22, (byte)0x54,
        (byte)0x66, (byte)0x2d, (byte)0xed, (byte)0x28,
        (byte)0xd1, (byte)0x80, (byte)0xd8, (byte)0xb2,
        (byte)0x6d, (byte)0x70, (byte)0x26, (byte)0x55,
        (byte)0xaf, (byte)0x26, (byte)0x26, (byte)0x82,
        (byte)0x52, (byte)0x82, (byte)0xea, (byte)0x0c,
        (byte)0x05, (byte)0x2e, (byte)0x73, (byte)0xc0,
        (byte)0x27, (byte)0xb1, (byte)0xfd, (byte)0x61,
        (byte)0x55, (byte)0x05, (byte)0x22, (byte)0x29,
        (byte)0x7d, (byte)0x42, (byte)0xd6, (byte)0xb3,
        (byte)0x5f, (byte)0xcc, (byte)0x18, (byte)0x0c,
        (byte)0x8c, (byte)0x31, (byte)0x24, (byte)0x54,
        (byte)0x22, (byte)0x12, (byte)0xd3, (byte)0x81
    };
    // cd5f764886bbd5f5c6698925550e1ecfaab9ef90dd9e81558ebe34c81cc51413
    // e3da4ca6273be7f350a58a52ee2dbffde1ab3c8d39efa8199c169a6b7fad4703  Z64.zip
    private static final byte[] zip_sha512_digest = {
        (byte)0xcd, (byte)0x5f, (byte)0x76, (byte)0x48,
        (byte)0x86, (byte)0xbb, (byte)0xd5, (byte)0xf5,
        (byte)0xc6, (byte)0x69, (byte)0x89, (byte)0x25,
        (byte)0x55, (byte)0x0e, (byte)0x1e, (byte)0xcf,
        (byte)0xaa, (byte)0xb9, (byte)0xef, (byte)0x90,
        (byte)0xdd, (byte)0x9e, (byte)0x81, (byte)0x55,
        (byte)0x8e, (byte)0xbe, (byte)0x34, (byte)0xc8,
        (byte)0x1c, (byte)0xc5, (byte)0x14, (byte)0x13,
        (byte)0xe3, (byte)0xda, (byte)0x4c, (byte)0xa6,
        (byte)0x27, (byte)0x3b, (byte)0xe7, (byte)0xf3,
        (byte)0x50, (byte)0xa5, (byte)0x8a, (byte)0x52,
        (byte)0xee, (byte)0x2d, (byte)0xbf, (byte)0xfd,
        (byte)0xe1, (byte)0xab, (byte)0x3c, (byte)0x8d,
        (byte)0x39, (byte)0xef, (byte)0xa8, (byte)0x19,
        (byte)0x9c, (byte)0x16, (byte)0x9a, (byte)0x6b,
        (byte)0x7f, (byte)0xad, (byte)0x47, (byte)0x03,
    };

    // Copied and renamed from
    // test/jdk/java/net/httpclient/ManyRequestsLegacy.java's
    // bytesToHexString.
    private static String toHexString(byte[] bytes) {
        if (bytes == null)
            return "null";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return sb.toString();
    }

    private static void ensureMatch(byte[] expected, byte[] bytes, String name)
        throws NoSuchAlgorithmException {
        String digest_type = "SHA-512";
        byte[] digest = MessageDigest.getInstance(digest_type).digest(bytes);
        if (!Arrays.equals(digest, expected)) {
            throw new RuntimeException(digest_type + " mismatch for " + name +
                                       "; expected: " + toHexString(expected) +
                                       "; got: " + toHexString(digest));
        }
    }

    private static void dumpTestZip() throws IOException,
                                             NoSuchAlgorithmException {
        ensureMatch(class_sha512_digest, class_bytes, CLASS_FILE);
        byte[] local_header_1 = {
            // 0000 LOCAL HEADER #1       04034B50
            (byte)0x50, (byte)0x4b, (byte)0x03, (byte)0x04,
            // 0004 Extract Zip Spec      0A '1.0'
            (byte)0x0a,
            // 0005 Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 0006 General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 0008 Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 000A Last Mod Time         58F09093 'Tue Jul 16 14:04:38 2024'
            (byte)0x93, (byte)0x90, (byte)0xf0, (byte)0x58,
            // 000E CRC                   8D4791A0
            (byte)0xa0, (byte)0x91, (byte)0x47, (byte)0x8d,
            // 0012 Compressed Length     00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0016 Uncompressed Length   00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 001A Filename Length       0007
            (byte)0x07, (byte)0x00,
            // 001C Extra Length          001C
            (byte)0x1c, (byte)0x00,
            // 001E Filename              'T.class'
            (byte)0x54, (byte)0x2e,
            (byte)0x63, (byte)0x6c, (byte)0x61, (byte)0x73, (byte)0x73,
            // 0025 Extra ID #0001        5455 'UT: Extended Timestamp'
            (byte)0x55, (byte)0x54,
            // 0027   Length              0009
            (byte)0x09, (byte)0x00,
            // 0029   Flags               '03 mod access'
            (byte)0x03,
            // 002A   Mod Time            6696EE76 'Tue Jul 16 18:04:38 2024'
            (byte)0x76, (byte)0xee, (byte)0x96, (byte)0x66,
            // 002E   Access Time         6696EE76 'Tue Jul 16 18:04:38 2024'
            (byte)0x76, (byte)0xee, (byte)0x96, (byte)0x66,
            // 0032 Extra ID #0002        7875 'ux: Unix Extra Type 3'
            (byte)0x75, (byte)0x78,
            // 0034   Length              000B
            (byte)0x0b, (byte)0x00,
            // 0036   Version             01
            (byte)0x01,
            // 0037   UID Size            04
            (byte)0x04,
            // 0038   UID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
            // 003C   GID Size            04
            (byte)0x04,
            // 003D   GID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
        };
        // See class_bytes above.
        byte[] local_header_2 = {
            // 00B5 LOCAL HEADER #2       04034B50
            (byte)0x50, (byte)0x4b, (byte)0x03, (byte)0x04,
            // 00B9 Extract Zip Spec      2D '4.5'
            (byte)0x2d,
            // 00BA Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 00BB General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 00BD Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 00BF Last Mod Time         58F77CBC 'Tue Jul 23 11:37:56 2024'
            (byte)0xbc, (byte)0x7c, (byte)0xf7, (byte)0x58,
            // 00C3 CRC                   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 00C7 Compressed Length     FFFFFFFF
            (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
            // 00CB Uncompressed Length   FFFFFFFF
            (byte)0xff, (byte)0xff, (byte)0xff, (byte)0xff,
            // 00CF Filename Length       0001
            (byte)0x01, (byte)0x00,
            // 00D1 Extra Length          0014
            (byte)0x14, (byte)0x00,
            // 00D3 Filename              '-'
            (byte)0x2d,
            // 00D4 Extra ID #0001        0001 'ZIP64'
            (byte)0x01, (byte)0x00,
            // 00D6   Length              0010
            (byte)0x10, (byte)0x00,
            // 00D8   Uncompressed Size   0000000000000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 00E0   Compressed Size     0000000000000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        };
        byte[] central_header_1 = {
            // 00E8 CENTRAL HEADER #1     02014B50
            (byte)0x50, (byte)0x4b, (byte)0x01, (byte)0x02,
            // 00EC Created Zip Spec      1E '3.0'
            (byte)0x1e,
            // 00ED Created OS            03 'Unix'
            (byte)0x03,
            // 00EE Extract Zip Spec      0A '1.0'
            (byte)0x0a,
            // 00EF Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 00F0 General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 00F2 Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 00F4 Last Mod Time         58F09093 'Tue Jul 16 14:04:38 2024'
            (byte)0x93, (byte)0x90, (byte)0xf0, (byte)0x58,
            // 00F8 CRC                   8D4791A0
            (byte)0xa0, (byte)0x91, (byte)0x47, (byte)0x8d,
            // 00FC Compressed Length     00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0100 Uncompressed Length   00000074
            (byte)0x74, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0104 Filename Length       0007
            (byte)0x07, (byte)0x00,
            // 0106 Extra Length          0018
            (byte)0x18, (byte)0x00,
            // 0108 Comment Length        0000
            (byte)0x00, (byte)0x00,
            // 010A Disk Start            0000
            (byte)0x00, (byte)0x00,
            // 010C Int File Attributes   0000
            //      [Bit 0]               0 'Binary Data'
            (byte)0x00, (byte)0x00,
            // 010E Ext File Attributes   81A40000
            (byte)0x00, (byte)0x00, (byte)0xa4, (byte)0x81,
            // 0112 Local Header Offset   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0116 Filename              'T.class'
            (byte)0x54, (byte)0x2e,
            (byte)0x63, (byte)0x6c, (byte)0x61, (byte)0x73, (byte)0x73,
            // 011D Extra ID #0001        5455 'UT: Extended Timestamp'
            (byte)0x55, (byte)0x54,
            // 011F   Length              0005
            (byte)0x05, (byte)0x00,
            // 0121   Flags               '03 mod access'
            (byte)0x03,
            // 0122   Mod Time            6696EE76 'Tue Jul 16 18:04:38 2024'
            (byte)0x76, (byte)0xee, (byte)0x96, (byte)0x66,
            // 0126 Extra ID #0002        7875 'ux: Unix Extra Type 3'
            (byte)0x75, (byte)0x78,
            // 0128   Length              000B
            (byte)0x0b, (byte)0x00,
            // 012A   Version             01
            (byte)0x01,
            // 012B   UID Size            04
            (byte)0x04,
            // 012C   UID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
            // 0130   GID Size            04
            (byte)0x04,
            // 0131   GID                 0040519C
            (byte)0x9c, (byte)0x51, (byte)0x40, (byte)0x00,
        };
        byte[] central_header_2 = {
            // 0135 CENTRAL HEADER #2     02014B50
            (byte)0x50, (byte)0x4b, (byte)0x01, (byte)0x02,
            // 0139 Created Zip Spec      1E '3.0'
            (byte)0x1e,
            // 013A Created OS            03 'Unix'
            (byte)0x03,
            // 013B Extract Zip Spec      2D '4.5'
            (byte)0x2d,
            // 013C Extract OS            00 'MS-DOS'
            (byte)0x00,
            // 013D General Purpose Flag  0000
            (byte)0x00, (byte)0x00,
            // 013F Compression Method    0000 'Stored'
            (byte)0x00, (byte)0x00,
            // 0141 Last Mod Time         58F77CBC 'Tue Jul 23 11:37:56 2024'
            (byte)0xbc, (byte)0x7c, (byte)0xf7, (byte)0x58,
            // 0145 CRC                   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0149 Compressed Length     00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 014D Uncompressed Length   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0151 Filename Length       0001
            (byte)0x01, (byte)0x00,
            // 0153 Extra Length          0000
            (byte)0x00, (byte)0x00,
            // 0155 Comment Length        0000
            (byte)0x00, (byte)0x00,
            // 0157 Disk Start            0000
            (byte)0x00, (byte)0x00,
            // 0159 Int File Attributes   0000
            //      [Bit 0]               0 'Binary Data'
            (byte)0x00, (byte)0x00,
            // 015B Ext File Attributes   11800000
            (byte)0x00, (byte)0x00, (byte)0x80, (byte)0x11,
            // 015F Local Header Offset   000000B5
            (byte)0xb5, (byte)0x00, (byte)0x00, (byte)0x00,
            // 0163 Filename              '-'
            (byte)0x2d,
        };
        byte[] zip64_end_central_dir_record = {
            // 0164 ZIP64 END CENTRAL DIR 06064B50
            //      RECORD
            (byte)0x50, (byte)0x4b, (byte)0x06, (byte)0x06,
            // 0168 Size of record        000000000000002C
            (byte)0x2c, (byte)0x00, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
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
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
        };
        byte[] zip64_end_central_dir_locator = {
            // 019C ZIP64 END CENTRAL DIR 07064B50
            //      LOCATOR
            (byte)0x50, (byte)0x4b, (byte)0x06, (byte)0x07,
            // 01A0 Central Dir Disk no   00000000
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01A4 Offset to Central dir 0000000000000164
            (byte)0x64, (byte)0x01, (byte)0x00, (byte)0x00,
            (byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00,
            // 01AC Total no of Disks     00000001
            (byte)0x01, (byte)0x00, (byte)0x00, (byte)0x00,
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
            (byte)0x00, (byte)0x00,
            // Done
        };
        File f = new File(ZIP_PATH.toString());
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(local_header_1);
        fos.write(class_bytes);
        fos.write(local_header_2);
        fos.write(central_header_1);
        fos.write(central_header_2);
        fos.write(zip64_end_central_dir_record);
        fos.write(zip64_end_central_dir_locator);
        fos.write(end_central_header);
        fos.close();
        byte[] zip_bytes = Files.readAllBytes(ZIP_PATH);
        ensureMatch(zip_sha512_digest, zip_bytes, ZIP_PATH.toString());
    }

    private static void createTestZip () throws IOException,
                                                NoSuchAlgorithmException {
        try {
            // Delete any existing class file.
            Files.deleteIfExists(CLASS_FILE_PATH);
            // Create class file.
            File f = new File(CLASS_FILE_PATH.toString());
            FileOutputStream fos = new FileOutputStream(f, false);
            fos.write(class_bytes);
            fos.close();
            // Verify class file is byte-for-byte as-expected.
            byte[] check_class_bytes = Files.readAllBytes(CLASS_FILE_PATH);
            ensureMatch(class_sha512_digest, check_class_bytes,
                        CLASS_FILE_PATH.toString());
            // Set class file time attributes so ZIP file entry times are
            // reproducible.
            BasicFileAttributeView attributes = Files
                .getFileAttributeView(CLASS_FILE_PATH,
                                      BasicFileAttributeView.class);
            FileTime time = FileTime
                .fromMillis(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
                            .parse("2024-07-16T22:04:38+00:00").getTime());
            attributes.setTimes(time, time, time);
            // Delete any existing ZIP file.
            Files.deleteIfExists(ZIP_PATH);
            //
            Process zip = new ProcessBuilder("zip",
                                             "--compression-method",
                                             "store",
                                             ZIP_PATH.toString(),
                                             CLASS_FILE,
                                             "-").start();
            OutputStream os = zip.getOutputStream();
            os.write(new byte[0]);
            os.close();
            zip.waitFor();
            // Log "zip" output.
            System.out.println("zip" +
                               " --compression-method" +
                               " store" +
                               " " + ZIP_PATH.toString() +
                               " " + CLASS_FILE +
                               " -");
            InputStreamReader i = new InputStreamReader(zip.getInputStream());
            BufferedReader r = new BufferedReader(i);
            r.lines().forEach(System.out::println);
            if (zip.exitValue() != 0) {
                i = new InputStreamReader(zip.getErrorStream());
                r = new BufferedReader(i);
                r.lines().forEach(System.out::println);
                throw new RuntimeException("zip exited with status "
                                           + zip.exitValue());
            }
            if (!Files.exists(ZIP_PATH)) {
                throw new RuntimeException(ZIP_PATH + " does not exist");
            }
            // Hack standard input ('-') "Last Mod Time" fields for
            // reproducibility.
            byte[] zip_bytes = Files.readAllBytes(ZIP_PATH);
            // 00B5 LOCAL HEADER #2    04034B50 [...]
            // 00BF Last Mod Time      58F77CBC 'Tue Jul 23 11:37:56 2024' [...]
            // 00D3 Filename           '-' [...]
            // 0135 CENTRAL HEADER #2  02014B50 [...]
            // 0141 Last Mod Time      58F77CBC 'Tue Jul 23 11:37:56 2024' [...]
            // 0163 Filename           '-'
            zip_bytes[0xbf] = zip_bytes[0x141] = (byte)0xbc;
            zip_bytes[0xc0] = zip_bytes[0x142] = (byte)0x7c;
            zip_bytes[0xc1] = zip_bytes[0x143] = (byte)0xf7;
            zip_bytes[0xc2] = zip_bytes[0x144] = (byte)0x58;
            // Overwrite ZIP file timestamps.
            f = new File(ZIP_PATH.toString());
            fos = new FileOutputStream(f, false);
            fos.write(zip_bytes);
            fos.close();
            // Verify that new ZIP file is byte-for-byte as-expected.
            zip_bytes = Files.readAllBytes(ZIP_PATH);
            ensureMatch(zip_sha512_digest, zip_bytes, ZIP_PATH.toString());
        } catch (Exception e) {
            System.out.println("Dumping inline ZIP file" +
                               "; recreation unsuccessful due to: " +
                               e.getMessage());
            dumpTestZip();
            System.out.println("Successfully dumped inline ZIP file");
        } finally {
            // Delete class file so BootClassPathZip64Test does not find it.
            Files.deleteIfExists(CLASS_FILE_PATH);
        }
    }

    private static void tearDown() throws IOException {
        Files.deleteIfExists(ZIP_PATH);
    }

    private static void setUp() throws IOException, InterruptedException,
                                       NoSuchAlgorithmException {
        tearDown();
        createTestZip();
    }

    public static void main(String[] args) throws Exception {
        setUp();
    }

}
