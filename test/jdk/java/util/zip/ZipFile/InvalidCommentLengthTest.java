/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @test
 * @bug 8280404
 * @summary Validate that Zip/JarFile will throw a ZipException when the CEN
 * comment length field contains an incorrect value
 * @run junit/othervm InvalidCommentLengthTest
 */
public class InvalidCommentLengthTest {

    // Name used to create a JAR with an invalid comment length
    public static final Path INVALID_CEN_COMMENT_LENGTH_JAR =
            Path.of("Invalid-CEN-Comment-Length.jar");
    // Name used to create a JAR with a valid comment length
    public static final Path VALID_CEN_COMMENT_LENGTH_JAR =
            Path.of("Valid-CEN-Comment-Length.jar");
    // Zip/Jar CEN file header entry that will be modified
    public static final String META_INF_MANIFEST_MF = "META-INF/MANIFEST.MF";
    // Expected ZipException message when the comment length corrupts the
    // Zip/Jar file
    public static final String INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT =
            "invalid CEN header (bad entry name or comment)";

    /**
     * Byte array representing a valid jar file prior modifying the comment length
     * entry in a CEN file header.
     * The "Valid-CEN-Comment-Length.jar" jar file was created via:
     * <pre>
     *     {@code
     *     jar cvf Valid-CEN-Comment-Length.jar Hello.txt Tennis.txt BruceWayne.txt
     *     added manifest
     *     adding: Hello.txt(in = 12) (out= 14)(deflated -16%)
     *     adding: Tennis.txt(in = 53) (out= 53)(deflated 0%)
     *     adding: BruceWayne.txt(in = 12) (out= 14)(deflated -16%)
     *     }
     * </pre>
     * Its contents are:
     * <pre>
     *     {@code
     *     jar tvf Valid-CEN-Comment-Length.jar
     *      0 Wed Mar 02 06:39:24 EST 2022 META-INF/
     *     66 Wed Mar 02 06:39:24 EST 2022 META-INF/MANIFEST.MF
     *     12 Wed Mar 02 06:39:06 EST 2022 Hello.txt
     *     53 Wed Mar 02 13:04:48 EST 2022 Tennis.txt
     *     12 Wed Mar 02 15:15:34 EST 2022 BruceWayne.txt
     *     }
     * </pre>
     * The ByteArray was created by:
     * <pre>
     *  {@code
     *     var jar = Files.readAllBytes("Valid-CEN-Comment-Length.jar");
     *     var validEntryName = createByteArray(fooJar,
     *           "VALID_ZIP_WITH_NO_COMMENTS_BYTES");
     *  }
     * </pre>
     */
    public static byte[] VALID_ZIP_WITH_NO_COMMENTS_BYTES = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0xec, (byte) 0x34, (byte) 0x62, (byte) 0x54, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x4, (byte) 0x0,
            (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d,
            (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0xfe,
            (byte) 0xca, (byte) 0x0, (byte) 0x0, (byte) 0x3, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0xec, (byte) 0x34, (byte) 0x62, (byte) 0x54,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41,
            (byte) 0x2d, (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f,
            (byte) 0x4d, (byte) 0x41, (byte) 0x4e, (byte) 0x49, (byte) 0x46,
            (byte) 0x45, (byte) 0x53, (byte) 0x54, (byte) 0x2e, (byte) 0x4d,
            (byte) 0x46, (byte) 0xf3, (byte) 0x4d, (byte) 0xcc, (byte) 0xcb,
            (byte) 0x4c, (byte) 0x4b, (byte) 0x2d, (byte) 0x2e, (byte) 0xd1,
            (byte) 0xd, (byte) 0x4b, (byte) 0x2d, (byte) 0x2a, (byte) 0xce,
            (byte) 0xcc, (byte) 0xcf, (byte) 0xb3, (byte) 0x52, (byte) 0x30,
            (byte) 0xd4, (byte) 0x33, (byte) 0xe0, (byte) 0xe5, (byte) 0x72,
            (byte) 0x2e, (byte) 0x4a, (byte) 0x4d, (byte) 0x2c, (byte) 0x49,
            (byte) 0x4d, (byte) 0xd1, (byte) 0x75, (byte) 0xaa, (byte) 0x4,
            (byte) 0xa, (byte) 0x98, (byte) 0xe8, (byte) 0x19, (byte) 0xe8,
            (byte) 0x19, (byte) 0x2a, (byte) 0x68, (byte) 0xf8, (byte) 0x17,
            (byte) 0x25, (byte) 0x26, (byte) 0xe7, (byte) 0xa4, (byte) 0x2a,
            (byte) 0x38, (byte) 0xe7, (byte) 0x17, (byte) 0x15, (byte) 0xe4,
            (byte) 0x17, (byte) 0x25, (byte) 0x96, (byte) 0x0, (byte) 0x15,
            (byte) 0x6b, (byte) 0xf2, (byte) 0x72, (byte) 0xf1, (byte) 0x72,
            (byte) 0x1, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0xf4, (byte) 0x59, (byte) 0xdc, (byte) 0xa6,
            (byte) 0x42, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x42,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8,
            (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0xe3, (byte) 0x34,
            (byte) 0x62, (byte) 0x54, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x65,
            (byte) 0x6c, (byte) 0x6c, (byte) 0x6f, (byte) 0x2e, (byte) 0x74,
            (byte) 0x78, (byte) 0x74, (byte) 0xf3, (byte) 0x48, (byte) 0xcd,
            (byte) 0xc9, (byte) 0xc9, (byte) 0x57, (byte) 0x28, (byte) 0xcf,
            (byte) 0x2f, (byte) 0xca, (byte) 0x49, (byte) 0xe1, (byte) 0x2,
            (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8,
            (byte) 0xd5, (byte) 0xe0, (byte) 0x39, (byte) 0xb7, (byte) 0xe,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xc, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3,
            (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x98, (byte) 0x68, (byte) 0x62,
            (byte) 0x54, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xa, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x54, (byte) 0x65, (byte) 0x6e,
            (byte) 0x6e, (byte) 0x69, (byte) 0x73, (byte) 0x2e, (byte) 0x74,
            (byte) 0x78, (byte) 0x74, (byte) 0x73, (byte) 0xf2, (byte) 0xb,
            (byte) 0x50, (byte) 0x8, (byte) 0x48, (byte) 0x2c, (byte) 0xca,
            (byte) 0x4c, (byte) 0x4a, (byte) 0x2c, (byte) 0x56, (byte) 0xf0,
            (byte) 0x2f, (byte) 0x48, (byte) 0xcd, (byte) 0x53, (byte) 0xc8,
            (byte) 0x2c, (byte) 0x56, (byte) 0x48, (byte) 0x54, (byte) 0x48,
            (byte) 0x2b, (byte) 0xcd, (byte) 0x53, (byte) 0x8, (byte) 0x49,
            (byte) 0xcd, (byte) 0xcb, (byte) 0x3, (byte) 0x72, (byte) 0x42,
            (byte) 0xf2, (byte) 0x4b, (byte) 0x8b, (byte) 0xf2, (byte) 0x12,
            (byte) 0x73, (byte) 0x53, (byte) 0xf3, (byte) 0x4a, (byte) 0x14,
            (byte) 0x4a, (byte) 0xf2, (byte) 0x15, (byte) 0xca, (byte) 0x13,
            (byte) 0x4b, (byte) 0x92, (byte) 0x33, (byte) 0xb8, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8, (byte) 0xaa,
            (byte) 0xad, (byte) 0x14, (byte) 0xd, (byte) 0x35, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x35, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0xf1, (byte) 0x79, (byte) 0x62, (byte) 0x54,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0xe, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x42, (byte) 0x72, (byte) 0x75, (byte) 0x63,
            (byte) 0x65, (byte) 0x57, (byte) 0x61, (byte) 0x79, (byte) 0x6e,
            (byte) 0x65, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74,
            (byte) 0xf3, (byte) 0x54, (byte) 0x48, (byte) 0xcc, (byte) 0x55,
            (byte) 0x70, (byte) 0x4a, (byte) 0x2c, (byte) 0xc9, (byte) 0x4d,
            (byte) 0xcc, (byte) 0xe3, (byte) 0x2, (byte) 0x0, (byte) 0x50,
            (byte) 0x4b, (byte) 0x7, (byte) 0x8, (byte) 0x6c, (byte) 0x70,
            (byte) 0x60, (byte) 0xbd, (byte) 0xe, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0xec, (byte) 0x34, (byte) 0x62,
            (byte) 0x54, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x2, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0,
            (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41,
            (byte) 0x2d, (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f,
            (byte) 0xfe, (byte) 0xca, (byte) 0x0, (byte) 0x0, (byte) 0x50,
            (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0xec, (byte) 0x34, (byte) 0x62, (byte) 0x54,
            (byte) 0xf4, (byte) 0x59, (byte) 0xdc, (byte) 0xa6, (byte) 0x42,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x42, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x3d, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d,
            (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d,
            (byte) 0x41, (byte) 0x4e, (byte) 0x49, (byte) 0x46, (byte) 0x45,
            (byte) 0x53, (byte) 0x54, (byte) 0x2e, (byte) 0x4d, (byte) 0x46,
            (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0xe3, (byte) 0x34, (byte) 0x62,
            (byte) 0x54, (byte) 0xd5, (byte) 0xe0, (byte) 0x39, (byte) 0xb7,
            (byte) 0xe, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xc,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0xc1, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x48, (byte) 0x65, (byte) 0x6c, (byte) 0x6c,
            (byte) 0x6f, (byte) 0x2e, (byte) 0x74, (byte) 0x78, (byte) 0x74,
            (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x98, (byte) 0x68, (byte) 0x62,
            (byte) 0x54, (byte) 0xaa, (byte) 0xad, (byte) 0x14, (byte) 0xd,
            (byte) 0x35, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x35,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xa, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x6, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x54, (byte) 0x65, (byte) 0x6e, (byte) 0x6e,
            (byte) 0x69, (byte) 0x73, (byte) 0x2e, (byte) 0x74, (byte) 0x78,
            (byte) 0x74, (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2,
            (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8,
            (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0xf1, (byte) 0x79,
            (byte) 0x62, (byte) 0x54, (byte) 0x6c, (byte) 0x70, (byte) 0x60,
            (byte) 0xbd, (byte) 0xe, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0xc, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xe,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x73, (byte) 0x1,
            (byte) 0x0, (byte) 0x0, (byte) 0x42, (byte) 0x72, (byte) 0x75,
            (byte) 0x63, (byte) 0x65, (byte) 0x57, (byte) 0x61, (byte) 0x79,
            (byte) 0x6e, (byte) 0x65, (byte) 0x2e, (byte) 0x74, (byte) 0x78,
            (byte) 0x74, (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x5,
            (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x28, (byte) 0x1,
            (byte) 0x0, (byte) 0x0, (byte) 0xbd, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Create Jar files used by the tests.
     * The {@code byte} array {@code VALID_ZIP_WITH_NO_COMMENTS_BYTES} is written
     * to disk to create the jar file: {@code Valid-CEN-Comment-Length.jar}.
     *
     * The jar file {@code InValid-CEN-Comment-Length.jar} is created by copying
     * the {@code byte} array {@code VALID_ZIP_WITH_NO_COMMENTS_BYTES} and modifying
     * the CEN file header comment length entry for "META-INF/MANIFEST.MF" so that
     * new comment length will forward the CEN to a subsequent CEN file header
     * entry.
     *
     * For {@code InValid-CEN-Comment-Length.jar}, the comment length is changed
     * from {@code 0x0} to the {@code 0x37}.
     *
     * @throws IOException If an error occurs
     */
    @BeforeAll
    public static void setup() throws IOException {
        Files.deleteIfExists(VALID_CEN_COMMENT_LENGTH_JAR);
        Files.deleteIfExists(INVALID_CEN_COMMENT_LENGTH_JAR);
        // Create the valid jar
        Files.write(VALID_CEN_COMMENT_LENGTH_JAR, VALID_ZIP_WITH_NO_COMMENTS_BYTES);
        // Now create an invalid jar
        byte[] invalid_bytes = Arrays.copyOf(VALID_ZIP_WITH_NO_COMMENTS_BYTES,
                VALID_ZIP_WITH_NO_COMMENTS_BYTES.length);
        // Change CEN file Header comment length so that the length will
        // result in the offset pointing to a subsequent CEN file header
        // resulting in an invalid comment
        invalid_bytes[536] = 55;
        Files.write(INVALID_CEN_COMMENT_LENGTH_JAR, invalid_bytes);
    }

    /**
     * Clean up after the test run
     *
     * @throws IOException If an error occurs
     */
    @AfterAll
    public static void cleanup() throws IOException {
        Files.deleteIfExists(VALID_CEN_COMMENT_LENGTH_JAR);
        Files.deleteIfExists(INVALID_CEN_COMMENT_LENGTH_JAR);
    }

    /**
     * Validate that the original(valid) Jar file can be opened by {@code ZipFile}
     * and the expected Zip entry can be found
     * @throws IOException If an error occurs
     */
    @Test
    public void ZipFileValidCommentLengthTest() throws IOException {
        try (ZipFile jf = new ZipFile(VALID_CEN_COMMENT_LENGTH_JAR.toFile())) {
            ZipEntry ze = jf.getEntry(META_INF_MANIFEST_MF);
            assertNotNull(ze);
            assertEquals(META_INF_MANIFEST_MF, ze.getName());
        }
    }

    /**
     * Validate that the original(valid) Jar file can be opened by {@code JarFile}
     * and the expected Zip entry can be found
     * @throws IOException If an error occurs
     */
    @Test
    public void JarFileValidCommentLengthTest() throws IOException {
        try (JarFile jf = new JarFile(VALID_CEN_COMMENT_LENGTH_JAR.toFile())) {
            ZipEntry ze = jf.getEntry(META_INF_MANIFEST_MF);
            assertNotNull(ze);
            assertEquals(META_INF_MANIFEST_MF, ze.getName());
        }
    }

    /**
     * Validate that a ZipException is thrown when the CEN file header comment
     * length is non-zero and the CEN entry does not contain a comment when
     * the Jar file is opened by {@code ZipFile}
     */
    @Test
    public void ZipFileInValidCommentLengthTest() {
        var ex= assertThrows(ZipException.class,
                () -> new ZipFile(INVALID_CEN_COMMENT_LENGTH_JAR.toFile()));
        assertEquals(INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT, ex.getMessage());
    }

    /**
     * Validate that a ZipException is thrown when the CEN file header comment
     * length is non-zero and the CEN entry does not contain a comment when
     * the Jar file is opened by  {@code JarFile}
     */
    @Test
    public void JarFileInValidCommentLengthTest() {
        var ex= assertThrows(ZipException.class,
                () -> new JarFile(INVALID_CEN_COMMENT_LENGTH_JAR.toFile()));
        assertEquals(INVALID_CEN_HEADER_BAD_ENTRY_NAME_OR_COMMENT, ex.getMessage());
    }
}
