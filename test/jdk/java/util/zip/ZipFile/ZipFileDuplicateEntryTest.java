/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Formatter;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @test
 * @bug 8276123
 * @summary ZipFile::getEntry will not return a file entry when there is a
 * directory entry of the same name within a Zip File
 * @run junit/othervm ZipFileDuplicateEntryTest
 */
public class ZipFileDuplicateEntryTest {

    /**
     * Name to use for creating Zip entries
     */
    private static final String ENTRY_NAME = "entry";

    /**
     * Zip and Jar files to be created
     */
    private static final Path ZIP_FILE = Paths.get("fileDirEntry.zip");
    private static final Path ZIP_FILE2 = Paths.get("OnlyDirEntry.zip");
    private static final Path DUPLICATE_FILE_ENTRY_FILE = Paths.get("DupFIleEntry.zip");
    private static final Path TEST_JAR = Paths.get("fileDirEntry.jar");

    /**
     * Directory entry added to the Zip File.
     */
    private static final Entry DIR_ENTRY =
            Entry.of(ENTRY_NAME + "/", ZipEntry.DEFLATED,
                    "I am a Directory");

    /**
     * File entry added to the Zip File.
     */
    private static final Entry FILE_ENTRY =
            Entry.of(ENTRY_NAME, ZipEntry.DEFLATED, "I am a File");

    /**
     * Duplicate File entry added to the Zip file. This is the 2nd entry added
     * to the Zip file and is expected to be returned.
     */
    private static final Entry DUPLICATE_FILE_ENTRY =
            Entry.of(ENTRY_NAME, ZipEntry.DEFLATED, "Yet another File");
    /**
     * Entries expected to be returned via ZipFile::stream
     */
    private static final List<String> EXPECTED_ENTRIES =
            Arrays.asList(FILE_ENTRY.name, DIR_ENTRY.name);

    /**
     * Max buffer size for readAllBytes method which can be used when
     * InputStream::readAllBytes is not available
     */
    private static final int MAX_BUFFER_SIZE = 1024;

    /**
     * Flag to enable test output
     */
    private static final boolean DEBUG = false;

