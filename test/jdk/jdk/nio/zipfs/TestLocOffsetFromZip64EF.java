/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.testng.Assert.assertEquals;

/**
 * @test
 * @bug 8255380 8257445
 * @summary Test that Zip FS can access the LOC offset from the Zip64 extra field
 * @modules jdk.zipfs
 * @run testng TestLocOffsetFromZip64EF
 */
public class TestLocOffsetFromZip64EF {

    private static final String ZIP_FILE_NAME = "LocOffsetFromZip64.zip";

    // Size of a Zip64 extended information field
    public static final int ZIP64_SIZE = Short.BYTES // Tag
            + Short.BYTES    // Data size
            + Long.BYTES     // Uncompressed size
            + Long.BYTES     // Compressed size
            + Long.BYTES     // Loc offset
            + Integer.BYTES; // Start disk

    /**
     * Create the files used by this test
     *
     * @throws IOException if an error occurs
     */
    @BeforeClass
    public void setUp() throws IOException {
        cleanup();
        createZipWithZip64Ext();
    }

    /**
     * Delete files used by this test
     * @throws IOException if an error occurs
     */
    @AfterClass
    public void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIP_FILE_NAME));
    }

    /*
     * DataProvider used to verify that a Zip file that contains a Zip64 Extra
     * (EXT) header can be traversed
     */
    @DataProvider(name = "zipInfoTimeMap")
    protected Object[][] zipInfoTimeMap() {
        return new Object[][]{
                {Map.of()},
                {Map.of("zipinfo-time", "False")},
                {Map.of("zipinfo-time", "true")},
                {Map.of("zipinfo-time", "false")}
        };
    }

    /**
     * Navigate through the Zip file entries using Zip FS
     * @param env Zip FS properties to use when accessing the Zip file
     * @throws IOException if an error occurs
     */
    @Test(dataProvider = "zipInfoTimeMap")
    public void walkZipFSTest(final Map<String, String> env) throws IOException {
        try (FileSystem fs =
                     FileSystems.newFileSystem(Paths.get(ZIP_FILE_NAME), env)) {
            for (Path root : fs.getRootDirectories()) {
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes
                            attrs) throws IOException {
                        System.out.println(Files.readAttributes(file,
                                BasicFileAttributes.class).toString());
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
    }

    /**
     * Navigate through the Zip file entries using ZipFile
     * @throws IOException if an error occurs
     */
    @Test
    public void walkZipFileTest() throws IOException {
        try (ZipFile zip = new ZipFile(ZIP_FILE_NAME)) {
            zip.stream().forEach(z -> System.out.printf("%s, %s, %s%n",
                    z.getName(), z.getMethod(), z.getLastModifiedTime()));
        }
    }

    /**
     * This produces a ZIP with similar features as the one created by 'Info-ZIP' which
     * caused 'Extended timestamp' parsing to fail before JDK-8255380.
     *
     * The issue was sensitive to the ordering of 'Info-ZIP extended timestamp' fields and
     * 'Zip64 extended information' fields. ZipOutputStream and 'Info-ZIP' order these differently.
     *
     * ZipFileSystem tried to read the Local file header while parsing the extended timestamp,
     * but if the Zip64 extra field was not read yet, ZipFileSystem would incorrecly try to read
     * the Local File header from offset 0xFFFFFFFF.
     *
     * This method creates a ZIP file which includes a CEN with the following features:
     *
     * - Its extra field has a 'Info-ZIP extended timestamp' field followed by a
     *   'Zip64 extended information' field.
     * - The sizes and offset fields values of the CEN are set to 0xFFFFFFFF (Zip64 magic values)
     *
     */
    public void createZipWithZip64Ext() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zo = new ZipOutputStream(out)) {

            ZipEntry e = new ZipEntry("entry");
            // Make it STORED and empty to simplify parsing
            e.setMethod(ZipEntry.STORED);
            e.setSize(0);
            e.setCrc(0);

            // Make ZipOutputStream output an 'Info-Zip extended timestamp' extra field
            e.setLastModifiedTime(FileTime.from(Instant.now()));
            e.setLastAccessTime(FileTime.from(Instant.now()));

            // Add an opaque extra field, right-sized for a Zip64 extended field
            // We'll update this below
            byte[] zip64 = new byte[ZIP64_SIZE];
            ByteBuffer buffer = ByteBuffer.wrap(zip64).order(ByteOrder.LITTLE_ENDIAN);
            buffer.putShort((short) 0x42); // Opaque tag makes ZipEntry.setExtra ignore it
            buffer.putShort((short) (zip64.length - 2 * Short.BYTES)); // Data size
            e.setExtra(zip64);

            zo.putNextEntry(e);
        }

        byte[] zip = out.toByteArray();

        // ZIP now has the right structure, but we need to update the CEN to Zip64 format
        updateToZip64(zip);
        // Write the ZIP to disk
        Files.write(Path.of(ZIP_FILE_NAME), zip);
    }

    /**
     * Update the CEN record to Zip64 format
     */
    private static void updateToZip64(byte[] bytes) throws IOException {

        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        // Local header name length
        short nlenLoc = buffer.getShort(ZipFile.LOCNAM);
        // Local header extra field length
        short elenLoc = buffer.getShort(ZipFile.LOCEXT);

        // Offset of the CEN header
        int cenOff = ZipFile.LOCHDR + nlenLoc + elenLoc;

        // Read name, extra field and comment lengths
        short nlen = buffer.getShort(cenOff + ZipFile.CENNAM);
        short clen = buffer.getShort(cenOff + ZipFile.CENCOM);
        short elen = buffer.getShort(cenOff + ZipFile.CENEXT);

        // Update CEN sizes and loc offset to 0xFFFFFFFF
        buffer.putInt(cenOff + ZipFile.CENLEN, 0XFFFFFFFF);
        buffer.putInt(cenOff + ZipFile.CENSIZ, 0XFFFFFFFF);
        buffer.putInt(cenOff + ZipFile.CENOFF, 0xFFFFFFFF);

        // Offset of the extra fields
        int extraOff = cenOff + ZipFile.CENHDR + nlen;

        // Position at the start of the Zip64 extra field
        buffer.position(extraOff + elen - ZIP64_SIZE);

        // Update the Zip64 field with real values
        buffer.putShort((short) 0x1); //  Tag for Zip64
        buffer.getShort(); // Data size is good
        buffer.putLong(0); // Uncompressed size
        buffer.putLong(0); // Compressed size
        buffer.putLong(0); // loc offset
        buffer.putInt(0);  // Set disk start
    }
}
