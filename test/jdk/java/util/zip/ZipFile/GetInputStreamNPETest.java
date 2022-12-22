/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Formatter;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.testng.Assert.*;

/**
 * @test
 * @bug 8280409
 * @summary Validate that Zip/JarFile::getInputStream will throw a NullPointerException
 * @run testng/othervm GetInputStreamNPETest
 */
public class GetInputStreamNPETest {
    // Name used to create a JAR with an invalid entry name
    public static final Path INVALID_ENTRY_NAME_JAR =
            Path.of("Invalid-EntryName.jar");
    // Name used to create a JAR with a valid entry name
    public static final Path VALID_ENTRY_NAME_JAR =
            Path.of("Valid-EntryName.jar");
    // Name used to create a JAR with an invalid entry name
    public static final Path SIGNED_INVALID_ENTRY_NAME_JAR =
            Path.of("Signed-Invalid-EntryName.jar");
    // Name used to create a JAR with a valid entry name
    public static final Path SIGNED_VALID_ENTRY_NAME_JAR =
            Path.of("Signed-Valid-EntryName.jar");
    // Value to change the "S" in "Singleton.class" to
    public static final byte INVALID_UTF8_BYTE = (byte) 0x13;
    // CEN offset to where "Singleton.class" filename starts
    public static final int SINGLETON_CEN_FILENAME_OFFSET = 37;
    // CEN filename which will be modified to validate a ZipException is thrown
    public static final String CEN_FILENAME_TO_MODIFY = "javax/inject/Singleton.class";
    // Zip Entry name that does not exist within the JarFile
    public static final String ZIP_ENTRY_THAT_DOES_NOT_EXIST = "org/gotham/Batcave.class";

