/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4241361 4842702 4985614 6646605 5032358 6923692 6233323 8144977 8186464 4401122 8322830
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
import java.util.Collections;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

public class ReadZip {

    // ZIP file produced during tests
    private Path zip = Path.of("read-zip.zip");

    /**
     * Create a sample ZIP file for use by tests
     * @param name name of the ZIP file to create
     * @return a sample ZIP file
     * @throws IOException if an unexpected IOException occurs
     */
    private Path createZip(String name) throws IOException {
        Path zip = Path.of(name);

        try (OutputStream out = Files.newOutputStream(zip);
             ZipOutputStream zo = new ZipOutputStream(out)) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            zo.write("hello".getBytes(StandardCharsets.UTF_8));
        }

        return zip;
    }

    /**
     * Delete the ZIP file produced after each test method
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Make sure we throw NPE when calling getEntry or getInputStream with null params
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void nullPointerExceptionOnNullParams() throws IOException {
        zip = createZip("null-params.zip");
        try (ZipFile zf = new ZipFile(zip.toFile())) {

            assertThrows(NullPointerException.class, () -> zf.getEntry(null));
            assertThrows(NullPointerException.class, () -> zf.getInputStream(null));

            // Sanity check that we can still read an entry
            ZipEntry ze = zf.getEntry("file.txt");
            assertNotNull(ze, "cannot read from zip file");
        }
    }

    /**
     * Read the zip file that has some garbage bytes padded at the end
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void bytesPaddedAtEnd() throws IOException {

        zip = createZip("bytes-padded.zip");

        // pad some bytes
        try (OutputStream os = Files.newOutputStream(zip,
                StandardOpenOption.APPEND)) {
            os.write(1);
            os.write(3);
            os.write(5);
            os.write(7);
        }

        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry ze = zf.getEntry("file.txt");
            assertNotNull(ze, "cannot read from zip file");
        }
    }

    /**
     * Verify that we can read a comment from the ZIP
     * file's 'End of Central Directory' header
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readZipFileComment() throws IOException {

        // Create a zip file with a comment in the 'End of Central Directory' header
        try (OutputStream out = Files.newOutputStream(zip);
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
        try (ZipFile zf = new ZipFile(zip.toFile())) {
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
    public void readDirectoryEntries() throws IOException {

        // Create a ZIP containing some directory entries
        try (OutputStream fos = Files.newOutputStream(zip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            // Add a META-INF directory with STORED compression type
            ZipEntry metaInf = new ZipEntry("META-INF/");
            metaInf.setMethod(ZipEntry.STORED);
            metaInf.setSize(0);
            metaInf.setCrc(0);
            zos.putNextEntry(metaInf);

            // Add a regular directory
            ZipEntry dir = new ZipEntry("directory/");
            zos.putNextEntry(dir);
            zos.closeEntry();
        }

        // Verify directory lookups
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            // Look up 'directory/' using the full name
            ZipEntry ze = zf.getEntry("directory/");
            assertNotNull(ze, "read entry \"directory/\" failed");
            assertTrue(ze.isDirectory(), "read entry \"directory/\" failed");
            assertEquals("directory/", ze.getName());

            try (InputStream is = zf.getInputStream(ze)) {
                is.available();
            } catch (Exception x) {
                x.printStackTrace();
            }

            // Look up 'directory/' without the trailing slash
            ze = zf.getEntry("directory");
            assertNotNull(ze, "read entry \"directory\" failed");
            assertTrue(ze.isDirectory(), "read entry \"directory\" failed");
            assertEquals("directory/", ze.getName());

            try (InputStream is = zf.getInputStream(ze)) {
                is.available();
            } catch (Exception x) {
                x.printStackTrace();
            }
            // Sanity check that also META-INF/ can be looked up with or without the trailing slash
            assertNotNull(zf.getEntry("META-INF"));
            assertNotNull(zf.getEntry("META-INF/"));
            assertEquals(zf.getEntry("META-INF").getName(),
                    zf.getEntry("META-INF/").getName());
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
        Map<String, Object> env = Map.of("create", "true", "forceZIP64End", "true");
        try (FileSystem fs = FileSystems.newFileSystem(zip, env)) {
            Files.write(fs.getPath("hello"), "hello".getBytes());
        }
        // Read using ZipFile
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            try (InputStream in = zf.getInputStream(zf.getEntry("hello"))) {
                assertEquals("hello", new String(in.readAllBytes(), StandardCharsets.US_ASCII));
            }
        }
        // Read using ZipFileSystem
        try (FileSystem fs = FileSystems.newFileSystem(zip, Map.of())) {
            assertEquals("hello", new String(Files.readAllBytes(fs.getPath("hello"))));
        }
    }

    /**
     * Read a zip file created via Info-ZIP in streaming mode,
     * which includes a 'Zip64 End of Central Directory header'.
     *
     * @throws IOException if an unexpected IOException occurs
     * @throws InterruptedException if an unexpected InterruptedException occurs
     */
    @Test
    public void readZip64EndInfoZIPStreaming() throws IOException, InterruptedException {
        // ZIP created using: "echo -n hello | zip zip64.zip -"
        // Hex encoded using: "cat zip64.zip | xxd -ps"
        byte[] zipBytes = HexFormat.of().parseHex("""
                  504b03042d0000000000c441295886a61036ffffffffffffffff01001400
                  2d010010000500000000000000050000000000000068656c6c6f504b0102
                  1e032d0000000000c441295886a610360500000005000000010000000000
                  000001000000b011000000002d504b06062c000000000000001e032d0000
                  00000000000000010000000000000001000000000000002f000000000000
                  003800000000000000504b06070000000067000000000000000100000050
                  4b050600000000010001002f000000380000000000
                  """.replaceAll("\n","")
        );

        Files.write(zip, zipBytes);

        try (ZipFile zf = new ZipFile(this.zip.toFile())) {
            try (InputStream in = zf.getInputStream(zf.getEntry("-"))) {
                String contents = new String(in.readAllBytes(), StandardCharsets.US_ASCII);
                assertEquals("hello", contents);
            }
        }
    }

    /**
     * Check that the available() method overriden by the input stream returned by
     * ZipFile.getInputStream correctly returns the number of remaining uncompressed bytes
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void availableShouldReturnRemainingUncompressedBytes() throws IOException {
        // The number of uncompressed bytes to write to the sample ZIP entry
        final int expectedBytes = 512;

        // Create a sample ZIP with deflated entry of a known uncompressed size
        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(zip))) {
            zo.putNextEntry(new ZipEntry("file.txt"));
            zo.write(new byte[expectedBytes]);
        }

        // Verify the behavior of ZipFileInflaterInputStream.available()
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("file.txt");
            try (InputStream in = zf.getInputStream(e)) {
                // Initially, available() should return the full uncompressed size of the entry
                assertEquals(expectedBytes, in.available(),
                        "wrong initial return value of available");

                // Reading a few bytes should reduce the number of available bytes accordingly
                int bytesToRead = 10;
                in.read(new byte[bytesToRead]);
                assertEquals(expectedBytes - bytesToRead, in.available());

                // Reading all remaining bytes should reduce the number of available bytes to zero
                in.transferTo(OutputStream.nullOutputStream());
                assertEquals(0, in.available());

                // available on a closed input stream should return zero
                in.close();
                assertEquals(0, in.available());
            }
        }
    }

    /**
     * Verify that reading an InputStream from a closed ZipFile
     * throws IOException as expected and does not crash the VM.
     * See bugs: 4528128 6846616
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void readAfterClose() throws IOException {
        zip = createZip("read-after-close.zip");
        InputStream in;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry zent = zf.getEntry("file.txt");
            in = zf.getInputStream(zent);
        }

        // zf is closed at this point
        assertThrows(IOException.class,  () -> {
            in.read();
        });
        assertThrows(IOException.class,  () -> {
            in.read(new byte[10]);
        });
        assertThrows(IOException.class,  () -> {
            byte[] buf = new byte[10];
            in.read(buf, 0, buf.length);
        });
        assertThrows(IOException.class,  () -> {
            in.readAllBytes();
        });
    }

    /**
     * Verify that ZipFile can open a ZIP file with zero entries
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void noEntries() throws IOException {
        // Create a ZIP file with no entries
        try (ZipOutputStream zo = new ZipOutputStream(Files.newOutputStream(zip))) {
        }

        // Open the "empty" ZIP file
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            // Verify size
            assertEquals(0, zf.size());

            // Verify entry lookup using ZipFile.getEntry()
            assertNull(zf.getEntry("file.txt"));

            // Verify iteration using ZipFile.entries()
            assertEquals(Collections.emptyList(), Collections.list(zf.entries()));

            // Verify iteration using ZipFile.stream()
            assertEquals(Collections.emptyList(), zf.stream().toList());
        }
    }
}