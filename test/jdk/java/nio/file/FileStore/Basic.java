/*
 * Copyright (c) 2008, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6873621 6979526 7006126 7020517 8264400 8360887
 * @summary Unit test for java.nio.file.FileStore
 * @key intermittent
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 *        jdk.test.lib.util.FileUtils
 * @run junit Basic
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileStore;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import jdk.test.lib.Platform;
import jdk.test.lib.util.FileUtils;

public class Basic {

    static final long G = 1024L * 1024L * 1024L;

    static Path[] factory(@TempDir Path tempDir) {
        return new Path[] { tempDir };
    }

    static void checkWithin1GB(String space, long expected, long actual) {
        long diff = Math.abs(actual - expected);
        assertTrue(diff <= G, () -> String.format("%s: |actual %d - expected %d| = %d (%f G)",
                                       space, actual, expected, diff,
                                       (float)diff/G));
    }

    static <V extends FileAttributeView> void testFileAttributes(Path file,
                                                                 Class<V> viewClass,
                                                                 String viewName) throws IOException {
        FileStore store = Files.getFileStore(file);
        boolean supported = store.supportsFileAttributeView(viewClass);
        assertEquals(store.supportsFileAttributeView(viewName), supported);
        // If the file attribute view is supported by the FileStore then
        // Files.getFileAttributeView should return that view
        if (supported) {
            assertNotNull(Files.getFileAttributeView(file, viewClass));
        }
    }

    /*
     * Test: Directory should be on FileStore that is writable
     */
    @ParameterizedTest
    @MethodSource("factory")
    void testDirectoryWritable(Path dir) throws IOException {
        assertFalse(Files.getFileStore(dir).isReadOnly());
    }

    /*
     * Test: Two files should have the same FileStore
     */
    @ParameterizedTest
    @MethodSource("factory")
    void testEquality(Path dir) throws IOException {
        Path file1 = Files.createFile(dir.resolve("foo"));
        Path file2 = Files.createFile(dir.resolve("bar"));
        FileStore store1 = Files.getFileStore(file1);
        FileStore store2 = Files.getFileStore(file2);
        assertEquals(store1, store2);
        assertEquals(store2, store1);
        assertEquals(store1.hashCode(), store2.hashCode());
    }

    /*
     * Test: FileStore should not be case sensitive
     */
    @ParameterizedTest
    @MethodSource("factory")
    @EnabledOnOs({OS.WINDOWS})
    void testCaseSensitivity(Path dir) throws IOException {
        FileStore upper = Files.getFileStore(Path.of("C:\\"));
        FileStore lower = Files.getFileStore(Path.of("c:\\"));
        assertEquals(lower, upper);
    }

    /*
     * Test: File and FileStore attributes
     */
    @ParameterizedTest
    @MethodSource("factory")
    void testAttributes(Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("foo"));
        FileStore store = Files.getFileStore(file);
        assertTrue(store.supportsFileAttributeView("basic"));
        testFileAttributes(dir, BasicFileAttributeView.class, "basic");
        testFileAttributes(dir, PosixFileAttributeView.class, "posix");
        testFileAttributes(dir, DosFileAttributeView.class, "dos");
        testFileAttributes(dir, AclFileAttributeView.class, "acl");
        testFileAttributes(dir, UserDefinedFileAttributeView.class, "user");
    }

    /*
     * Test: Space attributes
     */
    @ParameterizedTest
    @MethodSource("factory")
    void testSpaceAttributes(Path dir) throws IOException {
        Path file = Files.createFile(dir.resolve("foo"));
        FileStore store = Files.getFileStore(file);
        File f = file.toFile();

        // check values are "close"
        checkWithin1GB("total",  f.getTotalSpace(),  store.getTotalSpace());
        checkWithin1GB("free",   f.getFreeSpace(),   store.getUnallocatedSpace());
        checkWithin1GB("usable", f.getUsableSpace(), store.getUsableSpace());

        // get values by name
        checkWithin1GB("total",  f.getTotalSpace(),
                       (Long)store.getAttribute("totalSpace"));
        checkWithin1GB("free",   f.getFreeSpace(),
                       (Long)store.getAttribute("unallocatedSpace"));
        checkWithin1GB("usable", f.getUsableSpace(),
                       (Long)store.getAttribute("usableSpace"));
    }

    /*
     * Test: Enumerate all FileStores
     */
    @ParameterizedTest
    @MethodSource("factory")
    void testEnumerateFileStores(Path dir) throws IOException {
        assumeTrue(FileUtils.areMountPointsAccessibleAndUnique());
        List<FileStore> stores = StreamSupport.stream(FileSystems.getDefault()
                                 .getFileStores().spliterator(), false)
                                 .toList();
        Set<FileStore> uniqueStores = new HashSet<>(stores);
        assertEquals(stores.size(), uniqueStores.size(), "FileStores should be unique");
        for (FileStore store: stores) {
            System.err.format("%s (name=%s type=%s)\n", store, store.name(),
                store.type());

            // check space attributes are accessible
            try {
                store.getTotalSpace();
                store.getUnallocatedSpace();
                store.getUsableSpace();
            } catch (NoSuchFileException nsfe) {
                // ignore exception as the store could have been
                // deleted since the iterator was instantiated
                System.err.format("%s was not found\n", store);
            } catch (AccessDeniedException ade) {
                // ignore exception as the lack of ability to access the
                // store due to lack of file permission or similar does not
                // reflect whether the space attributes would be accessible
                // were access to be permitted
                System.err.format("%s is inaccessible\n", store);
            } catch (FileSystemException fse) {
                // On Linux, ignore the FSE if the path is one of the
                // /run/user/$UID mounts created by pam_systemd(8) as it
                // might be mounted as a fuse.portal filesystem and
                // its access attempt might fail with EPERM
                if (!Platform.isLinux() || store.toString().indexOf("/run/user") == -1) {
                    throw new RuntimeException(fse);
                } else {
                    System.err.format("%s error: %s\n", store, fse);
                }
            }
        }
    }
}