    /**
     * Byte array representing a valid jar file prior modifying a filename in the
     * CEN.
     * The "Valid-EntryName.jar" jar file was created via:
     * <pre>
     *     {@code
     *        jar cvf Valid-EntryName.jar javax/inject/Singleton.class
     *        added manifest
     *        adding: javax/inject/Singleton.class(in = 359) (out= 221)(deflated 38%)
     *     }
     * </pre>
     * Its contents are:
     * <pre>
     *     {@code
     *        jar tvf Valid-EntryName.jar
     *         0 Wed Jan 26 14:27:26 EST 2022 META-INF/
     *        66 Wed Jan 26 14:27:26 EST 2022 META-INF/MANIFEST.MF
     *       359 Mon Jan 24 22:11:24 EST 2011 javax/inject/Singleton.class
     *     }
     * </pre>
     * The ByteArray was created by:
     * <pre>
     *  {@code
     *     var jar = Files.readAllBytes("Valid-EntryName.jar");
     *     var validEntryName = createByteArray(fooJar, "VALID_ENTRY_NAME");
     *  }
     * </pre>
     */
    public static byte[] VALID_ENTRY_NAME = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0x6d, (byte) 0x73, (byte) 0x3a, (byte) 0x54, (byte) 0x0,
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
            (byte) 0x0, (byte) 0x6d, (byte) 0x73, (byte) 0x3a, (byte) 0x54,
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
            (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x6c, (byte) 0xb1,
            (byte) 0x38, (byte) 0x3e, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1c,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6a, (byte) 0x61,
            (byte) 0x76, (byte) 0x61, (byte) 0x78, (byte) 0x2f, (byte) 0x69,
            (byte) 0x6e, (byte) 0x6a, (byte) 0x65, (byte) 0x63, (byte) 0x74,
            (byte) 0x2f, (byte) 0x53, (byte) 0x69, (byte) 0x6e, (byte) 0x67,
            (byte) 0x6c, (byte) 0x65, (byte) 0x74, (byte) 0x6f, (byte) 0x6e,
            (byte) 0x2e, (byte) 0x63, (byte) 0x6c, (byte) 0x61, (byte) 0x73,
            (byte) 0x73, (byte) 0x85, (byte) 0x90, (byte) 0x4d, (byte) 0x4b,
            (byte) 0x82, (byte) 0x41, (byte) 0x10, (byte) 0xc7, (byte) 0xff,
            (byte) 0xa3, (byte) 0xd9, (byte) 0x63, (byte) 0x56, (byte) 0xe6,
            (byte) 0x21, (byte) 0x5, (byte) 0x4f, (byte) 0xda, (byte) 0xc5,
            (byte) 0x63, (byte) 0x4b, (byte) 0xe7, (byte) 0x4e, (byte) 0x41,
            (byte) 0x6, (byte) 0x81, (byte) 0xa5, (byte) 0x3c, (byte) 0x5a,
            (byte) 0xf7, (byte) 0x75, (byte) 0x1b, (byte) 0x64, (byte) 0x65,
            (byte) 0xdd, (byte) 0x8d, (byte) 0xdc, (byte) 0x47, (byte) 0xea,
            (byte) 0xab, (byte) 0x79, (byte) 0xe8, (byte) 0x3, (byte) 0xf4,
            (byte) 0xa1, (byte) 0xc4, (byte) 0xd9, (byte) 0xe, (byte) 0x4a,
            (byte) 0x20, (byte) 0x34, (byte) 0x30, (byte) 0x2f, (byte) 0xcc,
            (byte) 0xfc, (byte) 0x66, (byte) 0x98, (byte) 0x99, (byte) 0x9f,
            (byte) 0xcd, (byte) 0xfa, (byte) 0x1b, (byte) 0xc0, (byte) 0xd,
            (byte) 0x1a, (byte) 0x84, (byte) 0x2c, (byte) 0x7f, (byte) 0x79,
            (byte) 0x9e, (byte) 0x3c, (byte) 0x3e, (byte) 0xf5, (byte) 0x9,
            (byte) 0x8d, (byte) 0xb9, (byte) 0x5e, (byte) 0x69, (byte) 0xe5,
            (byte) 0xb4, (byte) 0x9f, (byte) 0xa9, (byte) 0xe1, (byte) 0x74,
            (byte) 0xce, (byte) 0x26, (byte) 0x12, (byte) 0x3a, (byte) 0xfb,
            (byte) 0x94, (byte) 0xf6, (byte) 0x3e, (byte) 0x44, (byte) 0x1d,
            (byte) 0x6d, (byte) 0xf0, (byte) 0xea, (byte) 0x6e, (byte) 0x17,
            (byte) 0x12, (byte) 0x5a, (byte) 0x89, (byte) 0xf8, (byte) 0x54,
            (byte) 0xd6, (byte) 0xa7, (byte) 0x6, (byte) 0x35, (byte) 0xb6,
            (byte) 0x7e, (byte) 0xe6, (byte) 0x38, (byte) 0xa6, (byte) 0x42,
            (byte) 0x65, (byte) 0xa5, (byte) 0x5d, (byte) 0xc1, (byte) 0x19,
            (byte) 0x4a, (byte) 0x19, (byte) 0xca, (byte) 0x19, (byte) 0x8e,
            (byte) 0x8, (byte) 0x57, (byte) 0x83, (byte) 0x83, (byte) 0xc3,
            (byte) 0xee, (byte) 0x83, (byte) 0x29, (byte) 0x16, (byte) 0xec,
            (byte) 0x23, (byte) 0xbf, (byte) 0xdd, (byte) 0x12, (byte) 0xba,
            (byte) 0x87, (byte) 0x99, (byte) 0x9c, (byte) 0xa3, (byte) 0x10,
            (byte) 0x12, (byte) 0x9, (byte) 0xd2, (byte) 0xfb, (byte) 0x7,
            (byte) 0x19, (byte) 0x5, (byte) 0x67, (byte) 0xcd, (byte) 0x97,
            (byte) 0x80, (byte) 0x97, (byte) 0x83, (byte) 0xbf, (byte) 0xab,
            (byte) 0x99, (byte) 0xf0, (byte) 0xce, (byte) 0x92, (byte) 0x6e,
            (byte) 0xe7, (byte) 0x85, (byte) 0x70, (byte) 0xb, (byte) 0x7e,
            (byte) 0xb5, (byte) 0x4b, (byte) 0x3b, (byte) 0x75, (byte) 0xbc,
            (byte) 0xbf, (byte) 0x65, (byte) 0x49, (byte) 0xa8, (byte) 0xef,
            (byte) 0xf6, (byte) 0xbf, (byte) 0x4e, (byte) 0xbd, (byte) 0x84,
            (byte) 0xda, (byte) 0x38, (byte) 0x14, (byte) 0x1f, (byte) 0x86,
            (byte) 0x1f, (byte) 0xac, (byte) 0xe3, (byte) 0x1e, (byte) 0xa1,
            (byte) 0x8a, (byte) 0x63, (byte) 0xc8, (byte) 0xc3, (byte) 0x90,
            (byte) 0xa4, (byte) 0x84, (byte) 0x8b, (byte) 0x5f, (byte) 0x5b,
            (byte) 0xc7, (byte) 0xb9, (byte) 0xf8, (byte) 0x26, (byte) 0xca,
            (byte) 0x38, (byte) 0x13, (byte) 0x7f, (byte) 0x22, (byte) 0x5a,
            (byte) 0x13, (byte) 0xa6, (byte) 0xc2, (byte) 0x38, (byte) 0x5,
            (byte) 0x6d, (byte) 0x1, (byte) 0x50, (byte) 0x4b, (byte) 0x7,
            (byte) 0x8, (byte) 0x5a, (byte) 0xf1, (byte) 0x0, (byte) 0x98,
            (byte) 0xdd, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x67,
            (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0x6d, (byte) 0x73, (byte) 0x3a, (byte) 0x54, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x4, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d,
            (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49,
            (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0xfe, (byte) 0xca,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0,
            (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x6d,
            (byte) 0x73, (byte) 0x3a, (byte) 0x54, (byte) 0xf4, (byte) 0x59,
            (byte) 0xdc, (byte) 0xa6, (byte) 0x42, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x42, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x3d,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d, (byte) 0x45,
            (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49, (byte) 0x4e,
            (byte) 0x46, (byte) 0x2f, (byte) 0x4d, (byte) 0x41, (byte) 0x4e,
            (byte) 0x49, (byte) 0x46, (byte) 0x45, (byte) 0x53, (byte) 0x54,
            (byte) 0x2e, (byte) 0x4d, (byte) 0x46, (byte) 0x50, (byte) 0x4b,
            (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0x6c, (byte) 0xb1, (byte) 0x38, (byte) 0x3e, (byte) 0x5a,
            (byte) 0xf1, (byte) 0x0, (byte) 0x98, (byte) 0xdd, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x67, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x1c, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0xc1, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x6a,
            (byte) 0x61, (byte) 0x76, (byte) 0x61, (byte) 0x78, (byte) 0x2f,
            (byte) 0x69, (byte) 0x6e, (byte) 0x6a, (byte) 0x65, (byte) 0x63,
            // We will modify 0x53, "S" within the CEN filename entry
            (byte) 0x74, (byte) 0x2f, (byte) 0x53, (byte) 0x69, (byte) 0x6e,
            (byte) 0x67, (byte) 0x6c, (byte) 0x65, (byte) 0x74, (byte) 0x6f,
            (byte) 0x6e, (byte) 0x2e, (byte) 0x63, (byte) 0x6c, (byte) 0x61,
            (byte) 0x73, (byte) 0x73, (byte) 0x50, (byte) 0x4b, (byte) 0x5,
            (byte) 0x6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x3, (byte) 0x0, (byte) 0x3, (byte) 0x0, (byte) 0xc7,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xe8, (byte) 0x1,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
    };

    /**
     * Byte array representing a valid signed jar file prior modifying a filename
     * in the CEN.
     * The "Valid-EntryName.jar" jar file was signed via:
     * <pre>
     *     {@code
     *        keytool -genkey -keyalg RSA -alias myFirstKey -keystore myKeystore -storepass changeit -keypass changeit
     *        jarsigner -keystore myKeystore -verbose Valid-EntryName.jar -signedjar Signed-Valid-EntryName.jar myFirstKey
     *      }
     * </pre>
     * The ByteArray was created by:
     * <pre>
     *  {@code
     *     var signedJar = Files.readAllBytes("Signed-Valid-EntryName.jar");
     *     var signedValidEntryName = createByteArray(fooJar, "SIGNED_VALID_ENTRY_NAME");
     *  }
     * </pre>
     */
    public static byte[] SIGNED_VALID_ENTRY_NAME = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0x17, (byte) 0x71, (byte) 0x3b, (byte) 0x54, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d,
            (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d,
            (byte) 0x41, (byte) 0x4e, (byte) 0x49, (byte) 0x46, (byte) 0x45,
            (byte) 0x53, (byte) 0x54, (byte) 0x2e, (byte) 0x4d, (byte) 0x46,
            (byte) 0x15, (byte) 0xcd, (byte) 0xbb, (byte) 0xe, (byte) 0x82,
            (byte) 0x30, (byte) 0x18, (byte) 0x40, (byte) 0xe1, (byte) 0x9d,
            (byte) 0x84, (byte) 0x77, (byte) 0xe8, (byte) 0xa8, (byte) 0x43,
            (byte) 0xb, (byte) 0x28, (byte) 0x10, (byte) 0x42, (byte) 0xe2,
            (byte) 0xc0, (byte) 0x45, (byte) 0xc1, (byte) 0x41, (byte) 0x31,
            (byte) 0x21, (byte) 0x3a, (byte) 0xb8, (byte) 0x95, (byte) 0xfa,
            (byte) 0xb, (byte) 0xad, (byte) 0xd8, (byte) 0x6a, (byte) 0xa9,
            (byte) 0x17, (byte) 0xde, (byte) 0x5e, (byte) 0x5c, (byte) 0x4f,
            (byte) 0x4e, (byte) 0xf2, (byte) 0xed, (byte) 0xa8, (byte) 0xe4,
            (byte) 0x57, (byte) 0x18, (byte) 0xc, (byte) 0x3e, (byte) 0x81,
            (byte) 0x1e, (byte) 0xb8, (byte) 0x92, (byte) 0x31, (byte) 0xf2,
            (byte) 0x88, (byte) 0x6b, (byte) 0x5b, (byte) 0x99, (byte) 0x6,
            (byte) 0x6a, (byte) 0xe0, (byte) 0x82, (byte) 0xd3, (byte) 0x71,
            (byte) 0xa, (byte) 0x3e, (byte) 0x71, (byte) 0x89, (byte) 0x87,
            (byte) 0x66, (byte) 0x95, (byte) 0xa6, (byte) 0xac, (byte) 0x7,
            (byte) 0x94, (byte) 0x29, (byte) 0xfd, (byte) 0x50, (byte) 0x9a,
            (byte) 0x9a, (byte) 0x69, (byte) 0x9e, (byte) 0xdb, (byte) 0x96,
            (byte) 0x6d, (byte) 0xed, (byte) 0xe9, (byte) 0x1d, (byte) 0x62,
            (byte) 0x24, (byte) 0xe8, (byte) 0x9b, (byte) 0x7e, (byte) 0x1d,
            (byte) 0x2e, (byte) 0x5, (byte) 0x30, (byte) 0xe3, (byte) 0xd4,
            (byte) 0x5c, (byte) 0xb6, (byte) 0x3d, (byte) 0x18, (byte) 0x25,
            (byte) 0x9, (byte) 0xeb, (byte) 0xe9, (byte) 0x30, (byte) 0xd8,
            (byte) 0x56, (byte) 0x5d, (byte) 0x26, (byte) 0x78, (byte) 0x11,
            (byte) 0x84, (byte) 0x38, (byte) 0xe7, (byte) 0xed, (byte) 0x64,
            (byte) 0xc5, (byte) 0x48, (byte) 0xb9, (byte) 0x7, (byte) 0xf0,
            (byte) 0x97, (byte) 0x51, (byte) 0x1d, (byte) 0xe8, (byte) 0xcd,
            (byte) 0xb3, (byte) 0x39, (byte) 0x46, (byte) 0x1f, (byte) 0x91,
            (byte) 0xa4, (byte) 0x41, (byte) 0xb3, (byte) 0xbe, (byte) 0xf1,
            (byte) 0x17, (byte) 0x1b, (byte) 0x4b, (byte) 0xd1, (byte) 0x55,
            (byte) 0x45, (byte) 0x27, (byte) 0xf3, (byte) 0x73, (byte) 0xd1,
            (byte) 0x9, (byte) 0x8, (byte) 0xc3, (byte) 0xed, (byte) 0xea,
            (byte) 0x6f, (byte) 0xfc, (byte) 0x0, (byte) 0x50, (byte) 0x4b,
            (byte) 0x7, (byte) 0x8, (byte) 0xc1, (byte) 0xfa, (byte) 0x9d,
            (byte) 0xe2, (byte) 0x9e, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0xa6, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x50,
            (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0,
            (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x17,
            (byte) 0x71, (byte) 0x3b, (byte) 0x54, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d,
            (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49,
            (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d, (byte) 0x59,
            (byte) 0x46, (byte) 0x49, (byte) 0x52, (byte) 0x53, (byte) 0x54,
            (byte) 0x4b, (byte) 0x2e, (byte) 0x53, (byte) 0x46, (byte) 0x75,
            (byte) 0x8d, (byte) 0xcb, (byte) 0x6e, (byte) 0x82, (byte) 0x40,
            (byte) 0x18, (byte) 0x46, (byte) 0xf7, (byte) 0x24, (byte) 0xbc,
            (byte) 0xc3, (byte) 0x2c, (byte) 0xdb, (byte) 0x10, (byte) 0xee,
            (byte) 0xa8, (byte) 0x29, (byte) 0x49, (byte) 0x17, (byte) 0x23,
            (byte) 0x2a, (byte) 0x4a, (byte) 0x41, (byte) 0xdb, (byte) 0x62,
            (byte) 0x6d, (byte) 0xc2, (byte) 0x6e, (byte) 0x3a, (byte) 0x8c,
            (byte) 0x64, (byte) 0x44, (byte) 0x67, (byte) 0xe8, (byte) 0xcc,
            (byte) 0x2f, (byte) 0x5e, (byte) 0x9e, (byte) 0xbe, (byte) 0xa4,
            (byte) 0xcb, (byte) 0x26, (byte) 0xdd, (byte) 0x9d, (byte) 0x7c,
            (byte) 0xf9, (byte) 0x72, (byte) 0x4e, (byte) 0xc9, (byte) 0x1b,
            (byte) 0x41, (byte) 0xe0, (byte) 0xac, (byte) 0x98, (byte) 0xbd,
            (byte) 0x63, (byte) 0x4a, (byte) 0x73, (byte) 0x29, (byte) 0x62,
            (byte) 0xe4, (byte) 0x3b, (byte) 0x9e, (byte) 0x69, (byte) 0x24,
            (byte) 0x8a, (byte) 0x11, (byte) 0x60, (byte) 0xb5, (byte) 0x3d,
            (byte) 0xbd, (byte) 0xd, (byte) 0x43, (byte) 0xe4, (byte) 0x78,
            (byte) 0x8e, (byte) 0x8f, (byte) 0x1e, (byte) 0x36, (byte) 0x8a,
            (byte) 0xd0, (byte) 0x23, (byte) 0x43, (byte) 0x89, (byte) 0x54,
            (byte) 0x9d, (byte) 0x54, (byte) 0x4, (byte) 0x86, (byte) 0xf3,
            (byte) 0xa3, (byte) 0x69, (byte) 0x94, (byte) 0x4b, (byte) 0x6c,
            (byte) 0x7, (byte) 0xa3, (byte) 0xb1, (byte) 0x3d, (byte) 0xe3,
            (byte) 0xd, (byte) 0xd3, (byte) 0x60, (byte) 0x17, (byte) 0x44,
            (byte) 0xf0, (byte) 0xfd, (byte) 0x0, (byte) 0x31, (byte) 0xda,
            (byte) 0xeb, (byte) 0xd9, (byte) 0xc6, (byte) 0xad, (byte) 0x2c,
            (byte) 0xab, (byte) 0x29, (byte) 0xb2, (byte) 0x57, (byte) 0xbc,
            (byte) 0xfb, (byte) 0x8, (byte) 0x57, (byte) 0xa3, (byte) 0x2e,
            (byte) 0x97, (byte) 0x3d, (byte) 0xe0, (byte) 0x4d, (byte) 0x48,
            (byte) 0xb3, (byte) 0xa0, (byte) 0x4d, (byte) 0x73, (byte) 0xd7,
            (byte) 0xf3, (byte) 0x83, (byte) 0xa0, (byte) 0xbf, (byte) 0x15,
            (byte) 0x8b, (byte) 0xba, (byte) 0x78, (byte) 0xfe, (byte) 0x57,
            (byte) 0x33, (byte) 0x0, (byte) 0x17, (byte) 0x36, (byte) 0x6,
            (byte) 0x50, (byte) 0xfc, (byte) 0xeb, (byte) 0xc, (byte) 0x4c,
            (byte) 0xc7, (byte) 0x28, (byte) 0xa4, (byte) 0x55, (byte) 0xb6,
            (byte) 0x8e, (byte) 0x40, (byte) 0x94, (byte) 0xf7, (byte) 0x76,
            (byte) 0x4b, (byte) 0xd2, (byte) 0xc8, (byte) 0x67, (byte) 0xb,
            (byte) 0xa8, (byte) 0x93, (byte) 0xc9, (byte) 0x64, (byte) 0x3a,
            (byte) 0x29, (byte) 0x96, (byte) 0xe3, (byte) 0xd6, (byte) 0x6a,
            (byte) 0x4d, (byte) 0x3, (byte) 0x9d, (byte) 0xe7, (byte) 0xdd,
            (byte) 0xf5, (byte) 0x6e, (byte) 0x61, (byte) 0x39, (byte) 0xbf,
            (byte) 0xd6, (byte) 0x74, (byte) 0x30, (byte) 0x9b, (byte) 0xc6,
            (byte) 0x9a, (byte) 0x9c, (byte) 0x58, (byte) 0x8c, (byte) 0xe,
            (byte) 0xa4, (byte) 0x27, (byte) 0x57, (byte) 0x97, (byte) 0x8b,
            (byte) 0x3, (byte) 0xa3, (byte) 0xe0, (byte) 0x96, (byte) 0x5c,
            (byte) 0x34, (byte) 0x47, (byte) 0x6, (byte) 0x52, (byte) 0x38,
            (byte) 0xf4, (byte) 0x48, (byte) 0xb4, (byte) 0xfe, (byte) 0xdb,
            (byte) 0x8f, (byte) 0xd1, (byte) 0x28, (byte) 0xf4, (byte) 0xab,
            (byte) 0xd5, (byte) 0xc9, (byte) 0xc5, (byte) 0x51, (byte) 0xe2,
            (byte) 0xdf, (byte) 0x29, (byte) 0x93, (byte) 0x5b, (byte) 0xac,
            (byte) 0x32, (byte) 0xef, (byte) 0xe9, (byte) 0x70, (byte) 0xc9,
            (byte) 0xd3, (byte) 0xf7, (byte) 0x6f, (byte) 0xae, (byte) 0x39,
            (byte) 0x81, (byte) 0xf4, (byte) 0xd, (byte) 0x7f, (byte) 0x36,
            (byte) 0x51, (byte) 0xc7, (byte) 0x9b, (byte) 0x17, (byte) 0xef,
            (byte) 0xb7, (byte) 0xf1, (byte) 0x3, (byte) 0x50, (byte) 0x4b,
            (byte) 0x7, (byte) 0x8, (byte) 0x8b, (byte) 0xcb, (byte) 0xd5,
            (byte) 0xea, (byte) 0x3, (byte) 0x1, (byte) 0x0, (byte) 0x0,
            (byte) 0x48, (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x50,
            (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0,
            (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x17,
            (byte) 0x71, (byte) 0x3b, (byte) 0x54, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x15, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d,
            (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49,
            (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d, (byte) 0x59,
            (byte) 0x46, (byte) 0x49, (byte) 0x52, (byte) 0x53, (byte) 0x54,
            (byte) 0x4b, (byte) 0x2e, (byte) 0x52, (byte) 0x53, (byte) 0x41,
            (byte) 0x33, (byte) 0x68, (byte) 0x62, (byte) 0xd, (byte) 0x61,
            (byte) 0xe3, (byte) 0xd4, (byte) 0x6a, (byte) 0xf3, (byte) 0x68,
            (byte) 0xfb, (byte) 0xce, (byte) 0xcb, (byte) 0xc8, (byte) 0xce,
            (byte) 0xb4, (byte) 0xa0, (byte) 0x89, (byte) 0xd5, (byte) 0xd5,
            (byte) 0xa0, (byte) 0x89, (byte) 0xd5, (byte) 0x91, (byte) 0x89,
            (byte) 0x91, (byte) 0xd1, (byte) 0x90, (byte) 0xdf, (byte) 0x80,
            (byte) 0x97, (byte) 0x8d, (byte) 0x33, (byte) 0xa1, (byte) 0xcd,
            (byte) 0x83, (byte) 0x31, (byte) 0x95, (byte) 0x99, (byte) 0x85,
            (byte) 0x89, (byte) 0x91, (byte) 0x95, (byte) 0xc1, (byte) 0x80,
            (byte) 0x1b, (byte) 0xa1, (byte) 0x90, (byte) 0x71, (byte) 0x41,
            (byte) 0x13, (byte) 0x73, (byte) 0x85, (byte) 0x41, (byte) 0x13,
            (byte) 0x73, (byte) 0x89, (byte) 0x41, (byte) 0x13, (byte) 0x53,
            (byte) 0xcc, (byte) 0x2, (byte) 0x66, (byte) 0x26, (byte) 0x46,
            (byte) 0x26, (byte) 0x26, (byte) 0x4e, (byte) 0x86, (byte) 0xcd,
            (byte) 0xbc, (byte) 0x81, (byte) 0x6c, (byte) 0xf9, (byte) 0x5b,
            (byte) 0xf6, (byte) 0xaa, (byte) 0x82, (byte) 0xf4, (byte) 0x41,
            (byte) 0x15, (byte) 0x32, (byte) 0x72, (byte) 0x3, (byte) 0xf5,
            (byte) 0x65, (byte) 0x18, (byte) 0x72, (byte) 0x1b, (byte) 0x70,
            (byte) 0xb2, (byte) 0x31, (byte) 0x87, (byte) 0xb2, (byte) 0xb0,
            (byte) 0x9, (byte) 0x33, (byte) 0x85, (byte) 0x6, (byte) 0xc3,
            (byte) 0x38, (byte) 0x1c, (byte) 0xc2, (byte) 0x4c, (byte) 0xbe,
            (byte) 0x8e, (byte) 0x86, (byte) 0xc2, (byte) 0x6, (byte) 0x82,
            (byte) 0x20, (byte) 0xe, (byte) 0xbb, (byte) 0x30, (byte) 0x97,
            (byte) 0x53, (byte) 0x7e, (byte) 0x45, (byte) 0x52, (byte) 0x7e,
            (byte) 0x51, (byte) 0x7e, (byte) 0x69, (byte) 0x7a, (byte) 0x6,
            (byte) 0xd8, (byte) 0x66, (byte) 0xa0, (byte) 0x20, (byte) 0x97,
            (byte) 0x30, (byte) 0x9b, (byte) 0x7f, (byte) 0x51, (byte) 0x62,
            (byte) 0x72, (byte) 0x4e, (byte) 0xaa, (byte) 0x21, (byte) 0x2f,
            (byte) 0xd0, (byte) 0x6e, (byte) 0xa0, (byte) 0x0, (byte) 0xb7,
            (byte) 0x30, (byte) 0x8b, (byte) 0x57, (byte) 0x62, (byte) 0x59,
            (byte) 0xa2, (byte) 0xa1, (byte) 0xb8, (byte) 0x81, (byte) 0x28,
            (byte) 0x88, (byte) 0xcb, (byte) 0x2c, (byte) 0xcc, (byte) 0xe7,
            (byte) 0x93, (byte) 0x98, (byte) 0x97, (byte) 0x9c, (byte) 0xaa,
            (byte) 0xe0, (byte) 0x98, (byte) 0x97, (byte) 0x92, (byte) 0x5a,
            (byte) 0x54, (byte) 0x9c, (byte) 0x9a, (byte) 0x67, (byte) 0x20,
            (byte) 0x27, (byte) 0xce, (byte) 0x6b, (byte) 0x64, (byte) 0x64,
            (byte) 0x60, (byte) 0x68, (byte) 0x64, (byte) 0x6e, (byte) 0x68,
            (byte) 0x69, (byte) 0x60, (byte) 0x6a, (byte) 0x60, (byte) 0x19,
            (byte) 0x5, (byte) 0xe6, (byte) 0x9a, (byte) 0xc0, (byte) 0xb9,
            (byte) 0x74, (byte) 0x74, (byte) 0x49, (byte) 0x13, (byte) 0xa3,
            (byte) 0x12, (byte) 0x72, (byte) 0x30, (byte) 0x0, (byte) 0x83,
            (byte) 0x8f, (byte) 0xb9, (byte) 0x89, (byte) 0x91, (byte) 0x9f,
            (byte) 0x1, (byte) 0x28, (byte) 0xce, (byte) 0xc5, (byte) 0xd4,
            (byte) 0xc4, (byte) 0xc8, (byte) 0xc8, (byte) 0x30, (byte) 0xd7,
            (byte) 0xef, (byte) 0xf5, (byte) 0x1c, (byte) 0x99, (byte) 0x95,
            (byte) 0xf7, (byte) 0xca, (byte) 0x4e, (byte) 0xff, (byte) 0x75,
            (byte) 0x49, (byte) 0xe4, (byte) 0xa9, (byte) 0x8e, (byte) 0xb8,
            (byte) 0x91, (byte) 0xe1, (byte) 0xb5, (byte) 0xf3, (byte) 0xca,
            (byte) 0xc1, (byte) 0xe9, (byte) 0x6e, (byte) 0xcd, (byte) 0x97,
            (byte) 0xef, (byte) 0x28, (byte) 0x25, (byte) 0xc7, (byte) 0x89,
            (byte) 0x6d, (byte) 0xf2, (byte) 0xb, (byte) 0x10, (byte) 0xf0,
            (byte) 0x7f, (byte) 0x63, (byte) 0xb8, (byte) 0x91, (byte) 0xfb,
            (byte) 0xff, (byte) 0x6b, (byte) 0xfd, (byte) 0x85, (byte) 0x17,
            (byte) 0x96, (byte) 0x67, (byte) 0x79, (byte) 0xfd, (byte) 0xa8,
            (byte) 0x7b, (byte) 0xbf, (byte) 0x75, (byte) 0xad, (byte) 0x77,
            (byte) 0xc0, (byte) 0x4e, (byte) 0x9f, (byte) 0xb6, (byte) 0x9e,
            (byte) 0xee, (byte) 0xd, (byte) 0xcb, (byte) 0xf8, (byte) 0xd7,
            (byte) 0xc9, (byte) 0xdc, (byte) 0x2c, (byte) 0xee, (byte) 0x4d,
            (byte) 0x91, (byte) 0x2e, (byte) 0xb0, (byte) 0x48, (byte) 0xd8,
            (byte) 0xb2, (byte) 0x63, (byte) 0xcb, (byte) 0x91, (byte) 0x8f,
            (byte) 0x89, (byte) 0x69, (byte) 0x7a, (byte) 0x53, (byte) 0x6f,
            (byte) 0x6d, (byte) 0x59, (byte) 0xa3, (byte) 0xd3, (byte) 0xfb,
            (byte) 0xab, (byte) 0xa0, (byte) 0xe6, (byte) 0xab, (byte) 0x24,
            (byte) 0x73, (byte) 0xd0, (byte) 0x32, (byte) 0xc6, (byte) 0x2c,
            (byte) 0x1, (byte) 0x29, (byte) 0xf5, (byte) 0xa3, (byte) 0x1a,
            (byte) 0x62, (byte) 0xc5, (byte) 0x4f, (byte) 0xce, (byte) 0xda,
            (byte) 0xed, (byte) 0x54, (byte) 0x8b, (byte) 0xfb, (byte) 0x26,
            (byte) 0x1b, (byte) 0xba, (byte) 0xe0, (byte) 0xb1, (byte) 0x76,
            (byte) 0xb9, (byte) 0x75, (byte) 0xcb, (byte) 0x67, (byte) 0x27,
            (byte) 0xa6, (byte) 0xd2, (byte) 0xdb, (byte) 0xb9, (byte) 0x7,
            (byte) 0x5, (byte) 0x3e, (byte) 0xee, (byte) 0x9c, (byte) 0x64,
            (byte) 0x74, (byte) 0xdc, (byte) 0xad, (byte) 0x80, (byte) 0x8d,
            (byte) 0xeb, (byte) 0xe5, (byte) 0xb3, (byte) 0x15, (byte) 0x9b,
            (byte) 0x33, (byte) 0xf, (byte) 0x1f, (byte) 0xf7, (byte) 0x34,
            (byte) 0x9a, (byte) 0xf5, (byte) 0xf2, (byte) 0xc3, (byte) 0x14,
            (byte) 0xd3, (byte) 0x48, (byte) 0x11, (byte) 0xa1, (byte) 0xea,
            (byte) 0xe7, (byte) 0xea, (byte) 0x95, (byte) 0x95, (byte) 0x32,
            (byte) 0x37, (byte) 0x15, (byte) 0xfa, (byte) 0x2, (byte) 0xfe,
            (byte) 0xbd, (byte) 0x61, (byte) 0x17, (byte) 0xb2, (byte) 0xcc,
            (byte) 0x9c, (byte) 0xf3, (byte) 0x6e, (byte) 0x9a, (byte) 0x57,
            (byte) 0xce, (byte) 0x54, (byte) 0x55, (byte) 0x33, (byte) 0x96,
            (byte) 0x64, (byte) 0xde, (byte) 0x2b, (byte) 0x6b, (byte) 0x39,
            (byte) 0xfe, (byte) 0x76, (byte) 0xe7, (byte) 0xdc, (byte) 0xb2,
            (byte) 0xb3, (byte) 0xbe, (byte) 0xed, (byte) 0x52, (byte) 0x7b,
            (byte) 0xb9, (byte) 0x90, (byte) 0xd9, (byte) 0x85, (byte) 0xd9,
            (byte) 0xed, (byte) 0xbb, (byte) 0x57, (byte) 0x35, (byte) 0xcf,
            (byte) 0xe6, (byte) 0x87, (byte) 0xf3, (byte) 0x4f, (byte) 0x4,
            (byte) 0x54, (byte) 0x7b, (byte) 0x95, (byte) 0x5c, (byte) 0xd0,
            (byte) 0x9d, (byte) 0x7a, (byte) 0xbd, (byte) 0xb7, (byte) 0x6f,
            (byte) 0xe9, (byte) 0x6f, (byte) 0x8f, (byte) 0x86, (byte) 0x93,
            (byte) 0x5b, (byte) 0x97, (byte) 0xec, (byte) 0xfe, (byte) 0xd5,
            (byte) 0x78, (byte) 0xc2, (byte) 0xd6, (byte) 0xa7, (byte) 0x46,
            (byte) 0x8d, (byte) 0x89, (byte) 0xb9, (byte) 0x57, (byte) 0xab,
            (byte) 0xb5, (byte) 0x75, (byte) 0xab, (byte) 0xcd, (byte) 0x9d,
            (byte) 0x43, (byte) 0x9c, (byte) 0xde, (byte) 0x17, (byte) 0x2b,
            (byte) 0x3e, (byte) 0xfa, (byte) 0xbb, (byte) 0x4c, (byte) 0x96,
            (byte) 0x9c, (byte) 0x73, (byte) 0xe3, (byte) 0x64, (byte) 0xb4,
            (byte) 0xcc, (byte) 0xcb, (byte) 0xfd, (byte) 0x4c, (byte) 0xcc,
            (byte) 0x8c, (byte) 0xc, (byte) 0x8c, (byte) 0x8b, (byte) 0x15,
            (byte) 0xd, (byte) 0xe4, (byte) 0xd, (byte) 0x64, (byte) 0x81,
            (byte) 0x1, (byte) 0x28, (byte) 0xcb, (byte) 0xc7, (byte) 0x22,
            (byte) 0xc6, (byte) 0x22, (byte) 0xf2, (byte) 0xb0, (byte) 0xe4,
            (byte) 0x67, (byte) 0x69, (byte) 0xfa, (byte) 0xd5, (byte) 0x12,
            (byte) 0xdf, (byte) 0x8c, (byte) 0xdf, (byte) 0x21, (byte) 0xd5,
            (byte) 0x9f, (byte) 0x17, (byte) 0x8b, (byte) 0xfa, (byte) 0x68,
            (byte) 0x9, (byte) 0xf1, (byte) 0xcb, (byte) 0xa0, (byte) 0xa5,
            (byte) 0x25, (byte) 0x66, (byte) 0x50, (byte) 0xd8, (byte) 0xf5,
            (byte) 0x73, (byte) 0xfc, (byte) 0xf9, (byte) 0xaf, (byte) 0x12,
            (byte) 0xbf, (byte) 0x42, (byte) 0xbb, (byte) 0x41, (byte) 0xe0,
            (byte) 0xb6, (byte) 0x91, (byte) 0xfc, (byte) 0xbc, (byte) 0xe3,
            (byte) 0xf3, (byte) 0x39, (byte) 0x1c, (byte) 0x1b, (byte) 0xf3,
            (byte) 0xd8, (byte) 0x2f, (byte) 0x7c, (byte) 0xe9, (byte) 0x7b,
            (byte) 0xd2, (byte) 0x1a, (byte) 0xf0, (byte) 0xb3, (byte) 0x52,
            (byte) 0x61, (byte) 0xb5, (byte) 0xbf, (byte) 0xde, (byte) 0x31,
            (byte) 0xf6, (byte) 0x17, (byte) 0xe7, (byte) 0x5a, (byte) 0x9b,
            (byte) 0x9f, (byte) 0x6f, (byte) 0xf5, (byte) 0x94, (byte) 0x4b,
            (byte) 0x6d, (byte) 0x31, (byte) 0x2e, (byte) 0x56, (byte) 0x37,
            (byte) 0x5c, (byte) 0x64, (byte) 0x32, (byte) 0xc1, (byte) 0xef,
            (byte) 0x75, (byte) 0xcf, (byte) 0xe9, (byte) 0x8c, (byte) 0xcc,
            (byte) 0xcf, (byte) 0x9d, (byte) 0xdf, (byte) 0x34, (byte) 0x1b,
            (byte) 0xf4, (byte) 0xe4, (byte) 0x75, (byte) 0xac, (byte) 0xff,
            (byte) 0xbf, (byte) 0x5b, (byte) 0xaf, (byte) 0xfa, (byte) 0x98,
            (byte) 0x5f, (byte) 0x22, (byte) 0x75, (byte) 0xf5, (byte) 0xd5,
            (byte) 0x8c, (byte) 0xd3, (byte) 0xae, (byte) 0x17, (byte) 0xab,
            (byte) 0xad, (byte) 0xb9, (byte) 0x6a, (byte) 0x17, (byte) 0x84,
            (byte) 0xf8, (byte) 0xf9, (byte) 0x72, (byte) 0xf5, (byte) 0x7b,
            (byte) 0xad, (byte) 0x5b, (byte) 0xd3, (byte) 0xd0, (byte) 0x5f,
            (byte) 0x71, (byte) 0x29, (byte) 0xa0, (byte) 0xa3, (byte) 0x2c,
            (byte) 0xfc, (byte) 0x73, (byte) 0xc2, (byte) 0x3f, (byte) 0xd6,
            (byte) 0xf4, (byte) 0x9c, (byte) 0xbe, (byte) 0xa9, (byte) 0x2c,
            (byte) 0xf5, (byte) 0x7e, (byte) 0x17, (byte) 0xdf, (byte) 0x95,
            (byte) 0xb2, (byte) 0xdd, (byte) 0x29, (byte) 0x33, (byte) 0xb8,
            (byte) 0x66, (byte) 0x56, (byte) 0x55, (byte) 0xcc, (byte) 0xba,
            (byte) 0x6a, (byte) 0x5d, (byte) 0x92, (byte) 0xeb, (byte) 0xe7,
            (byte) 0x7e, (byte) 0x2f, (byte) 0x67, (byte) 0x99, (byte) 0xa2,
            (byte) 0xb, (byte) 0xda, (byte) 0x7d, (byte) 0xa7, (byte) 0x73,
            (byte) 0x2b, (byte) 0xb6, (byte) 0x5f, (byte) 0x7e, (byte) 0x3b,
            (byte) 0x45, (byte) 0xce, (byte) 0xdc, (byte) 0x28, (byte) 0xed,
            (byte) 0xed, (byte) 0xf2, (byte) 0x84, (byte) 0xf0, (byte) 0xc2,
            (byte) 0x8d, (byte) 0x62, (byte) 0xbc, (byte) 0xe, (byte) 0xac,
            (byte) 0x29, (byte) 0x33, (byte) 0xe3, (byte) 0xdd, (byte) 0xb6,
            (byte) 0x33, (byte) 0x98, (byte) 0x15, (byte) 0x54, (byte) 0x4f,
            (byte) 0x6f, (byte) 0x8d, (byte) 0xba, (byte) 0x11, (byte) 0xf9,
            (byte) 0xeb, (byte) 0xd1, (byte) 0x7e, (byte) 0x3, (byte) 0x13,
            (byte) 0xd6, (byte) 0xf6, (byte) 0xea, (byte) 0xee, (byte) 0xc8,
            (byte) 0xa5, (byte) 0x7c, (byte) 0xf, (byte) 0x14, (byte) 0x1d,
            (byte) 0xae, (byte) 0xfc, (byte) 0xb4, (byte) 0xfb, (byte) 0x2a,
            (byte) 0xa4, (byte) 0xf7, (byte) 0xd0, (byte) 0x7b, (byte) 0x53,
            (byte) 0x73, (byte) 0xc6, (byte) 0x8b, (byte) 0x6d, (byte) 0xc7,
            (byte) 0xac, (byte) 0x8f, (byte) 0x68, (byte) 0x5e, (byte) 0x95,
            (byte) 0x7a, (byte) 0x34, (byte) 0xd7, (byte) 0x4e, (byte) 0x5b,
            (byte) 0x35, (byte) 0xd3, (byte) 0xe1, (byte) 0xe3, (byte) 0xd7,
            (byte) 0x94, (byte) 0x60, (byte) 0xd3, (byte) 0xa4, (byte) 0x93,
            (byte) 0x7a, (byte) 0x86, (byte) 0x3f, (byte) 0x35, (byte) 0x58,
            (byte) 0x1e, (byte) 0x88, (byte) 0x5d, (byte) 0x7e, (byte) 0xd0,
            (byte) 0xf6, (byte) 0x63, (byte) 0xf3, (byte) 0xe3, (byte) 0xd4,
            (byte) 0xf, (byte) 0x3b, (byte) 0xd5, (byte) 0xaf, (byte) 0x98,
            (byte) 0xfc, (byte) 0x6c, (byte) 0x3e, (byte) 0x66, (byte) 0x1e,
            (byte) 0xe5, (byte) 0xe6, (byte) 0x60, (byte) 0x55, (byte) 0x7e,
            (byte) 0x78, (byte) 0x45, (byte) 0x89, (byte) 0x86, (byte) 0xde,
            (byte) 0xc6, (byte) 0x4f, (byte) 0x86, (byte) 0x4d, (byte) 0x8c,
            (byte) 0xb, (byte) 0x80, (byte) 0x9, (byte) 0x69, (byte) 0xe,
            (byte) 0x30, (byte) 0x93, (byte) 0x1a, (byte) 0x94, (byte) 0xd2,
            (byte) 0x2f, (byte) 0x4d, (byte) 0xa3, (byte) 0xe5, (byte) 0x70,
            (byte) 0xe4, (byte) 0x92, (byte) 0x1, (byte) 0x35, (byte) 0xa5,
            (byte) 0xb3, (byte) 0x34, (byte) 0x31, (byte) 0x32, (byte) 0xf4,
            (byte) 0xa, (byte) 0x9c, (byte) 0xb0, (byte) 0x9d, (byte) 0x61,
            (byte) 0x3e, (byte) 0x53, (byte) 0x78, (byte) 0xce, (byte) 0x42,
            (byte) 0xb7, (byte) 0x7, (byte) 0x9b, (byte) 0xcb, (byte) 0x95,
            (byte) 0xb6, (byte) 0x74, (byte) 0xe8, (byte) 0x16, (byte) 0x8,
            (byte) 0xfe, (byte) 0x3c, (byte) 0x74, (byte) 0xe1, (byte) 0xe,
            (byte) 0x87, (byte) 0xf6, (byte) 0x3a, (byte) 0xeb, (byte) 0x45,
            (byte) 0xfb, (byte) 0x97, (byte) 0x71, (byte) 0xbf, (byte) 0xaa,
            (byte) 0x13, (byte) 0x89, (byte) 0x78, (byte) 0x1e, (byte) 0x74,
            (byte) 0xe4, (byte) 0x97, (byte) 0x69, (byte) 0xf2, (byte) 0xe5,
            (byte) 0xd, (byte) 0x22, (byte) 0xa, (byte) 0x73, (byte) 0xf7,
            (byte) 0x5e, (byte) 0xd1, (byte) 0x92, (byte) 0xdb, (byte) 0x25,
            (byte) 0x72, (byte) 0xf0, (byte) 0xdb, (byte) 0x34, (byte) 0xc3,
            (byte) 0x79, (byte) 0xcb, (byte) 0xaa, (byte) 0xcb, (byte) 0xc,
            (byte) 0xd4, (byte) 0xd3, (byte) 0xd6, (byte) 0x9e, (byte) 0xb8,
            (byte) 0xa9, (byte) 0xf3, (byte) 0x71, (byte) 0xa5, (byte) 0x48,
            (byte) 0x4a, (byte) 0xe2, (byte) 0x57, (byte) 0xbb, (byte) 0xb6,
            (byte) 0xe5, (byte) 0xaf, (byte) 0x5e, (byte) 0x4, (byte) 0x48,
            (byte) 0xd6, (byte) 0xd5, (byte) 0x71, (byte) 0xc8, (byte) 0xaf,
            (byte) 0x34, (byte) 0xe0, (byte) 0xae, (byte) 0x5e, (byte) 0x1a,
            (byte) 0xbe, (byte) 0xb5, (byte) 0xc3, (byte) 0xbc, (byte) 0xa9,
            (byte) 0xfc, (byte) 0x51, (byte) 0xc, (byte) 0x73, (byte) 0xef,
            (byte) 0x83, (byte) 0x47, (byte) 0x3e, (byte) 0x72, (byte) 0x3b,
            (byte) 0xce, (byte) 0xbe, (byte) 0xfd, (byte) 0xb4, (byte) 0x41,
            (byte) 0x20, (byte) 0x54, (byte) 0xf2, (byte) 0x47, (byte) 0x45,
            (byte) 0x69, (byte) 0xb8, (byte) 0xcd, (byte) 0x59, (byte) 0xbb,
            (byte) 0x8e, (byte) 0xf2, (byte) 0xc3, (byte) 0x86, (byte) 0xea,
            (byte) 0x32, (byte) 0x1f, (byte) 0xbf, (byte) 0x4, (byte) 0x6b,
            (byte) 0xda, (byte) 0xaa, (byte) 0x2b, (byte) 0x33, (byte) 0x7d,
            (byte) 0x54, (byte) 0xf2, (byte) 0x9, (byte) 0x16, (byte) 0x16,
            (byte) 0xfa, (byte) 0x5b, (byte) 0xb6, (byte) 0x6d, (byte) 0x42,
            (byte) 0xaa, (byte) 0x9e, (byte) 0x9e, (byte) 0x16, (byte) 0x73,
            (byte) 0xc0, (byte) 0x6a, (byte) 0xf7, (byte) 0xbf, (byte) 0x13,
            (byte) 0xf9, (byte) 0xbc, (byte) 0xfc, (byte) 0x79, (byte) 0xcc,
            (byte) 0x84, (byte) 0x6a, (byte) 0x9f, (byte) 0x9c, (byte) 0x88,
            (byte) 0xcd, (byte) 0xb3, (byte) 0xb3, (byte) 0x98, (byte) 0xc0,
            (byte) 0xf3, (byte) 0xf0, (byte) 0xc4, (byte) 0x86, (byte) 0x6f,
            (byte) 0x86, (byte) 0xd1, (byte) 0x97, (byte) 0x53, (byte) 0x4b,
            (byte) 0x6b, (byte) 0xb9, (byte) 0xbe, (byte) 0xca, (byte) 0xde,
            (byte) 0xd8, (byte) 0x23, (byte) 0x58, (byte) 0xee, (byte) 0x92,
            (byte) 0xcc, (byte) 0x7f, (byte) 0xa7, (byte) 0xe7, (byte) 0xbc,
            (byte) 0xff, (byte) 0x84, (byte) 0x93, (byte) 0x13, (byte) 0xcc,
            (byte) 0x94, (byte) 0xe, (byte) 0x73, (byte) 0x9b, (byte) 0x2c,
            (byte) 0x9d, (byte) 0xfa, (byte) 0x62, (byte) 0x75, (byte) 0x98,
            (byte) 0x6f, (byte) 0x70, (byte) 0xf1, (byte) 0x97, (byte) 0xaa,
            (byte) 0xe5, (byte) 0x9b, (byte) 0xa6, (byte) 0xac, (byte) 0x4e,
            (byte) 0x7f, (byte) 0xbb, (byte) 0xc2, (byte) 0x34, (byte) 0xec,
            (byte) 0xe2, (byte) 0xbe, (byte) 0xc0, (byte) 0x99, (byte) 0x1d,
            (byte) 0x9f, (byte) 0xb8, (byte) 0xa5, (byte) 0x6b, (byte) 0xb8,
            (byte) 0x4, (byte) 0xf7, (byte) 0x2d, (byte) 0x11, (byte) 0x9a,
            (byte) 0x56, (byte) 0xb1, (byte) 0xa9, (byte) 0xbd, (byte) 0xb4,
            (byte) 0x8f, (byte) 0x57, (byte) 0x60, (byte) 0xcf, (byte) 0xb3,
            (byte) 0xab, (byte) 0xad, (byte) 0x86, (byte) 0xea, (byte) 0x46,
            (byte) 0xbb, (byte) 0x7b, (byte) 0xf7, (byte) 0xfe, (byte) 0xe8,
            (byte) 0xd6, (byte) 0xd2, (byte) 0x9d, (byte) 0x3, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8, (byte) 0xba,
            (byte) 0x40, (byte) 0x24, (byte) 0x13, (byte) 0x4c, (byte) 0x4,
            (byte) 0x0, (byte) 0x0, (byte) 0x58, (byte) 0x5, (byte) 0x0,
            (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x6d, (byte) 0x73, (byte) 0x3a, (byte) 0x54,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x9, (byte) 0x0, (byte) 0x4,
            (byte) 0x0, (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41,
            (byte) 0x2d, (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f,
            (byte) 0xfe, (byte) 0xca, (byte) 0x0, (byte) 0x0, (byte) 0x3,
            (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3,
            (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x6c, (byte) 0xb1, (byte) 0x38,
            (byte) 0x3e, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x1c, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x6a, (byte) 0x61, (byte) 0x76,
            (byte) 0x61, (byte) 0x78, (byte) 0x2f, (byte) 0x69, (byte) 0x6e,
            (byte) 0x6a, (byte) 0x65, (byte) 0x63, (byte) 0x74, (byte) 0x2f,
            (byte) 0x53, (byte) 0x69, (byte) 0x6e, (byte) 0x67, (byte) 0x6c,
            (byte) 0x65, (byte) 0x74, (byte) 0x6f, (byte) 0x6e, (byte) 0x2e,
            (byte) 0x63, (byte) 0x6c, (byte) 0x61, (byte) 0x73, (byte) 0x73,
            (byte) 0x85, (byte) 0x90, (byte) 0x4d, (byte) 0x4b, (byte) 0x82,
            (byte) 0x41, (byte) 0x10, (byte) 0xc7, (byte) 0xff, (byte) 0xa3,
            (byte) 0xd9, (byte) 0x63, (byte) 0x56, (byte) 0xe6, (byte) 0x21,
            (byte) 0x5, (byte) 0x4f, (byte) 0xda, (byte) 0xc5, (byte) 0x63,
            (byte) 0x4b, (byte) 0xe7, (byte) 0x4e, (byte) 0x41, (byte) 0x6,
            (byte) 0x81, (byte) 0xa5, (byte) 0x3c, (byte) 0x5a, (byte) 0xf7,
            (byte) 0x75, (byte) 0x1b, (byte) 0x64, (byte) 0x65, (byte) 0xdd,
            (byte) 0x8d, (byte) 0xdc, (byte) 0x47, (byte) 0xea, (byte) 0xab,
            (byte) 0x79, (byte) 0xe8, (byte) 0x3, (byte) 0xf4, (byte) 0xa1,
            (byte) 0xc4, (byte) 0xd9, (byte) 0xe, (byte) 0x4a, (byte) 0x20,
            (byte) 0x34, (byte) 0x30, (byte) 0x2f, (byte) 0xcc, (byte) 0xfc,
            (byte) 0x66, (byte) 0x98, (byte) 0x99, (byte) 0x9f, (byte) 0xcd,
            (byte) 0xfa, (byte) 0x1b, (byte) 0xc0, (byte) 0xd, (byte) 0x1a,
            (byte) 0x84, (byte) 0x2c, (byte) 0x7f, (byte) 0x79, (byte) 0x9e,
            (byte) 0x3c, (byte) 0x3e, (byte) 0xf5, (byte) 0x9, (byte) 0x8d,
            (byte) 0xb9, (byte) 0x5e, (byte) 0x69, (byte) 0xe5, (byte) 0xb4,
            (byte) 0x9f, (byte) 0xa9, (byte) 0xe1, (byte) 0x74, (byte) 0xce,
            (byte) 0x26, (byte) 0x12, (byte) 0x3a, (byte) 0xfb, (byte) 0x94,
            (byte) 0xf6, (byte) 0x3e, (byte) 0x44, (byte) 0x1d, (byte) 0x6d,
            (byte) 0xf0, (byte) 0xea, (byte) 0x6e, (byte) 0x17, (byte) 0x12,
            (byte) 0x5a, (byte) 0x89, (byte) 0xf8, (byte) 0x54, (byte) 0xd6,
            (byte) 0xa7, (byte) 0x6, (byte) 0x35, (byte) 0xb6, (byte) 0x7e,
            (byte) 0xe6, (byte) 0x38, (byte) 0xa6, (byte) 0x42, (byte) 0x65,
            (byte) 0xa5, (byte) 0x5d, (byte) 0xc1, (byte) 0x19, (byte) 0x4a,
            (byte) 0x19, (byte) 0xca, (byte) 0x19, (byte) 0x8e, (byte) 0x8,
            (byte) 0x57, (byte) 0x83, (byte) 0x83, (byte) 0xc3, (byte) 0xee,
            (byte) 0x83, (byte) 0x29, (byte) 0x16, (byte) 0xec, (byte) 0x23,
            (byte) 0xbf, (byte) 0xdd, (byte) 0x12, (byte) 0xba, (byte) 0x87,
            (byte) 0x99, (byte) 0x9c, (byte) 0xa3, (byte) 0x10, (byte) 0x12,
            (byte) 0x9, (byte) 0xd2, (byte) 0xfb, (byte) 0x7, (byte) 0x19,
            (byte) 0x5, (byte) 0x67, (byte) 0xcd, (byte) 0x97, (byte) 0x80,
            (byte) 0x97, (byte) 0x83, (byte) 0xbf, (byte) 0xab, (byte) 0x99,
            (byte) 0xf0, (byte) 0xce, (byte) 0x92, (byte) 0x6e, (byte) 0xe7,
            (byte) 0x85, (byte) 0x70, (byte) 0xb, (byte) 0x7e, (byte) 0xb5,
            (byte) 0x4b, (byte) 0x3b, (byte) 0x75, (byte) 0xbc, (byte) 0xbf,
            (byte) 0x65, (byte) 0x49, (byte) 0xa8, (byte) 0xef, (byte) 0xf6,
            (byte) 0xbf, (byte) 0x4e, (byte) 0xbd, (byte) 0x84, (byte) 0xda,
            (byte) 0x38, (byte) 0x14, (byte) 0x1f, (byte) 0x86, (byte) 0x1f,
            (byte) 0xac, (byte) 0xe3, (byte) 0x1e, (byte) 0xa1, (byte) 0x8a,
            (byte) 0x63, (byte) 0xc8, (byte) 0xc3, (byte) 0x90, (byte) 0xa4,
            (byte) 0x84, (byte) 0x8b, (byte) 0x5f, (byte) 0x5b, (byte) 0xc7,
            (byte) 0xb9, (byte) 0xf8, (byte) 0x26, (byte) 0xca, (byte) 0x38,
            (byte) 0x13, (byte) 0x7f, (byte) 0x22, (byte) 0x5a, (byte) 0x13,
            (byte) 0xa6, (byte) 0xc2, (byte) 0x38, (byte) 0x5, (byte) 0x6d,
            (byte) 0x1, (byte) 0x50, (byte) 0x4b, (byte) 0x7, (byte) 0x8,
            (byte) 0x5a, (byte) 0xf1, (byte) 0x0, (byte) 0x98, (byte) 0xdd,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x67, (byte) 0x1,
            (byte) 0x0, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0,
            (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x17,
            (byte) 0x71, (byte) 0x3b, (byte) 0x54, (byte) 0xc1, (byte) 0xfa,
            (byte) 0x9d, (byte) 0xe2, (byte) 0x9e, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0xa6, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d, (byte) 0x45,
            (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49, (byte) 0x4e,
            (byte) 0x46, (byte) 0x2f, (byte) 0x4d, (byte) 0x41, (byte) 0x4e,
            (byte) 0x49, (byte) 0x46, (byte) 0x45, (byte) 0x53, (byte) 0x54,
            (byte) 0x2e, (byte) 0x4d, (byte) 0x46, (byte) 0x50, (byte) 0x4b,
            (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8, (byte) 0x0,
            (byte) 0x17, (byte) 0x71, (byte) 0x3b, (byte) 0x54, (byte) 0x8b,
            (byte) 0xcb, (byte) 0xd5, (byte) 0xea, (byte) 0x3, (byte) 0x1,
            (byte) 0x0, (byte) 0x0, (byte) 0x48, (byte) 0x1, (byte) 0x0,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0xe0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x4d,
            (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d, (byte) 0x49,
            (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d, (byte) 0x59,
            (byte) 0x46, (byte) 0x49, (byte) 0x52, (byte) 0x53, (byte) 0x54,
            (byte) 0x4b, (byte) 0x2e, (byte) 0x53, (byte) 0x46, (byte) 0x50,
            (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x17, (byte) 0x71, (byte) 0x3b, (byte) 0x54,
            (byte) 0xba, (byte) 0x40, (byte) 0x24, (byte) 0x13, (byte) 0x4c,
            (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x58, (byte) 0x5,
            (byte) 0x0, (byte) 0x0, (byte) 0x15, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x25, (byte) 0x2, (byte) 0x0, (byte) 0x0,
            (byte) 0x4d, (byte) 0x45, (byte) 0x54, (byte) 0x41, (byte) 0x2d,
            (byte) 0x49, (byte) 0x4e, (byte) 0x46, (byte) 0x2f, (byte) 0x4d,
            (byte) 0x59, (byte) 0x46, (byte) 0x49, (byte) 0x52, (byte) 0x53,
            (byte) 0x54, (byte) 0x4b, (byte) 0x2e, (byte) 0x52, (byte) 0x53,
            (byte) 0x41, (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2,
            (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8,
            (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x6d, (byte) 0x73,
            (byte) 0x3a, (byte) 0x54, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x9,
            (byte) 0x0, (byte) 0x4, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xb4, (byte) 0x6,
            (byte) 0x0, (byte) 0x0, (byte) 0x4d, (byte) 0x45, (byte) 0x54,
            (byte) 0x41, (byte) 0x2d, (byte) 0x49, (byte) 0x4e, (byte) 0x46,
            (byte) 0x2f, (byte) 0xfe, (byte) 0xca, (byte) 0x0, (byte) 0x0,
            (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14,
            (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x6c, (byte) 0xb1, (byte) 0x38,
            (byte) 0x3e, (byte) 0x5a, (byte) 0xf1, (byte) 0x0, (byte) 0x98,
            (byte) 0xdd, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x67,
            (byte) 0x1, (byte) 0x0, (byte) 0x0, (byte) 0x1c, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0xf1, (byte) 0x6, (byte) 0x0,
            (byte) 0x0, (byte) 0x6a, (byte) 0x61, (byte) 0x76, (byte) 0x61,
            (byte) 0x78, (byte) 0x2f, (byte) 0x69, (byte) 0x6e, (byte) 0x6a,
            // We will modify 0x53, "S", within the CEN filename entry
            (byte) 0x65, (byte) 0x63, (byte) 0x74, (byte) 0x2f, (byte) 0x53,
            (byte) 0x69, (byte) 0x6e, (byte) 0x67, (byte) 0x6c, (byte) 0x65,
            (byte) 0x74, (byte) 0x6f, (byte) 0x6e, (byte) 0x2e, (byte) 0x63,
            (byte) 0x6c, (byte) 0x61, (byte) 0x73, (byte) 0x73, (byte) 0x50,
            (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x5,
            (byte) 0x0, (byte) 0x4c, (byte) 0x1, (byte) 0x0, (byte) 0x0,
            (byte) 0x18, (byte) 0x8, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0,
    };

    /**
     * DataProvider used to specify valid jars and whether to verify them
     *
     * @return Entry object indicating the jar file and whether it will be verified
     */
    @DataProvider
    public Object[][] validJars() {
        return new Object[][]{
                {SIGNED_VALID_ENTRY_NAME_JAR, true},
                {SIGNED_VALID_ENTRY_NAME_JAR, false},
                {VALID_ENTRY_NAME_JAR, true},
                {VALID_ENTRY_NAME_JAR, false},
        };
    }

    /**
     * DataProvider used to specify invalid jars and whether to verify them
     *
     * @return Entry object indicating the jar file and whether it will be verified
     */
    @DataProvider
    public Object[][] inValidJars() {
        return new Object[][]{
                {SIGNED_INVALID_ENTRY_NAME_JAR, true},
                {SIGNED_INVALID_ENTRY_NAME_JAR, false},
                {INVALID_ENTRY_NAME_JAR, true},
                {INVALID_ENTRY_NAME_JAR, false},
        };
    }

    /**
     * Create Jar files used by the tests.
     *
     * The {@code byte} arrays {@code VALID_ENTRY_NAME_JAR} and
     * {@code SIGNED-VALID_ENTRY_NAME_JAR} are written to disk to create the jar
     * files: {@code Valid-EntryName.jar} and {@code Signed-Valid-EntryName.jar}.
     *
     * The jar files {@code Invalid-EntryName.jar} and
     * {@code Signed-Invalid-EntryName.jar} are created by copying the
     * {@code byte} arrays {@code VALID_ENTRY_NAME} and
     * {@code SIGNED-VALID_ENTRY_NAME} and modifying
     * the CEN filename entry, "javax/inject/Singleton.class", changing the
     * first character from {@code 0x53}, "S", to the {@code 0x13}.
     *
     * @throws IOException If an error occurs
     *
     */
    @BeforeTest
    public static void setup() throws IOException {

        // Create valid jar
        Files.deleteIfExists(VALID_ENTRY_NAME_JAR);
        Files.write(VALID_ENTRY_NAME_JAR, VALID_ENTRY_NAME);

        // Create valid signed jar
        Files.deleteIfExists(SIGNED_VALID_ENTRY_NAME_JAR);
        Files.write(SIGNED_VALID_ENTRY_NAME_JAR, SIGNED_VALID_ENTRY_NAME);

        // Create a JAR file with the invalid entry name
        Files.deleteIfExists(INVALID_ENTRY_NAME_JAR);
        // Make a copy of the byte array containing the valid entry name
        byte[] invalid_bytes = Arrays.copyOf(VALID_ENTRY_NAME, VALID_ENTRY_NAME.length);
        // Change from 0x53, "S", to an invalid UTF8 character, 0x13
        invalid_bytes[VALID_ENTRY_NAME.length - SINGLETON_CEN_FILENAME_OFFSET] =
                INVALID_UTF8_BYTE;
        Files.write(INVALID_ENTRY_NAME_JAR, invalid_bytes);

        // Create a signed JAR file with the invalid entry name
        Files.deleteIfExists(SIGNED_INVALID_ENTRY_NAME_JAR);
        // Make a copy of the byte array containing the valid entry name
        invalid_bytes = Arrays.copyOf(SIGNED_VALID_ENTRY_NAME,
                SIGNED_VALID_ENTRY_NAME.length);
        // Change from 0x53, "S", to an invalid UTF8 character, 0x13
        invalid_bytes[SIGNED_VALID_ENTRY_NAME.length - SINGLETON_CEN_FILENAME_OFFSET] =
                INVALID_UTF8_BYTE;
        Files.write(SIGNED_INVALID_ENTRY_NAME_JAR, invalid_bytes);
    }

    /**
     * Clean up after the test run
     *
     * @throws IOException If an error occurs
     */
    @AfterTest
    public static void cleanup() throws IOException {
        Files.deleteIfExists(VALID_ENTRY_NAME_JAR);
        Files.deleteIfExists(SIGNED_VALID_ENTRY_NAME_JAR);
        Files.deleteIfExists(INVALID_ENTRY_NAME_JAR);
        Files.deleteIfExists(SIGNED_INVALID_ENTRY_NAME_JAR);
    }

    /**
     * Validate that the CEN filename to be modified can be accessed in
     * the original jar when the file is opened using JarFile and accessed via
     * a ZipEntry
     *
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void validJarFileZipEntryTest(Path jar, boolean verify) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            ZipEntry ze = jf.getEntry(CEN_FILENAME_TO_MODIFY);
            var is = jf.getInputStream(ze);
            byte[] cnt = is.readAllBytes();
            assertNotNull(cnt);
        }
    }

    /**
     * Validate that the CEN filename to be modified can be accessed in
     * the original jar when the file is opened using JarFile and accessed via
     * a JarEntry
     *
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void validJarFileJarEntryTest(Path jar, boolean verify) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            ZipEntry ze = jf.getEntry(CEN_FILENAME_TO_MODIFY);
            var is = jf.getInputStream(ze);
            byte[] cnt = is.readAllBytes();
            assertNotNull(cnt);
        }
    }

    /**
     * Validate that the CEN filename to be modified can be accessed in
     * the original jar when the file is opened using ZipFile
     *
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified(not used)
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void validZipFileZipEntryTest(Path jar, boolean verify) throws Exception {
        try (ZipFile jf = new ZipFile(jar.toFile())) {
            ZipEntry ze = jf.getEntry(CEN_FILENAME_TO_MODIFY);
            var is = jf.getInputStream(ze);
            byte[] cnt = is.readAllBytes();
            assertNotNull(cnt);
        }
    }

    /**
     * Validate that a NullPointerException is thrown by JarFile::getInputStream
     * when the specified ZipEntry is null.
     *
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified(not used)
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "inValidJars")
    public static void invalidJarFileZipEntry(Path jar, boolean verify) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            // The entry will not be found resulting in the ZipEntry being null
            ZipEntry ze = jf.getEntry(CEN_FILENAME_TO_MODIFY);
            var ex= expectThrows(NullPointerException.class,
                    () -> jf.getInputStream(ze) );
            // Validate that we receive the expected message from Objects.requireNonNull
            assertTrue( ex != null && ex.getMessage().equals("ze"));
        }
    }

    /**
     * Validate that a NullPointerException is thrown by ZipFile::getInputStream
     * when the specified ZipEntry is null.
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified(not used)
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "inValidJars")
    public static void invalidZipFileZipEntry(Path jar, boolean verify) throws Exception {
        try (ZipFile jf = new ZipFile(jar.toFile())) {
            // The entry will not be found resulting in the ZipEntry being null
            ZipEntry ze = jf.getEntry(CEN_FILENAME_TO_MODIFY);
            var ex= expectThrows(NullPointerException.class,
                    () -> jf.getInputStream(ze) );
            // Validate that we receive the expected message from Objects.requireNonNull
            assertTrue( ex != null && ex.getMessage().equals("entry"));
        }
    }

    /**
     * Validate that JarFile::getInputStream will return null when the specified
     * ZipEntry does not exist in the Jar file
     *
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void JarFileZipEntryDoesNotExistGetInputStreamTest(
            Path jar, boolean verify) throws Exception {

        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            var ze = new ZipEntry(ZIP_ENTRY_THAT_DOES_NOT_EXIST);
            var is = jf.getInputStream(ze);
            // As the ZipEntry cannot be found, the returned InputStream is null
            assertNull(is);
        }
    }

    /**
     * Validate that ZipFile::getInputStream will return null when the specified
     * ZipEntry does not exist in the Jar file
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified(not used)
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void ZipFileZipEntryDoesNotExistGetInputStreamTest(
            Path jar, boolean verify) throws Exception {
        try (ZipFile jf = new ZipFile(jar.toFile())) {
            var ze = new ZipEntry(ZIP_ENTRY_THAT_DOES_NOT_EXIST);
            var is = jf.getInputStream(ze);
            // As the ZipEntry cannot be found, the returned InputStream is null
            assertNull(is);
        }
    }

    /**
     * Validate that JarFile::getInputStream will return null when the specified
     * JarEntry does not exist in the Jar file
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void JarFileJarEntryEntryDoesNotExistGetInputStreamTest (
            Path jar, boolean verify) throws Exception {
        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            var je = new JarEntry(ZIP_ENTRY_THAT_DOES_NOT_EXIST);
            var is = jf.getInputStream(je);
            // As the JarEntry cannot be found, the returned InputStream is null
            assertNull(is);
        }
    }

    /**
     * Validate that JarFile::getInputStream will return null when validating
     * a signed jar and the ZipEntry passed as a parameter returns null
     * when ZipEntry::getName is invoked.
     * @param jar the jar file to be used
     * @param verify indicates whether the jar should be verified
     * @throws Exception if an error occurs
     */
    @Test(dataProvider = "validJars")
    public static void JarFileZipEntryGetNameNullTest(Path jar, boolean verify) throws Exception {

        // Signed Jar is used for the next checks
        try (JarFile jf = new JarFile(jar.toFile(), verify)) {
            var ze = new InvalidZipEntry(CEN_FILENAME_TO_MODIFY);
            var is = jf.getInputStream(ze);
            // As the ZipEntry cannot be found, the returned InputStream is null
            assertNull(is);
        }
    }

    /**
     * Utility method which takes a byte array and converts to byte array
     * declaration.  For example:
     * <pre>
     *     {@code
     *        var fooJar = Files.readAllBytes(Path.of("foo.jar"));
     *        var result = createByteArray(fooJar, "FOO_BYTES");
     *      }
     * </pre>
     *
     * @param bytes A byte array used to create a byte array declaration
     * @param name  Name to be used in the byte array declaration
     * @return The formatted byte array declaration
     */
    public static String createByteArray(byte[] bytes, String name) {
        StringBuilder sb = new StringBuilder(bytes.length * 5);
        Formatter fmt = new Formatter(sb);
        fmt.format("    public static byte[] %s = {", name);
        final int length = 5;
        for (int i = 0; i < bytes.length; i++) {
            if (i % length == 0) {
                fmt.format("%n        ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");
        return sb.toString();
    }

    /**
     * Overridden ZipEntry class which specifies an invalid name for the
     * ZipEntry and will always return null for the Zip entry name
     */
    public static class InvalidZipEntry extends ZipEntry {
        public InvalidZipEntry(String name) {
            super(ZIP_ENTRY_THAT_DOES_NOT_EXIST);
        }
        public String getName() {
            return null;
        }
    }
}
