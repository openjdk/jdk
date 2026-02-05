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
 * size but missing the expected header fields.
 * @run junit MissingZIP64EntriesTest
 */
public class MissingZIP64EntriesTest {

    /*
     * Byte array representing a ZIP file which contains a
     * Zip64 Extra Header with only the size field.
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
     *               ->ZIP64: size *0x14
     *           [data= 14 00 00 00 00 00 00 00 ]
     *    0x6d: Comment Length   :        0x0
     *    0x6f: Disk Start       :        0x0
     *    0x71: Attrs            :        0x0
     *    0x73: AttrsEx          :        0x0
     *    0x77: Loc Header Offset:        0x0
     *    0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_ZIP64_EXTRAHDR_SIZE_ONLY_BYTEARRAY = {
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
     * Byte array representing a ZIP file which contains a
     * Zip64 Extra Header with only the LOC offset field.
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x4d: Signature        : 0x02014b50
     *       0x51: Created Zip Spec :       0x2d [4.5]
     *       0x52: Created OS       :        0x0 [MS-DOS]
     *       0x53: VerMadeby        :       0x2d [0, 4.5]
     *       0x54: VerExtract       :       0x2d [4.5]
     *       0x55: Flag             :      0x808
     *       0x57: Method           :        0x8 [DEFLATED]
     *       0x59: Last Mod Time    : 0x572d69c5 [Wed Sep 13 13:14:10 EDT 2023]
     *       0x5d: CRC              : 0x57de98d2
     *       0x61: Compressed Size  :       0x16
     *       0x65: Uncompressed Size:       0x14
     *       0x69: Name Length      :        0x9
     *       0x6b: Extra Length     :        0xc
     *       Extra data:[01, 00, 08, 00, 00, 00, 00, 00, 00, 00, 00, 00]
     *             [tag=0x0001, sz=8]
     *           ->ZIP64: LOC Off *0x0
     *          [data= 00 00 00 00 00 00 00 00 ]
     *       0x6d: Comment Length   :        0x0
     *       0x6f: Disk Start       :        0x0
     *       0x71: Attrs            :        0x0
     *       0x73: AttrsEx          :        0x0
     *       0x77: Loc Header Offset: 0xffffffff
     *       0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_ZIP64_EXTRAHDR_LOC_ONLY_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0xc5, (byte) 0x69, (byte) 0x2d, (byte) 0x57, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x8, (byte) 0x49, (byte) 0xcd,
            (byte) 0xcb, (byte) 0xcb, (byte) 0x2c, (byte) 0x56, (byte) 0x8, (byte) 0xc8, (byte) 0x49, (byte) 0xac,
            (byte) 0x4c, (byte) 0x2d, (byte) 0x2a, (byte) 0x6, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xd2, (byte) 0x98, (byte) 0xde, (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0xc5, (byte) 0x69, (byte) 0x2d, (byte) 0x57, (byte) 0xd2, (byte) 0x98, (byte) 0xde,
            (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x8, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x43, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a ZIP file which contains a
     * Zip64 Extra Header with only the compressed size field.
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x4d: Signature        : 0x02014b50
     *       0x51: Created Zip Spec :       0x2d [4.5]
     *       0x52: Created OS       :        0x0 [MS-DOS]
     *       0x53: VerMadeby        :       0x2d [0, 4.5]
     *       0x54: VerExtract       :       0x2d [4.5]
     *       0x55: Flag             :      0x808
     *       0x57: Method           :        0x8 [DEFLATED]
     *       0x59: Last Mod Time    : 0x572d6960 [Wed Sep 13 13:11:00 EDT 2023]
     *       0x5d: CRC              : 0x57de98d2
     *       0x61: Compressed Size  : 0xffffffff
     *       0x65: Uncompressed Size:       0x14
     *       0x69: Name Length      :        0x9
     *       0x6b: Extra Length     :        0xc
     *          Extra data:[01, 00, 08, 00, 16, 00, 00, 00, 00, 00, 00, 00]
     *       [tag=0x0001, sz=8]
     *          ->ZIP64: csize *0x16
     *          [data= 16 00 00 00 00 00 00 00 ]
     *       0x6d: Comment Length   :        0x0
     *       0x6f: Disk Start       :        0x0
     *       0x71: Attrs            :        0x0
     *       0x73: AttrsEx          :        0x0
     *       0x77: Loc Header Offset:        0x0
     *       0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_ZIP64_EXTRAHDR_CSIZE_ONLY_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x60, (byte) 0x69, (byte) 0x2d, (byte) 0x57, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x8, (byte) 0x49, (byte) 0xcd,
            (byte) 0xcb, (byte) 0xcb, (byte) 0x2c, (byte) 0x56, (byte) 0x8, (byte) 0xc8, (byte) 0x49, (byte) 0xac,
            (byte) 0x4c, (byte) 0x2d, (byte) 0x2a, (byte) 0x6, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xd2, (byte) 0x98, (byte) 0xde, (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x60, (byte) 0x69, (byte) 0x2d, (byte) 0x57, (byte) 0xd2, (byte) 0x98, (byte) 0xde,
            (byte) 0x57, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x14, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x8, (byte) 0x0,
            (byte) 0x16, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x43, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a Zip file with a zero length ZIP64 Extra Header
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x43: Signature        : 0x02014b50
     *       0x47: Created Zip Spec :       0x2d [4.5]
     *       0x48: Created OS       :        0x3 [UNIX]
     *       0x49: VerMadeby        :      0x32d [3, 4.5]
     *       0x4a: VerExtract       :       0x2d [4.5]
     *       0x4b: Flag             :      0x800
     *       0x4d: Method           :        0x8 [DEFLATED]
     *       0x4f: Last Mod Time    : 0x572c3477 [Tue Sep 12 06:35:46 EDT 2023]
     *       0x53: CRC              : 0x31963516
     *       0x57: Compressed Size  :        0x8
     *       0x5b: Uncompressed Size:        0x6
     *       0x5f: Name Length      :        0x9
     *       0x61: Extra Length     :        0x4
     *         Extra data:[01, 00, 00, 00]
     *         [tag=0x0001, sz=0]
     *           ->ZIP64:
     *       0x63: Comment Length   :        0x0
     *       0x65: Disk Start       :        0x0
     *       0x67: Attrs            :        0x0
     *       0x69: AttrsEx          : 0x81a40000
     *       0x6d: Loc Header Offset:        0x0
     *       0x71: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_ZEROLEN_ZIP64_EXTRAHDR_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x2d, (byte) 0x0, (byte) 0x0, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x77, (byte) 0x34, (byte) 0x2c, (byte) 0x57, (byte) 0x16, (byte) 0x35,
            (byte) 0x96, (byte) 0x31, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0x9, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1,
            (byte) 0x0, (byte) 0x10, (byte) 0x0, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x8, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xf3, (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9,
            (byte) 0xe7, (byte) 0x2, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x2d,
            (byte) 0x3, (byte) 0x2d, (byte) 0x0, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x77,
            (byte) 0x34, (byte) 0x2c, (byte) 0x57, (byte) 0x16, (byte) 0x35, (byte) 0x96, (byte) 0x31, (byte) 0x8,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9,
            (byte) 0x0, (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xa4, (byte) 0x81, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74,
            (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x6, (byte) 0x6, (byte) 0x2c, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x3b, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x43, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x6, (byte) 0x7, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x7e, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x3b, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x43, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a ZIP file which contains a
     * Zip64 Extra Header with the size and csize fields.
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x4d: Signature        : 0x02014b50
     *       0x51: Created Zip Spec :       0x2d [4.5]
     *       0x52: Created OS       :        0x0 [MS-DOS]
     *       0x53: VerMadeby        :       0x2d [0, 4.5]
     *       0x54: VerExtract       :       0x2d [4.5]
     *       0x55: Flag             :      0x808
     *       0x57: Method           :        0x8 [DEFLATED]
     *       0x59: Last Mod Time    : 0x572c6445 [Tue Sep 12 12:34:10 EDT 2023]
     *       0x5d: CRC              : 0x57de98d2
     *       0x61: Compressed Size  : 0xffffffff
     *       0x65: Uncompressed Size: 0xffffffff
     *       0x69: Name Length      :        0x9
     *       0x6b: Extra Length     :       0x14
     *          Extra data:[01, 00, 10, 00, 14, 00, 00, 00, 00, 00, 00, 00, 16, 00, 00, 00, 00, 00, 00, 00]
     *       [tag=0x0001, sz=16]
     *          ->ZIP64: size *0x14 csize *0x16
     *          [data= 14 00 00 00 00 00 00 00 16 00 00 00 00 00 00 00 ]
     *       0x6d: Comment Length   :        0x0
     *       0x6f: Disk Start       :        0x0
     *       0x71: Attrs            :        0x0
     *       0x73: AttrsEx          :        0x0
     *       0x77: Loc Header Offset:        0x0
     *       0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_TWO_ZIP64_HEADER_ENTRIES_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x45, (byte) 0x64, (byte) 0x2c, (byte) 0x57, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x8, (byte) 0x49, (byte) 0xcd,
            (byte) 0xcb, (byte) 0xcb, (byte) 0x2c, (byte) 0x56, (byte) 0x8, (byte) 0xc8, (byte) 0x49, (byte) 0xac,
            (byte) 0x4c, (byte) 0x2d, (byte) 0x2a, (byte) 0x6, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xd2, (byte) 0x98, (byte) 0xde, (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x45, (byte) 0x64, (byte) 0x2c, (byte) 0x57, (byte) 0xd2, (byte) 0x98, (byte) 0xde,
            (byte) 0x57, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0x9, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x10, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x16, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x4b, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a ZIP file which contains a
     * Zip64 Extra Header with the size,csize, and LOC offset fields.
     * ----------------#1--------------------
     * [Central Directory Header]
     *       0x4d: Signature        : 0x02014b50
     *       0x51: Created Zip Spec :       0x2d [4.5]
     *       0x52: Created OS       :        0x0 [MS-DOS]
     *       0x53: VerMadeby        :       0x2d [0, 4.5]
     *       0x54: VerExtract       :       0x2d [4.5]
     *       0x55: Flag             :      0x808
     *       0x57: Method           :        0x8 [DEFLATED]
     *       0x59: Last Mod Time    : 0x572d7214 [Wed Sep 13 14:16:40 EDT 2023]
     *       0x5d: CRC              : 0x57de98d2
     *       0x61: Compressed Size  : 0xffffffff
     *       0x65: Uncompressed Size: 0xffffffff
     *       0x69: Name Length      :        0x9
     *       0x6b: Extra Length     :       0x1c
     *          Extra data:[01, 00, 18, 00, 14, 00, 00, 00, 00, 00, 00, 00, 16, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00, 00]
     *        [tag=0x0001, sz=24]
     *         ->ZIP64: size *0x14 csize *0x16 LOC Off *0x0
     *         [data= 14 00 00 00 00 00 00 00 16 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 ]
     *       0x6d: Comment Length   :        0x0
     *       0x6f: Disk Start       :        0x0
     *       0x71: Attrs            :        0x0
     *       0x73: AttrsEx          :        0x0
     *       0x77: Loc Header Offset: 0xffffffff
     *       0x7b: File Name        : Hello.txt
     */
    public static byte[] ZIP_WITH_ZIP64_EXTRAHDR_ALL_BYTEARRAY = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x14, (byte) 0x72, (byte) 0x2d, (byte) 0x57, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0xf3,
            (byte) 0x48, (byte) 0xcd, (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x8, (byte) 0x49, (byte) 0xcd,
            (byte) 0xcb, (byte) 0xcb, (byte) 0x2c, (byte) 0x56, (byte) 0x8, (byte) 0xc8, (byte) 0x49, (byte) 0xac,
            (byte) 0x4c, (byte) 0x2d, (byte) 0x2a, (byte) 0x6, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xd2, (byte) 0x98, (byte) 0xde, (byte) 0x57, (byte) 0x16, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x2d, (byte) 0x0, (byte) 0x2d, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x14, (byte) 0x72, (byte) 0x2d, (byte) 0x57, (byte) 0xd2, (byte) 0x98, (byte) 0xde,
            (byte) 0x57, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff,
            (byte) 0xff, (byte) 0x9, (byte) 0x0, (byte) 0x1c, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xff,
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c, (byte) 0x6f,
            (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74, (byte) 0x1, (byte) 0x0, (byte) 0x18, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x16, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x1, (byte) 0x0, (byte) 0x1, (byte) 0x0, (byte) 0x53, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
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
     private static final String INVALID_EXTRA_LENGTH =
     "Invalid CEN header (invalid zip64 extra len size)";

     private static final String INVALID_ZIP64_EXTRAHDR_SIZE =
             "Invalid CEN header (invalid zip64 extra data field size)";

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
    private static final int ZIP64_MAGICCOUNT = 0xFFFF;

    /**
     * Copy of the byte array for the ZIP to be modified by a given test run
     */
    private byte[] zipArrayCopy;

    /**
     * Little-endian ByteBuffer for manipulating the ZIP copy
     */
    private ByteBuffer buffer;

    /**
     * The MethodSource returning a byte array representing the Zip file,
     * CEN offsets to set to 0xFFFFFFFF and the expected
     * ZipException error message when there are missing Zip64 Extra header fields
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> InvalidZip64MagicValues() {
        return Stream.of(
                // Byte array representing the Zip file, compressed size offset,
                // and expected ZipException Message
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_SIZE_ONLY_BYTEARRAY,
                        0x61, INVALID_ZIP64_EXTRAHDR_SIZE),
                // Byte array representing the Zip file, LOC offset and expected ZipException Message
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_SIZE_ONLY_BYTEARRAY,
                        0x77, INVALID_ZIP64_EXTRAHDR_SIZE),
                // Byte array representing the Zip file, LOC offset and expected ZipException Message
                Arguments.of(ZIP_WITH_TWO_ZIP64_HEADER_ENTRIES_BYTEARRAY,
                        0x77, INVALID_ZIP64_EXTRAHDR_SIZE)
        );
    }

    /**
     * The MethodSource of CEN offsets to set to 0xFFFFFFFF or 0xFFFF when the Extra Length
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
     * The MethodSource of CEN offsets to set to 0xFFFFFFFF when the ZIP64 extra header
     * Length size is 0 for the Zip file created using
     * ZIP_WITH_ZEROLEN_ZIP64_EXTRAHDR_BYTEARRAY
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> zip64ZeroLenHeaderExtraFieldEntries() {
        return Stream.of(
                // Compressed size offset
                Arguments.of(0x57),
                // Size offset
                Arguments.of(0x5b),
                // LOC offset
                Arguments.of(0x6d)
        );
    }

    /**
     * The MethodSource which will return a byte array representing a
     * valid Zip file and the expected content for the Zip file entry 'Hello.txt'.
     * @return Arguments used in each test run
     */
    private static Stream<Arguments> validZipFiles() {
        return Stream.of(
                // Byte array representing the Zip file, and the expected entry content
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_SIZE_ONLY_BYTEARRAY,
                        "Hello Tennis Players"),
                Arguments.of(ZIP_WITH_TWO_ZIP64_HEADER_ENTRIES_BYTEARRAY,
                        "Hello Tennis Players"),
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_LOC_ONLY_BYTEARRAY,
                        "Hello Tennis Players"),
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_CSIZE_ONLY_BYTEARRAY,
                        "Hello Tennis Players"),
                Arguments.of(ZIP_WITH_ZIP64_EXTRAHDR_ALL_BYTEARRAY,
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
     * @param zipArray Byte array representing the Zip file
     * @param offset Offset of the CEN Header field to set to 0xFFFFFFFF
     * @param errorMessage Expected ZipException error message
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("InvalidZip64MagicValues")
    public void invalidZip64ExtraHeaderZipFileTest(byte[] zipArray, int offset,
                                                   String errorMessage) throws IOException {
        // Set the CEN csize or LOC offset field to 0xFFFFFFFF.  There will not
        // be the expected Zip64 Extra Header field resulting in a ZipException
        // being thrown
        zipArrayCopy = zipArray.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            openWithZipFile(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(errorMessage),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that a ZipException is thrown by Zip FS if the Zip64 header
     * does not contain the required field
     * @param zipArray Byte array representing the Zip file
     * @param offset Offset of the CEN Header field to set to 0xFFFFFFFF
     * @param errorMessage Expected ZipException error message
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("InvalidZip64MagicValues")
    public void invalidZip64ExtraHeaderZipFSTest(byte[] zipArray, int offset,
                                                 String errorMessage) throws IOException {
        // Set the CEN csize or LOC offset field to 0xFFFFFFFF.  There will not
        // be the expected Zip64 Extra Header field resulting in a ZipException
        // being thrown
        zipArrayCopy = zipArray.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int)ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);

        ZipException ex = assertThrows(ZipException.class, () -> {
            openWithZipFS(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(errorMessage),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that ZipFile will throw a ZipException if the CEN
     * Extra length is 0 and the  CEN size, csize, LOC offset field is set to
     * 0xFFFFFFFF or the disk starting number is set to 0xFFFF
     * @param offset Offset of the CEN Header field to set to 0xFFFFFFFF or 0xFFFF
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
            openWithZipFile(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(INVALID_EXTRA_LENGTH),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that ZipFS will throw a ZipException if the CEN
     * Extra length is 0 and the CEN size, csize, LOC offset field is set to
     * 0xFFFFFFFF or the disk starting number is set to 0xFFFF
     * @param offset the offset of the CEN Header field to set to 0xFFFFFFFF or 0xFFFF
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
            openWithZipFS(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(INVALID_EXTRA_LENGTH),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that ZipFile will throw a ZipException if the ZIP64 extra header
     * has a size of 0 and the CEN size, csize, or the LOC offset field is set to
     * 0xFFFFFFFF
     * @param offset the offset of the CEN Header field to set to 0xFFFFFFFF
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("zip64ZeroLenHeaderExtraFieldEntries")
    public void zipFileZeroLenExtraHeader(int offset) throws IOException {
        zipArrayCopy = ZIP_WITH_ZEROLEN_ZIP64_EXTRAHDR_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);
        ZipException ex = assertThrows(ZipException.class, () -> {
            openWithZipFile(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(INVALID_ZIP64_EXTRAHDR_SIZE),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that ZipFS will throw a ZipException if the ZIP64 extra header
     * has a size of 0 and the CEN size, csize, or the LOC offset field is set to
     * 0xFFFFFFFF
     * @param offset the offset of the CEN Header field to set to 0xFFFFFFFF
     * @throws IOException if an error occurs
     */
    @ParameterizedTest
    @MethodSource("zip64ZeroLenHeaderExtraFieldEntries")
    public void zipFSZeroLenExtraHeader(int offset) throws IOException {
        zipArrayCopy = ZIP_WITH_ZEROLEN_ZIP64_EXTRAHDR_BYTEARRAY.clone();
        buffer = ByteBuffer.wrap(zipArrayCopy).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(offset, (int) ZIP64_MAGICVAL);
        Files.write(Path.of(BAD_ZIP_NAME), zipArrayCopy);
        ZipException ex = assertThrows(ZipException.class, () -> {
            openWithZipFS(BAD_ZIP_NAME, ZIP_FILE_ENTRY_NAME, null);
        });
        assertTrue(ex.getMessage().equals(INVALID_ZIP64_EXTRAHDR_SIZE),
                "Unexpected ZipException message: " + ex.getMessage());
    }

    /**
     * Verify that ZipFile will read the Zip files created from the
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
        openWithZipFile(ZIPFILE_NAME, ZIP_FILE_ENTRY_NAME, message);
    }

    /**
     * Verify that ZipFS will read the Zip files created from the
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
        openWithZipFS(ZIPFILE_NAME, ZIP_FILE_ENTRY_NAME, message);
    }

    /**
     * Utility method used to open a Zip file using ZipFile by the tests.
     * @param zipFile name of the Zip file to open
     * @param entryName Zip entry to read when the Zip file is expected to be
     *                  able to be opened
     * @param entryContents the expected contents for the Zip entry
     * @throws IOException if an error occurs
     */
    private static void openWithZipFile(String zipFile, String entryName,
                                        String entryContents) throws IOException {
        try (ZipFile zf = new ZipFile(zipFile)) {
            ZipEntry ze = zf.getEntry(entryName);
            try (InputStream is = zf.getInputStream(ze)) {
                String result = new String(is.readAllBytes());
                if (DEBUG) {
                    var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                    System.out.printf("Error: Zip File read :%s%n[%s]%n", result,
                            hx.formatHex(result.getBytes()));
                }
                // entryContents will be null when an exception is expected
                if (entryContents != null) {
                    assertEquals(entryContents, result);
                }
            }
        }
    }

    /**
     * Utility method used to open a Zip file using ZipFS by the tests.
     * @param zipFile name of the Zip file to open
     * @param entryName Zip entry to read when the Zip file is expected to be
     *                  able to be opened
     * @param entryContents the expected contents for the Zip entry
     * @throws IOException if an error occurs
     */
    private static void openWithZipFS(String zipFile, String entryName,
                                      String entryContents) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(
                Path.of(zipFile), Map.of())) {
            Path p = fs.getPath(entryName);
            String result = new String(Files.readAllBytes(p));
            if (DEBUG) {
                var hx = HexFormat.ofDelimiter(", ").withPrefix("0x");
                System.out.printf("Error: Zip FS read :%s%n[%s]%n", result,
                        hx.formatHex(result.getBytes()));
            }
            // entryContents will be null when an exception is expected
            if (entryContents != null) {
                assertEquals(entryContents, result);
            }
        }
    }
}
