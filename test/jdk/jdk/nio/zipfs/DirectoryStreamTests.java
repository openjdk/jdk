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
 */


import java.io.IOException;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import java.util.zip.ZipException;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * @test
 * @bug 8211385 8211919
 * @summary ZIP File System tests that leverage DirectoryStream
 * @modules jdk.zipfs
 * @compile DirectoryStreamTests.java
 * @run junit DirectoryStreamTests
 */
public class DirectoryStreamTests {

    // Map to used for creating a ZIP archive
    private static final Map<String, String> ZIPFS_MAP = Map.of("create", "true");

    // Map to used for extracting a ZIP archive
    private static final Map<String, String> UNZIPFS_MAP = Map.of();

    // The ZIP file system provider
    private static final FileSystemProvider ZIPFS_PROVIDER = getZipFSProvider();

    // Primary jar file used for testing
    private static Path jarFile;

    // Jar file used to validate the behavior of the navigation of an empty directory
    private static Path emptyJarFile;

    /**
     * Create the JAR files used by the tests
     */
    @BeforeAll
    public static void setUp()  throws Exception {
        emptyJarFile = Paths.get("emptyDir.jar");
        try (FileSystem zipfs = ZIPFS_PROVIDER.newFileSystem(emptyJarFile, ZIPFS_MAP)) {

            jarFile = Utils.createJarFile("basic.jar",
                    "META-INF/services/java.nio.file.spi.FileSystemProvider");

            Files.createDirectory(zipfs.getPath("emptyDir"));
        }
    }

    /**
     * Remove JAR files used by test as part of clean-up
     */
    @AfterAll
    public static void tearDown() throws Exception {
            Files.deleteIfExists(jarFile);
            Files.deleteIfExists(emptyJarFile);
    }

