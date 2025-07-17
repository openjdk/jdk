/*
 * Copyright (c) 2008, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run main Basic
 */

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.io.File;
import java.io.IOException;

import jdk.test.lib.Platform;
import jdk.test.lib.util.FileUtils;

public class Basic {

    static final long G = 1024L * 1024L * 1024L;

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            doTests(dir);
        } finally {
            TestUtil.removeAll(dir);
        }
    }

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion failed");
    }

    static void checkWithin1GB(String space, long expected, long actual) {
        long diff = Math.abs(actual - expected);
        if (diff > G) {
            String msg = String.format("%s: |actual %d - expected %d| = %d (%f G)",
                                       space, actual, expected, diff,
                                       (float)diff/G);
            throw new RuntimeException(msg);
        }
    }

    static <V extends FileAttributeView> void testFileAttributes(Path file,
                                                                 Class<V> viewClass,
                                                                 String viewName) throws IOException {
        FileStore store = Files.getFileStore(file);
        boolean supported = store.supportsFileAttributeView(viewClass);
        assertTrue(store.supportsFileAttributeView(viewName) == supported);
        boolean haveView = Files.getFileAttributeView(file, viewClass) != null;
        assertTrue(haveView == supported);
    }

    static void doTests(Path dir) throws IOException {
        /**
         * Test: Directory should be on FileStore that is writable
         */
        assertTrue(!Files.getFileStore(dir).isReadOnly());

        /**
         * Test: Two files should have the same FileStore
         */
        Path file1 = Files.createFile(dir.resolve("foo"));
        Path file2 = Files.createFile(dir.resolve("bar"));
        FileStore store1 = Files.getFileStore(file1);
        FileStore store2 = Files.getFileStore(file2);
        assertTrue(store1.equals(store2));
        assertTrue(store2.equals(store1));
        assertTrue(store1.hashCode() == store2.hashCode());

        if (Platform.isWindows()) {
            /**
             * Test: FileStore.equals() should not be case sensitive
             */
            FileStore upper = Files.getFileStore(Path.of("C:\\"));
            FileStore lower = Files.getFileStore(Path.of("c:\\"));
            assertTrue(lower.equals(upper));
        }

        /**
         * Test: File and FileStore attributes
         */
        assertTrue(store1.supportsFileAttributeView("basic"));
        testFileAttributes(dir, BasicFileAttributeView.class, "basic");
        testFileAttributes(dir, PosixFileAttributeView.class, "posix");
        testFileAttributes(dir, DosFileAttributeView.class, "dos");
        testFileAttributes(dir, AclFileAttributeView.class, "acl");
        testFileAttributes(dir, UserDefinedFileAttributeView.class, "user");

        /**
         * Test: Space atributes
         */
        File f = file1.toFile();

        // check values are "close"
        checkWithin1GB("total",  f.getTotalSpace(),  store1.getTotalSpace());
        checkWithin1GB("free",   f.getFreeSpace(),   store1.getUnallocatedSpace());
        checkWithin1GB("usable", f.getUsableSpace(), store1.getUsableSpace());

        // get values by name
        checkWithin1GB("total",  f.getTotalSpace(),
                       (Long)store1.getAttribute("totalSpace"));
        checkWithin1GB("free",   f.getFreeSpace(),
                       (Long)store1.getAttribute("unallocatedSpace"));
        checkWithin1GB("usable", f.getUsableSpace(),
                       (Long)store1.getAttribute("usableSpace"));

        /**
         * Test: Enumerate all FileStores
         */
        if (FileUtils.areMountPointsAccessibleAndUnique()) {
            FileStore prev = null;
            for (FileStore store: FileSystems.getDefault().getFileStores()) {
                System.out.format("%s (name=%s type=%s)\n", store, store.name(),
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

                // two distinct FileStores should not be equal
                assertTrue(!store.equals(prev));
                prev = store;
            }
        } else {
            System.err.println
                ("Skipping FileStore check due to file system access failure");
        }
    }
}
