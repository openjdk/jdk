/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

/**
 * @test
 * @bug 8253952
 * @summary Test behaviour when copying ZipEntries between zip files.
 * @run junit CopyZipFile
 */

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.*;

public class CopyZipFile {
    // ZIP file created in this test
    private Path zip = Path.of("first.zip");
    // The content to put in each entry
    private static final byte[] TEST_STRING = "TestTestTest".getBytes(StandardCharsets.UTF_8);

    /**
     * Create the sample ZIP file used in this test, including a STORED entry
     * and DEFLATE entries with various compression levels.
     * @throws IOException if an unexpected IOException occurs
     */
    @BeforeEach
    public void createZip() throws IOException {
        // By default, ZipOutputStream creates zip files with Local File Headers
        // without size, compressed size and crc values and an extra Data
        // Descriptor (see https://en.wikipedia.org/wiki/Zip_(file_format)
        // after the data belonging to that entry with these values if in the
        // corresponding ZipEntry one of the size, compressedSize or crc fields is
        // equal to '-1' (which is the default for newly created ZipEntries).
        try (OutputStream os = Files.newOutputStream(zip) ;
             ZipOutputStream zos = new ZipOutputStream(os)) {
            // First file will be compressed with DEFAULT_COMPRESSION (i.e. -1 or 6)
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);
            zos.putNextEntry(new ZipEntry("DEFAULT_COMPRESSION.txt"));
            zos.write(TEST_STRING);

            // Second file won't be compressed at all (i.e. STORED)
            zos.setMethod(ZipOutputStream.STORED);
            ZipEntry ze = new ZipEntry("STORED.txt");
            ze.setSize(TEST_STRING.length);
            ze.setCompressedSize(TEST_STRING.length);
            CRC32 crc = new CRC32();
            crc.update(TEST_STRING);
            ze.setCrc(crc.getValue());
            zos.putNextEntry(ze);
            zos.write(TEST_STRING);

            // Third file will be compressed with NO_COMPRESSION (i.e. 0)
            zos.setMethod(ZipOutputStream.DEFLATED);
            zos.setLevel(Deflater.NO_COMPRESSION);
            zos.putNextEntry(new ZipEntry("NO_COMPRESSION.txt"));
            zos.write(TEST_STRING);

            // Fourth file will be compressed with BEST_SPEED (i.e. 1)
            zos.setLevel(Deflater.BEST_SPEED);
            zos.putNextEntry(new ZipEntry("BEST_SPEED.txt"));
            zos.write(TEST_STRING);

            // Fifth file will be compressed with BEST_COMPRESSION (i.e. 9)
            zos.setLevel(Deflater.BEST_COMPRESSION);
            zos.putNextEntry(new ZipEntry("BEST_COMPRESSION.txt"));
            zos.write(TEST_STRING);
        }
    }

    /**
     * Delete the ZIP file produced by this test
     * @throws IOException if an unexpected IOException occurs
     */
    @AfterEach
    public void cleanup() throws IOException {
        Files.deleteIfExists(zip);
    }

    /**
     * Read all entries using ZipInputStream.getNextEntry and copy them
     * to a new zip file using ZipOutputStream.putNextEntry. This only works
     * reliably because the input zip file has no values for the size, compressedSize
     * and crc values of streamed zip entries in the local file header and
     * therefore the ZipEntry objects created by ZipOutputStream.getNextEntry
     * will have all these fields set to '-1'.
     *
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void copyFromZipInputStreamToZipOutputStream() throws IOException {

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip));
             ZipOutputStream zos = new ZipOutputStream(OutputStream.nullOutputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // ZipInputStream.getNextEntry() only reads the Local File Header of a zip entry,
                // so for the zip file we've just generated the ZipEntry fields 'size', 'compressedSize`
                // and 'crc' for deflated entries should be uninitialized (i.e. '-1').
                System.out.println(
                        String.format("name=%s, clen=%d, len=%d, crc=%d",
                                entry.getName(), entry.getCompressedSize(), entry.getSize(), entry.getCrc()));
                if (entry.getMethod() == ZipEntry.DEFLATED) {
                    // Expect size, compressed size and crc to not be initialized at this point
                    assertEquals(-1, entry.getCompressedSize());
                    assertEquals(-1, entry.getSize());
                    assertEquals(-1, entry.getCrc());
                }
                zos.putNextEntry(entry);
                zis.transferTo(zos);
                // After all the data belonging to a zip entry has been inflated (i.e. after ZipInputStream.read()
                // returned '-1'), it is guaranteed that the ZipInputStream will also have consumed the Data
                // Descriptor (if any) after the data and will have updated the 'size', 'compressedSize' and 'crc'
                // fields of the ZipEntry object.
                System.out.println(
                        String.format("name=%s, clen=%d, len=%d, crc=%d\n",
                                entry.getName(), entry.getCompressedSize(), entry.getSize(), entry.getCrc()));
                // Expect size, compressed size and crc to be initialized at this point
                assertNotEquals(-1, entry.getCompressedSize());
                assertNotEquals(-1, entry.getSize());
                assertNotEquals(-1, entry.getCrc());
            }
        }
    }

    /**
     * Read all entries using the ZipFile class and copy them to a new zip file
     * using ZipOutputStream.putNextEntry.
     * The ZipFile class reads all the zip entries from the Central
     * Directory, which has accurate information for size, compressedSize and crc.
     * This means that all ZipEntry objects returned from ZipFile will have correct
     * settings for these fields.
     * If the compression level was different in the input zip file (which we can't know
     * because the zip file format doesn't record this information), the
     * size of the re-compressed entry we are writing to the ZipOutputStream might differ
     * from the original compressed size recorded in the ZipEntry. This would result in an
     * "invalid entry compressed size" ZipException if ZipOutputStream wouldn't ignore
     * the implicitely set compressed size attribute of ZipEntries read from a ZipFile
     * or ZipInputStream.
     * @throws IOException if an unexpected IOException occurs
     */
    @Test
    public void copyFromZipFileToZipOutputStream() throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(OutputStream.nullOutputStream());
             ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry entry;
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                entry = entries.nextElement();
                System.out.println(
                    String.format("name=%s, clen=%d, len=%d, crc=%d\n",
                                  entry.getName(), entry.getCompressedSize(),
                                  entry.getSize(), entry.getCrc()));
                // Expect size, compressed size and crc to be initialized at this point
                assertNotEquals(-1, entry.getCompressedSize());
                assertNotEquals(-1, entry.getSize());
                assertNotEquals(-1, entry.getCrc());

                zos.putNextEntry(entry);
                try (InputStream is = zf.getInputStream(entry)) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    /**
     * If the compressed size is set explicitly using ZipEntry.setCompressedSize(),
     * then the entry will be restreamed with a data descriptor and the compressed size
     * recomputed. If the source compression level was different from the target compression
     * level, the compressed sizes may differ and a ZipException will be thrown
     * when the entry is closed in ZipOutputStream.closeEntry
     *
     * @throws IOException if an unexpected IOException is thrown
     */
    @Test
    public void explicitCompressedSizeWithDifferentCompressionLevels() throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(OutputStream.nullOutputStream());
             ZipFile zf = new ZipFile(zip.toFile())) {
            // Be explicit about the default compression level
            zos.setLevel(Deflater.DEFAULT_COMPRESSION);

            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // Explicitly setting the compressed size will disable data descriptors
                // and enable validation that the compressed size in the ZipEntry matches the
                // actual compressed size written by ZipOutputStream
                entry.setCompressedSize(entry.getCompressedSize());

                try (InputStream is = zf.getInputStream(entry)) {
                    zos.putNextEntry(entry);
                    is.transferTo(zos);
                    // Some compression levels lead to unexpected recompressed sizes when closing the entry
                    switch (entry.getName()) {
                        case "DEFAULT_COMPRESSION.txt" -> {
                            // DEFAULT_COMPRESSION matches expected size
                            zos.closeEntry();
                        }
                        case "STORED.txt" -> {
                            // STORED should not throw
                            zos.closeEntry();
                        }
                        case "NO_COMPRESSION.txt", "BEST_SPEED.txt" -> {
                            // NO_COMPRESSION and BEST_SPEED should lead to an unexpected recompressed size
                            ZipException ze = assertThrows(ZipException.class, () -> {
                                zos.closeEntry();
                            });

                            // Hack to fix and close the offending zip entry with the correct recompressed size.
                            // The exception message is something like:
                            //   "invalid entry compressed size (expected 12 but got 7 bytes)"
                            // and we need to extract the second integer.
                            Pattern cSize = Pattern.compile("\\d+");
                            Matcher m = cSize.matcher(ze.getMessage());
                            m.find();
                            m.find();
                            entry.setCompressedSize(Integer.parseInt(m.group()));
                            zos.closeEntry();
                        }
                        case "BEST_COMPRESSION.txt" -> {
                            // BEST_COMPRESSION produces the same compressed
                            // size as DEFAULT_COMPRESSION for sample content
                            zos.closeEntry();
                        }
                        default -> {
                            throw new IllegalArgumentException("Unexpected entry " + entry.getName());
                        }
                    }
                }
            }
        }
    }
}