    /**
     * Validate that you can specify a DirectoryStream filter using the ZIP File
     * System and that the returned Iterator correctly indicates whether the
     * filter has been matched
     */
    @ParameterizedTest
    @MethodSource("filterValues")
    public void test0000(String glob, boolean expectedResult, String errMsg)
            throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds = Files.newDirectoryStream(zipfs.getPath("/"),
                     new DirectoryStream.Filter<Path>() {
                         private PathMatcher matcher =
                                 zipfs.getPathMatcher("glob:" + glob);
                         public boolean accept(Path file) {
                             return matcher.matches(file.getFileName());
                         }
             }))
        {
            assertEquals(expectedResult, ds.iterator().hasNext(), errMsg);
        }
    }

    /**
     * Validate that you can specify a glob using the ZIP File System and that the
     * returned Iterator correctly indicates whether the glob pattern has been matched
     */
    @ParameterizedTest
    @MethodSource("filterValues")
    public void test0001(String glob, boolean expectedResult, String errMsg)
            throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"), glob)) {
            assertEquals(expectedResult, ds.iterator().hasNext(), errMsg);
        }
    }

    /**
     * Validate a PatternSyntaxException is thrown when specifying an invalid
     * glob pattern with the ZIP File system
     */
    @Test
    public void test0002() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP)) {
            assertThrows(PatternSyntaxException.class, () ->
                    Files.newDirectoryStream(zipfs.getPath("/"), "*[a-z"));
        }
    }

    /**
     * Validate that the correct type of paths are returned when creating a
     * DirectoryStream
     */
    @ParameterizedTest
    @MethodSource("Name")
    public void test0003(String startPath, String expectedPath)
            throws IOException {
        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> stream =
                     Files.newDirectoryStream(zipfs.getPath(startPath))) {

            for (Path entry : stream) {
                assertEquals(entry.toString(), expectedPath, String.format("Error: Expected path %s not found when"
                        + " starting at %s%n", expectedPath, entry));
            }
        }
    }

    /**
     * Validate a NotDirectoryException is thrown when specifying a file for the
     * starting path for creating a DirectoryStream with the ZIP File System
     */
    @Test
    public void test0004() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP)) {
            assertThrows(NotDirectoryException.class,
                    () -> Files.newDirectoryStream(
                            zipfs.getPath("META-INF/services/java.nio.file.spi."
                                    + "FileSystemProvider")));
        }
    }

    /**
     * Validate an IllegalStateException is thrown when accessing the Iterator
     * more than once with the ZIP File System
     */
    @Test
    public void test0005() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"))) {
            ds.iterator();
            assertThrows(IllegalStateException.class, () -> ds.iterator());

        }
    }

    /**
     * Validate an IllegalStateException is thrown when accessing the Iterator
     * after the DirectoryStream has been closed with the ZIP File System
     */
    @Test
    public void test0006() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"))) {
            ds.close();
            assertThrows(IllegalStateException.class, () -> ds.iterator());

            // ZipDirectoryStream.iterator() throws ClosedDirectoryStream when
            // obtaining an Iterator when the DirectoryStream is closed
            assertThrows(ClosedDirectoryStreamException.class, () -> ds.iterator());

        }
    }

    /**
     * Validate an UnsupportedOperationException is thrown when invoking an
     * Iterator operation that is not supported with the ZIP File System
     */
    @Test
    public void test0007() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"))) {
            Iterator<Path> i = ds.iterator();

            assertThrows(UnsupportedOperationException.class, () -> i.remove());
        }
    }

    /**
     * Validate an NoSuchElementException is thrown when invoking an
     * Iterator.next() on a closed DirectoryStream with the ZIP File System
     */
    @Test
    public void test0008() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"))) {
            Iterator<Path> i = ds.iterator();
            ds.close();
            assertThrows(NoSuchElementException.class, () -> i.next());
        }
    }

    /**
     * Validate Iterator.hasNext() returns false when the directory is empty with
     * the ZIP File System
     */
    @Test
    public void test0009() throws Exception {
        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(emptyJarFile, UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("emptyDir"))) {
            assertFalse(ds.iterator().hasNext(), "Error: directory was not empty!");

        }
    }

    /**
     * Validate Iterator.hasNext() returns false when the DirectoryStream is closed
     * with the ZIP File System
     */
    @Test
    public void test0010() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds =
                     Files.newDirectoryStream(zipfs.getPath("/"))) {
            Iterator<Path> i = ds.iterator();
            ds.close();
            assertFalse(i.hasNext(),
                    "Error: false should be returned as DirectoryStream is closed!");
        }
    }

    /**
     * Validate that an IOException thrown by a filter is returned as the cause
     * via a DirectoryIteratorException
     */
    @Test
    public void test0011() throws Exception {

        try (FileSystem zipfs =
                ZIPFS_PROVIDER.newFileSystem(Paths.get("basic.jar"), UNZIPFS_MAP);
             DirectoryStream<Path> ds = Files.newDirectoryStream(zipfs.getPath("/"),
                     new DirectoryStream.Filter<Path>() {
                         public boolean accept(Path file) throws IOException {
                             throw new java.util.zip.ZipException();
                         }
                     }))
        {
            ds.iterator().hasNext();
            throw new RuntimeException("Expected DirectoryIteratorException not thrown");

        } catch (DirectoryIteratorException x) {
            IOException cause = x.getCause();
            if (!(cause instanceof ZipException))
                throw new RuntimeException("Expected IOException not propagated");
        }
    }

    /**
     * Glob values to use to validate filtering
     */
    public static Stream<Arguments> filterValues() {

        String expectedMsg = "Error: Matching entries were expected but not found!!!";
        String notExpectedMsg = "Error: No matching entries expected but were found!!!";
        return Stream.of(
                Arguments.of("M*", true, expectedMsg),
                Arguments.of("I*", false, notExpectedMsg)
        );
    }

    /**
     * Starting Path for the DirectoryStream and the expected path to be returned
     * when traversing the stream
     */
    public static Stream<Arguments> Name() {
        return Stream.of(
                Arguments.of("META-INF", "META-INF/services"),
                Arguments.of("/META-INF", "/META-INF/services"),
                Arguments.of("/META-INF/../META-INF","/META-INF/../META-INF/services" ),
                Arguments.of("./META-INF", "./META-INF/services"),
                Arguments.of("", "META-INF"),
                Arguments.of("/", "/META-INF"),
                Arguments.of(".", "./META-INF"),
                Arguments.of("./", "./META-INF")
        );
    }

    /**
     * Returns the Zip FileSystem Provider
     */
    private static FileSystemProvider getZipFSProvider() {
        for (FileSystemProvider fsProvider : FileSystemProvider.installedProviders()) {
            if ("jar".equals(fsProvider.getScheme())) {
                return fsProvider;
            }
        }
        return null;
    }

}