    /**
     * Array representing a Jar File with the entries:
     * Name: entry, contents: "I am a File"
     * Name: entry, contents: "Yet another File"
     * See createByteArray()
     */
    private static final byte[] DUPLICATE_ENTRY_JAR_BYTES = {
            (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x8,
            (byte) 0x8, (byte) 0x0, (byte) 0x60, (byte) 0x59, (byte) 0x55, (byte) 0x53, (byte) 0x8e, (byte) 0x39,
            (byte) 0x14, (byte) 0x49, (byte) 0xd, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xb, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x65, (byte) 0x6e,
            (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x1, (byte) 0x0, (byte) 0x10, (byte) 0x0, (byte) 0xb,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xd,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xf3,
            (byte) 0x54, (byte) 0x48, (byte) 0xcc, (byte) 0x55, (byte) 0x48, (byte) 0x54, (byte) 0x70, (byte) 0xcb,
            (byte) 0xcc, (byte) 0x49, (byte) 0x5, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x3, (byte) 0x4,
            (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x60, (byte) 0x59,
            (byte) 0x55, (byte) 0x53, (byte) 0xe1, (byte) 0x4c, (byte) 0x29, (byte) 0xa4, (byte) 0x12, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x10, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x5, (byte) 0x0,
            (byte) 0x14, (byte) 0x0, (byte) 0x65, (byte) 0x6e, (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x1,
            (byte) 0x0, (byte) 0x10, (byte) 0x0, (byte) 0x10, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x12, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x8b, (byte) 0x4c, (byte) 0x2d, (byte) 0x51, (byte) 0x48,
            (byte) 0xcc, (byte) 0xcb, (byte) 0x2f, (byte) 0xc9, (byte) 0x48, (byte) 0x2d, (byte) 0x52, (byte) 0x70,
            (byte) 0xcb, (byte) 0xcc, (byte) 0x49, (byte) 0x5, (byte) 0x0, (byte) 0x50, (byte) 0x4b, (byte) 0x1,
            (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0, (byte) 0x0, (byte) 0x8, (byte) 0x8,
            (byte) 0x0, (byte) 0x60, (byte) 0x59, (byte) 0x55, (byte) 0x53, (byte) 0x8e, (byte) 0x39, (byte) 0x14,
            (byte) 0x49, (byte) 0xd, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0xb, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x65, (byte) 0x6e, (byte) 0x74, (byte) 0x72, (byte) 0x79,
            (byte) 0x50, (byte) 0x4b, (byte) 0x1, (byte) 0x2, (byte) 0x14, (byte) 0x0, (byte) 0x14, (byte) 0x0,
            (byte) 0x0, (byte) 0x8, (byte) 0x8, (byte) 0x0, (byte) 0x60, (byte) 0x59, (byte) 0x55, (byte) 0x53,
            (byte) 0xe1, (byte) 0x4c, (byte) 0x29, (byte) 0xa4, (byte) 0x12, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x10, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x5, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x44, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x65, (byte) 0x6e,
            (byte) 0x74, (byte) 0x72, (byte) 0x79, (byte) 0x50, (byte) 0x4b, (byte) 0x5, (byte) 0x6, (byte) 0x0,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0x2, (byte) 0x0, (byte) 0x66,
            (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x8d, (byte) 0x0, (byte) 0x0, (byte) 0x0, (byte) 0x0,
            (byte) 0x0,
    };

    /**
     * Create Zip files used by the tests.
     *
     * @throws IOException If an error occurs
     */
    @BeforeAll
    public static void setup() throws IOException {

        /**
         *  Zip contains two entries named "entry" and "entry/"
         */
        Files.deleteIfExists(ZIP_FILE);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(ZIP_FILE))) {
            zos.putNextEntry(new ZipEntry(FILE_ENTRY.name));
            zos.write(FILE_ENTRY.bytes);
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry(DIR_ENTRY.name));
            zos.write(DIR_ENTRY.bytes);
            zos.closeEntry();
        }

        /**
         *  Jar contains two entries named "entry" and "entry/"
         */
        Files.deleteIfExists(TEST_JAR);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(TEST_JAR))) {
            jos.putNextEntry(new JarEntry(FILE_ENTRY.name));
            jos.write(FILE_ENTRY.bytes);
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(DIR_ENTRY.name));
            jos.write(DIR_ENTRY.bytes);
            jos.closeEntry();
        }

        /**
         *  Zip contains the entry "entry/"
         */
        Files.deleteIfExists(ZIP_FILE2);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(ZIP_FILE2))) {
            zos.putNextEntry(new ZipEntry(DIR_ENTRY.name));
            zos.write(DIR_ENTRY.bytes);
            zos.closeEntry();
        }

        /**
         *  Create a Jar that contains two entries named "entry"
         */
        Files.deleteIfExists(DUPLICATE_FILE_ENTRY_FILE);
        Files.write(DUPLICATE_FILE_ENTRY_FILE, DUPLICATE_ENTRY_JAR_BYTES);
    }

    /**
     * Clean up after the test run
     *
     * @throws IOException If an error occurs
     */
    @AfterAll
    public static void cleanup() throws IOException {
        Files.deleteIfExists(ZIP_FILE);
        Files.deleteIfExists(ZIP_FILE2);
        Files.deleteIfExists(DUPLICATE_FILE_ENTRY_FILE);
        Files.deleteIfExists(TEST_JAR);
    }

    /**
     * MethodSource used to specify the Zip entries to use
     *
     * @return Stream that indicates which Entry to use within the test
     */
    public static Stream<Entry> entries() {
        return Stream.of(FILE_ENTRY, DIR_ENTRY);
    }

    /**
     * Test whether ZipFile::getEntry can find a directory entry within a Zip
     * file specifying "name" vs "name/"
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void readDirWithoutSlash() throws IOException {
        System.out.printf("%n%n**** readDirWithoutSlash ***%n");
        try (ZipFile zip = new ZipFile(ZIP_FILE2.toString())) {
            ZipEntry ze = zip.getEntry(ENTRY_NAME);
            if (DEBUG) {
                System.out.printf("    Entry:%s, found:%s%n", ENTRY_NAME, ze != null);
            }
            assertNotNull(ze);
            assertTrue(ze.isDirectory());
            try (InputStream in = zip.getInputStream(ze)) {
                byte[] bytes = in.readAllBytes();
                if (DEBUG) {
                    System.out.printf("name: %s, isDirectory: %s, payload= %s%n",
                            ze.getName(), ze.isDirectory(), new String(bytes));
                }
                assertArrayEquals(DIR_ENTRY.bytes, bytes,
                        String.format("Expected payload: %s",
                                new String(DIR_ENTRY.bytes)));
            }
        }
    }

    /**
     * Validate that ZipFile::getEntry will return the correct entry when a file
     * and directory have the same name
     *
     * @param entry The entry to search for
     * @throws IOException If an error occurs
     */
    @ParameterizedTest
    @MethodSource("entries")
    public void testSameFileDirEntryName(Entry entry) throws IOException {
        System.out.printf("%n%n**** testSameFileDirEntryName ***%n");

        try (ZipFile zip = new ZipFile(ZIP_FILE.toString())) {
            ZipEntry ze = zip.getEntry(entry.name);
            if (DEBUG) {
                System.out.printf("    Entry:%s, found:%s%n", entry.name, ze != null);
            }
            assertNotNull(ze);
            try (InputStream in = zip.getInputStream(ze)) {
                byte[] bytes = in.readAllBytes();
                if (DEBUG) {
                    System.out.printf("name: %s, isDirectory: %s, payload= %s%n",
                            ze.getName(), ze.isDirectory(), new String(bytes));
                }
                assertArrayEquals(entry.bytes, bytes,
                        String.format("Expected payload: %s", new String(entry.bytes)));
            }
        }
    }

    /**
     * Validate that ZipFile::getEntry will return the correct entry, which
     * is the second entry, when there are duplicate entries within the Zip file.
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void DupFileEntryTest() throws IOException {
        System.out.printf("%n%n**** DupFileEntryTest ***%n");
        try (ZipFile zip =
                     new ZipFile(DUPLICATE_FILE_ENTRY_FILE.toString())) {
            ZipEntry ze = zip.getEntry(ENTRY_NAME);
            if (DEBUG) {
                System.out.printf("    Entry:%s, found:%s%n", ENTRY_NAME, ze != null);
            }
            assertNotNull(ze);
            try (InputStream in = zip.getInputStream(ze)) {
                byte[] bytes = in.readAllBytes();
                if (DEBUG) {
                    System.out.printf("name: %s, isDirectory: %s, payload= %s%n",
                            ze.getName(), ze.isDirectory(), new String(bytes));
                }
                assertArrayEquals(DUPLICATE_FILE_ENTRY.bytes, bytes,
                        String.format("Expected payload: %s", new String(DUPLICATE_FILE_ENTRY.bytes)));
            }
        }
    }

    /**
     * Verify that ZipInputStream can be used to read all Zip entries including
     * a file and directory entry with the same name
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void ZipInputStreamTest() throws IOException {
        System.out.printf("%n%n**** ZipInputStreamTest ***%n");
        try (ZipInputStream zis = new ZipInputStream(
                new FileInputStream(ZIP_FILE.toFile()))) {
            ZipEntry zipEntry = zis.getNextEntry();
            assertNotNull(zipEntry);
            while (zipEntry != null) {
                Entry e;
                if (zipEntry.getName().equals(FILE_ENTRY.name)) {
                    e = FILE_ENTRY;
                } else if (zipEntry.getName().equals(DIR_ENTRY.name)) {
                    e = DIR_ENTRY;
                } else {
                    throw new RuntimeException(
                            String.format("Invalid Zip entry: %s", zipEntry.getName()));
                }
                assertEquals(e.method, zipEntry.getMethod());
                assertArrayEquals(e.bytes, zis.readAllBytes(),
                        String.format("Expected payload: %s", new String(e.bytes)));
                zipEntry = zis.getNextEntry();
            }
        }
    }

    /**
     * Verify that ZipFile::stream returns all Zip entries including
     * a file and directory entry with the same name
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void ZipFileStreamTest() throws IOException {
        System.out.printf("%n%n**** ZipFileStreamTest ***%n");
        try (ZipFile zf = new ZipFile(ZIP_FILE.toFile())) {
            List<? extends ZipEntry> entries = zf.stream().collect(Collectors.toList());
            assertEquals(EXPECTED_ENTRIES.size(), entries.size());
            for (ZipEntry e : entries) {
                assertTrue(EXPECTED_ENTRIES.contains(e.getName()));
            }
        }
    }

    /**
     * Verify that JarFile can be used to read all the entries including
     * a file and directory entry with the same name
     *
     * @param entry The entry to validate
     * @throws IOException If an error occurs
     */
    @ParameterizedTest
    @MethodSource("entries")
    public void JarFileInputStreamTest(Entry entry) throws IOException {
        System.out.printf("%n%n**** JarFileInputStreamTest ***%n");
        try (JarFile jarFile = new JarFile(TEST_JAR.toFile())) {
            JarEntry je = jarFile.getJarEntry(entry.name);
            assertNotNull(je);
            if (DEBUG) {
                System.out.printf("Entry Name: %s, method: %s, Expected Method: %s%n",
                        entry.name, je.getMethod(), entry.method);
            }
            assertEquals(entry.method, je.getMethod());
            try (InputStream in = jarFile.getInputStream(je)) {
                byte[] bytes = in.readAllBytes();
                if (DEBUG) {
                    System.out.printf("bytes= %s, expected=%s%n",
                            new String(bytes), new String(entry.bytes));
                }
                assertArrayEquals(entry.bytes, bytes,
                        String.format("Expected payload: %s", new String(entry.bytes)));
            }
        }
    }

    /**
     * Verify that JarInputStream can be used to read all entries including
     * a file and directory entry with the same name
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void JarInputStreamTest() throws IOException {
        System.out.printf("%n%n**** JarInputStreamTest ***%n");
        try (JarInputStream jis = new JarInputStream(
                new FileInputStream(TEST_JAR.toFile()))) {
            JarEntry jarEntry = jis.getNextJarEntry();
            assertNotNull(jarEntry);
            while (jarEntry != null) {
                Entry e;
                if (jarEntry.getName().equals(FILE_ENTRY.name)) {
                    e = FILE_ENTRY;
                } else if (jarEntry.getName().equals(DIR_ENTRY.name)) {
                    e = DIR_ENTRY;
                } else {
                    throw new RuntimeException(
                            String.format("Invalid Jar entry: %s", jarEntry.getName()));
                }
                assertEquals(e.method, jarEntry.getMethod());
                assertArrayEquals(e.bytes, jis.readAllBytes(),
                        String.format("Expected payload: %s", new String(e.bytes)));
                jarEntry = jis.getNextJarEntry();
            }
        }
    }

    /**
     * Verify that JarURLConnection can be used to access all the entries including
     * a file and directory entry with the same name within a jar file
     *
     * @param entry The entry to validate
     * @throws IOException If an error occurs
     */
    @ParameterizedTest
    @MethodSource("entries")
    public void JarURLConnectionTest(Entry entry) throws Exception {
        System.out.printf("%n%n**** JarURLConnectionTest ***%n");
        URL url = new URL("jar:" + TEST_JAR.toUri().toURL() + "!/" + entry.name);
        if (DEBUG) {
            System.out.printf("URL=%s%n", url);
        }
        JarURLConnection con = (JarURLConnection) url.openConnection();
        con.connect();
        JarEntry je = con.getJarEntry();
        try (JarFile jarFile = con.getJarFile()) {
            assertNotNull(je);
            assertNotNull(jarFile);
            assertNull(con.getAttributes());
            assertNull(con.getMainAttributes());
            assertNull(con.getManifest());
            assertEquals(entry.name, je.getName());
            assertEquals(entry.name, con.getEntryName());
            assertEquals(entry.method, je.getMethod());
            assertEquals(TEST_JAR.toUri().toURL(), con.getJarFileURL());
            if (DEBUG) {
                System.out.printf("   getEntryName: %s,  getJarFileURL:%s%n",
                        con.getEntryName(), con.getJarFileURL());
                System.out.printf("   Jar Entry= %s, size= %s%n", je.getName(), je.getSize());
            }

            try (InputStream is = jarFile.getInputStream(je)) {
                byte[] bytes = is.readAllBytes();
                if (DEBUG) {
                    System.out.printf("   Bytes read:%s%n", new String(bytes));
                }
                assertArrayEquals(entry.bytes, bytes,
                        String.format("Expected payload: %s", new String(entry.bytes)));
            }
        }
    }

    /**
     * Verify that JarFile::stream returns all entries including
     * a file and directory entry with the same name
     *
     * @throws IOException If an error occurs
     */
    @Test
    public void JarFileStreamTest() throws IOException {
        System.out.printf("%n%n**** JarFileStreamTest ***%n");
        try (JarFile jf = new JarFile(TEST_JAR.toFile())) {
            List<? extends JarEntry> entries = jf.stream().collect(Collectors.toList());
            assertEquals(EXPECTED_ENTRIES.size(), jf.size());
            for (JarEntry e : entries) {
                assertTrue(EXPECTED_ENTRIES.contains(e.getName()));
            }
        }
    }

    /**
     * Method used to read  the bytes from an InputStream.  This method is
     * here so that the test could be backported to JDK 8 if needed as
     * InputStream::readAllBytes() does not exist
     *
     * @param is The InputStream to read from
     * @return The byte array representing bytes read from the InputStream
     * @throws IOException If an error occurs
     */
    public static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] data = new byte[MAX_BUFFER_SIZE];
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int len;
        while ((len = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, len);
        }
        buffer.flush();
        return buffer.toByteArray();
    }

    /**
     * Method used to create a byte[] representing a Jar file with
     * duplicate file entries.  This uses ZipArchiveOutputStream as ZipOutputStream
     * will fail with a "java.util.zip.ZipException: duplicate entry".
     */
