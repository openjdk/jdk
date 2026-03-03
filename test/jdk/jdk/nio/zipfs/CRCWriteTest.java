/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

/**
 * @test
 * @bug 8232879
 * @summary Test OutputStream::write with Zip FS
 * @modules jdk.zipfs
 * @run junit/othervm CRCWriteTest
 */
public class CRCWriteTest {

    // Jar File path used by the test
    private final Path JAR_FILE = Path.of("CRCWrite.jar");

    /**
     * Validate that an OutputStream obtained for the Zip FileSystem
     * can be used successfully with the OutputStream write methods
     */
    @Test
    public void zipFsOsDeflatedWriteTest() throws Exception {
        Files.deleteIfExists(JAR_FILE);
        String[] msg = {"Hello ", "Tennis Anyone", "!!!!"};
        Entry e0 = Entry.of("Entry-0", ZipEntry.DEFLATED, String.join("",msg));

        try (FileSystem zipfs = FileSystems.newFileSystem(JAR_FILE,
                Map.of("create", "true"))) {

            // Write to the Jar file using the various OutputStream write methods
            try (OutputStream os = Files.newOutputStream(zipfs.getPath(e0.name))) {
                for (byte b : msg[0].getBytes(StandardCharsets.UTF_8)) {
                    os.write(b);
                }
                os.write(msg[1].getBytes(StandardCharsets.UTF_8));
                os.write(msg[2].getBytes(StandardCharsets.UTF_8), 0, msg[2].length());
            }
        }
        verify(JAR_FILE, e0);
    }

    /**
     * Represents an entry in a Zip file. An entry encapsulates a name, a
     * compression method, and its contents/data.
     */
    static class Entry {
        private final String name;
        private final int method;
        private final byte[] bytes;

        Entry(String name, int method, String contents) {
            this.name = name;
            this.method = method;
            this.bytes = contents.getBytes(StandardCharsets.UTF_8);
        }

        static Entry of(String name, int method, String contents) {
            return new Entry(name, method, contents);
        }

        /**
         * Returns a new Entry with the same name and compression method as this
         * Entry but with the given content.
         */
        Entry content(String contents) {
            return new Entry(name, method, contents);
        }
    }

    /**
     * Verify that the given path is a Zip file containing exactly the
     * given entries.
     */
    private static void verify(Path zipfile, Entry... entries) throws IOException {

        // check entries with ZipFile API
        try (ZipFile zf = new ZipFile(zipfile.toFile())) {
            // check entry count
            assertEquals(zf.size(), entries.length);

            // Check compression method and content of each entry
            for (Entry e : entries) {
                ZipEntry ze = zf.getEntry(e.name);
                assertNotNull(ze);
                assertEquals(ze.getMethod(), e.method);
                try (InputStream in = zf.getInputStream(ze)) {
                    byte[] bytes = in.readAllBytes();
                    assertArrayEquals(bytes, e.bytes);
                }
            }
        }

        // Check entries using ZipInputStream
        for (Entry e : entries) {
            try (ZipInputStream zis =
                         new ZipInputStream(new FileInputStream(zipfile.toFile()))) {
                ZipEntry ze;
                while ((ze = zis.getNextEntry()) != null) {
                    assertEquals(ze.getMethod(), e.method);
                    byte[] bytes = zis.readAllBytes();
                    assertArrayEquals(bytes, e.bytes);
                }
            }
        }

        // Check entries with FileSystem API
        try (FileSystem fs = FileSystems.newFileSystem(zipfile)) {
            // check entry count
            Path top = fs.getPath("/");
            long count = Files.find(top, Integer.MAX_VALUE,
                    (path, attrs) -> attrs.isRegularFile()).count();
            assertEquals(count, entries.length);

            // check content of each entry
            for (Entry e : entries) {
                Path file = fs.getPath(e.name);
                byte[] bytes = Files.readAllBytes(file);
                assertArrayEquals(bytes, e.bytes);
            }
        }
    }
}


