/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4241361 4842702 4985614 6646605 5032358 6923692 6233323 8144977 8186464
   @summary Make sure we can read a zip file.
   @modules jdk.zipfs
   @run junit ReadZip
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

public class ReadZip {

    // Binary test vector ZIP used by various tests
    private Path inputZip = Path.of(System.getProperty("test.src", "."))
            .resolve("input.zip");
    // Output file produced during tests
    private Path outputZip = Path.of("output.zip");

    /**
     * Make sure we throw NPE calling getEntry or getInputStream with null params
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void nullPointerExceptionOnNullParams() throws IOException {
        try (ZipFile zf = new ZipFile(inputZip.toFile())) {

            assertThrows(NullPointerException.class, () -> zf.getEntry(null));
            assertThrows(NullPointerException.class, () -> zf.getInputStream(null));

            // Sanity check that we can still read an entry
            ZipEntry ze = zf.getEntry("ReadZip.java");
            assertNotNull(ze, "cannot read from zip file");
        }
    }

    /**
     * Read the zip file that has some garbage bytes padded at the end.
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void bytesPaddedAtEnd() throws IOException {

        Files.copy(inputZip, outputZip, StandardCopyOption.REPLACE_EXISTING);

        outputZip.toFile().setWritable(true);

        // pad some bytes
        try (OutputStream os = Files.newOutputStream(outputZip,
                StandardOpenOption.APPEND)) {
            os.write(1);
            os.write(3);
            os.write(5);
            os.write(7);
        }

        try (ZipFile zf = new ZipFile(outputZip.toFile())) {
            ZipEntry ze = zf.getEntry("ReadZip.java");
            assertNotNull(ze, "cannot read from zip file");
        }
    }

    /**
     * Read a comment from the ZIP file's 'End of Central Directory' header
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readZipFileComment() throws IOException {

        // Create a zip file with an entry including a comment
        try (OutputStream out = Files.newOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(out)) {
            ZipEntry ze = new ZipEntry("ZipEntry");
            zos.putNextEntry(ze);
            zos.write(1);
            zos.write(2);
            zos.write(3);
            zos.write(4);
            zos.closeEntry();
            zos.setComment("This is the comment for testing");
        }

        // Read zip file comment
        try (ZipFile zf = new ZipFile(outputZip.toFile())) {
            ZipEntry ze = zf.getEntry("ZipEntry");
            assertNotNull(ze, "cannot read entry from zip file");
            assertEquals("This is the comment for testing", zf.getComment());
        }
    }

    /**
     * Verify that a directory entry can be found using the
     * name 'directory/' as well as 'directory/'
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readDirectoryEntry() throws IOException {

        try (OutputStream fos = Files.newOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry ze = new ZipEntry("directory/");
            zos.putNextEntry(ze);
            zos.closeEntry();
        }

        try (ZipFile zf = new ZipFile(outputZip.toFile())) {
            ZipEntry ze = zf.getEntry("directory/");
            assertNotNull(ze, "read entry \"directory/\" failed");
            assertTrue(ze.isDirectory(), "read entry \"directory/\" failed");

            try (InputStream is = zf.getInputStream(ze)) {
                is.available();
            } catch (Exception x) {
                x.printStackTrace();
            }

            ze = zf.getEntry("directory");
            assertNotNull(ze, "read entry \"directory\" failed");
            assertTrue(ze.isDirectory(), "read entry \"directory\" failed");
            try (InputStream is = zf.getInputStream(ze)) {
                is.available();
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    /**
     * Throw a NoSuchFileException exception when reading a non-existing zip file
     */
    @Test
    public void nonExistingFile() {
        File nonExistingFile = new File("non-existing-file-f6804460f.zip");
        assertThrows(NoSuchFileException.class, () ->
                new ZipFile(nonExistingFile));
    }

    /**
     * Read a Zip file with a 'Zip64 End of Central Directory header' which was created
     * using ZipFileSystem with the 'forceZIP64End' option.
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readZip64EndZipFs() throws IOException {

        // Create zip file with Zip64 end
        URI uri = URI.create("jar:" + outputZip.toUri());
        Map<String, Object> env = Map.of("create", "true", "forceZIP64End", "true");
        try (FileSystem fs = FileSystems.newFileSystem(uri, env)) {
            Files.write(fs.getPath("hello"), "hello".getBytes());
        }
        // Read using ZipFile
        try (ZipFile zf = new ZipFile(outputZip.toFile())) {
            try (InputStream in = zf.getInputStream(zf.getEntry("hello"))) {
                assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            }
        }
        // Read using ZipFileSystem
        try (FileSystem fs = FileSystems.newFileSystem(uri, Map.of())) {
            assertEquals("hello", new String(Files.readAllBytes(fs.getPath("hello"))));
        }
    }

    /**
     * Read a zip file created via "echo hello | zip dst.zip -",
     * which includes a 'Zip64 End of Central Directory header'
     *
     * @throws IOException if an unexpected IOException occurs
     * @throws InterruptedException if an unexpected InterruptedException occurs
     */
    @Test
    public void readZip64EndZipProcess() throws IOException, InterruptedException {
        if (Files.notExists(Paths.get("/usr/bin/zip"))) {
            return;
        }

        Process zip = new ProcessBuilder("zip", outputZip.toString(), "-").start();
        OutputStream os = zip.getOutputStream();
        os.write("hello".getBytes(US_ASCII));
        os.close();
        zip.waitFor();
        if (zip.exitValue() == 0 && Files.exists(outputZip)) {
            try (ZipFile zf = new ZipFile(outputZip.toFile())) {
                try (InputStream in = zf.getInputStream(zf.getEntry("-"))) {
                    String contents = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
                    assertEquals("hello", contents);
                }
            }
        }
    }

    /*
     * Delete the ZIP file produced after each test method
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(outputZip);
    }
}