//    public static void  createJarWithDuplicateFileEntries() throws IOException {
//    Files.deleteIfExists(DUPFILE_ENTRY_FILE);
//    try (ZipArchiveOutputStream zos =
//                     new ZipArchiveOutputStream(DUPFILE_ENTRY_FILE.toFile())) {
//            zos.putArchiveEntry(new ZipArchiveEntry(FILE_ENTRY.name));
//            zos.write(FILE_ENTRY.bytes);
//            zos.putArchiveEntry(new ZipArchiveEntry(FILE_ENTRY.name));
//            zos.write("Yet another File".getBytes(StandardCharsets.UTF_8));
//            zos.closeArchiveEntry();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        byte[] jarBytes = Files.readAllBytes(DUPFILE_ENTRY_FILE);
//        String result = createByteArray(jarBytes, "DUPLICATE_ENTRY_JAR_BYTES");
//        System.out.println(result);
//    }

    /**
     * Utility method which takes a byte array and converts to byte array
     * declaration.  For example:
     * <pre>
     *     {@code
     *        var fooJar = Files.readAllBytes(Path.of("foo.jar"));
     *        var result = createByteArray(fooJar, "FOOBYTES");
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
        final int linelen = 8;
        for (int i = 0; i < bytes.length; i++) {
            if (i % linelen == 0) {
                fmt.format("%n        ");
            }
            fmt.format(" (byte) 0x%x,", bytes[i] & 0xff);
        }
        fmt.format("%n    };%n");
        return sb.toString();
    }

    /**
     * Represents an entry in a Zip file. An entry encapsulates a name, a
     * compression method, and its contents/data.
     */
    public static class Entry {
        public final String name;
        public final int method;
        public final byte[] bytes;

        public Entry(String name, int method, String contents) {
            this.name = name;
            this.method = method;
            this.bytes = contents.getBytes(StandardCharsets.UTF_8);
        }

        public static Entry of(String name, int method, String contents) {
            return new Entry(name, method, contents);
        }
    }
}
