/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333 8005566 8215467 8255576 8286160
 * @summary Unit test for miscellaneous methods in java.nio.file.Files
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run main Misc
 */

import java.io.IOException;
import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.UserPrincipal;
import java.util.List;
import jdk.test.lib.Platform;

import static java.nio.file.Files.*;
import static java.nio.file.LinkOption.*;

public class Misc {

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            testIsHidden(dir);
            testIsSameFile(dir);
            testFileTypeMethods(dir);
            testAccessMethods(dir);
        } finally {
             TestUtil.removeAll(dir);
        }
    }


    /**
     * Tests isHidden
     */
    static void testIsHidden(Path tmpdir) throws IOException {
        // passing an empty path must not throw any runtime exception
        assertTrue(!isHidden(Path.of("")));

        assertTrue(!isHidden(tmpdir));

        Path file = tmpdir.resolve(".foo");
        if (Platform.isWindows()) {
            createFile(file);
            try {
                setAttribute(file, "dos:hidden", true);
                try {
                    assertTrue(isHidden(file));
                } finally {
                    setAttribute(file, "dos:hidden", false);
                }
            } finally {
                delete(file);
            }
            Path dir = tmpdir.resolve("hidden");
            createDirectory(dir);
            try {
                setAttribute(dir, "dos:hidden", true);
                try {
                    assertTrue(isHidden(dir));
                } finally {
                    setAttribute(dir, "dos:hidden", false);
                }
            } finally {
                delete(dir);
            }
        } else {
            assertTrue(isHidden(file));
        }
    }

    /**
     * Tests isSameFile
     */
    static void testIsSameFile(Path tmpdir) throws IOException {
        Path thisFile = tmpdir.resolve("thisFile");
        Path thatFile = tmpdir.resolve("thatFile");

        /**
         * Test: isSameFile for self
         */
        assertTrue(isSameFile(thisFile, thisFile));

        /**
         * Test: Neither files exist
         */
        try {
            isSameFile(thisFile, thatFile);
            throw new RuntimeException("IOException not thrown");
        } catch (IOException x) {
        }
        try {
            isSameFile(thatFile, thisFile);
            throw new RuntimeException("IOException not thrown");
        } catch (IOException x) {
        }

        createFile(thisFile);
        try {
            /**
             * Test: One file exists
             */
            try {
                isSameFile(thisFile, thatFile);
                throw new RuntimeException("IOException not thrown");
            } catch (IOException x) {
            }
            try {
                isSameFile(thatFile, thisFile);
                throw new RuntimeException("IOException not thrown");
            } catch (IOException x) {
            }

            /**
             * Test: Both file exists
             */
            createFile(thatFile);
            try {
                assertTrue(!isSameFile(thisFile, thatFile));
                assertTrue(!isSameFile(thatFile, thisFile));
            } finally {
                delete(thatFile);
            }

            /**
             * Test: Symbolic links
             */
            if (TestUtil.supportsSymbolicLinks(tmpdir)) {
                createSymbolicLink(thatFile, thisFile);
                try {
                    assertTrue(isSameFile(thisFile, thatFile));
                    assertTrue(isSameFile(thatFile, thisFile));
                } finally {
                    TestUtil.deleteUnchecked(thatFile);
                }
            }
        } finally {
            delete(thisFile);
        }

        // nulls
        try {
            isSameFile(thisFile, null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
        try {
            isSameFile(null, thatFile);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }
    }

    /**
     * Exercise isRegularFile, isDirectory, isSymbolicLink
     */
    static void testFileTypeMethods(Path tmpdir) throws IOException {
        assertTrue(!isRegularFile(tmpdir));
        assertTrue(!isRegularFile(tmpdir, NOFOLLOW_LINKS));
        assertTrue(isDirectory(tmpdir));
        assertTrue(isDirectory(tmpdir, NOFOLLOW_LINKS));
        assertTrue(!isSymbolicLink(tmpdir));

        Path file = createFile(tmpdir.resolve("foo"));
        try {
            assertTrue(isRegularFile(file));
            assertTrue(isRegularFile(file, NOFOLLOW_LINKS));
            assertTrue(!isDirectory(file));
            assertTrue(!isDirectory(file, NOFOLLOW_LINKS));
            assertTrue(!isSymbolicLink(file));

            if (TestUtil.supportsSymbolicLinks(tmpdir)) {
                Path link = tmpdir.resolve("link");

                createSymbolicLink(link, tmpdir);
                try {
                    assertTrue(!isRegularFile(link));
                    assertTrue(!isRegularFile(link, NOFOLLOW_LINKS));
                    assertTrue(isDirectory(link));
                    assertTrue(!isDirectory(link, NOFOLLOW_LINKS));
                    assertTrue(isSymbolicLink(link));
                } finally {
                    delete(link);
                }

                createSymbolicLink(link, file);
                try {
                    assertTrue(isRegularFile(link));
                    assertTrue(!isRegularFile(link, NOFOLLOW_LINKS));
                    assertTrue(!isDirectory(link));
                    assertTrue(!isDirectory(link, NOFOLLOW_LINKS));
                    assertTrue(isSymbolicLink(link));
                } finally {
                    delete(link);
                }
            }

            if (TestUtil.supportsHardLinks(tmpdir)) {
                Path link = tmpdir.resolve("hardlink");

                createLink(link, file);
                try {
                    assertTrue(isRegularFile(link));
                    assertTrue(isRegularFile(link, NOFOLLOW_LINKS));
                    assertTrue(!isDirectory(link));
                    assertTrue(!isDirectory(link, NOFOLLOW_LINKS));
                    assertTrue(!isSymbolicLink(link));
                } finally {
                    delete(link);
                }
            }
        } finally {
            delete(file);
        }
    }

    /**
     * Exercise isReadbale, isWritable, isExecutable, exists, notExists
     */
    static void testAccessMethods(Path tmpdir) throws IOException {
        // should return false when file does not exist
        Path doesNotExist = tmpdir.resolve("doesNotExist");
        assertTrue(!isReadable(doesNotExist));
        assertTrue(!isWritable(doesNotExist));
        assertTrue(!isExecutable(doesNotExist));
        assertTrue(!exists(doesNotExist));
        assertTrue(notExists(doesNotExist));

        Path file = createFile(tmpdir.resolve("foo"));
        try {
            // files exist
            assertTrue(isReadable(file));
            assertTrue(isWritable(file));
            assertTrue(exists(file));
            assertTrue(!notExists(file));
            assertTrue(isReadable(tmpdir));
            assertTrue(isWritable(tmpdir));
            assertTrue(exists(tmpdir));
            assertTrue(!notExists(tmpdir));

            if (Platform.isWindows()) {
                Path pageFile = Path.of("C:\\pagefile.sys");
                if (pageFile.toFile().exists()) {
                    System.out.printf("Check page file %s%n", pageFile);
                    assertTrue(exists(pageFile));
                }
            }

            // sym link exists
            if (TestUtil.supportsSymbolicLinks(tmpdir)) {
                Path link = tmpdir.resolve("link");

                createSymbolicLink(link, file);
                try {
                    assertTrue(isReadable(link));
                    assertTrue(isWritable(link));
                    assertTrue(exists(link));
                    assertTrue(!notExists(link));
                } finally {
                    delete(link);
                }

                createSymbolicLink(link, doesNotExist);
                try {
                    assertTrue(!isReadable(link));
                    assertTrue(!isWritable(link));
                    assertTrue(!exists(link));
                    assertTrue(exists(link, NOFOLLOW_LINKS));
                    assertTrue(notExists(link));
                    assertTrue(!notExists(link, NOFOLLOW_LINKS));
                } finally {
                    delete(link);
                }
            }

            /**
             * Test: Edit ACL to deny WRITE and EXECUTE
             */
            if (getFileStore(file).supportsFileAttributeView("acl")) {
                AclFileAttributeView view =
                    getFileAttributeView(file, AclFileAttributeView.class);
                UserPrincipal owner = view.getOwner();
                List<AclEntry> acl = view.getAcl();

                // Insert entry to deny WRITE and EXECUTE
                AclEntry entry = AclEntry.newBuilder()
                    .setType(AclEntryType.DENY)
                    .setPrincipal(owner)
                    .setPermissions(AclEntryPermission.WRITE_DATA,
                                    AclEntryPermission.EXECUTE)
                    .build();
                acl.add(0, entry);
                view.setAcl(acl);
                try {
                    if (isRoot()) {
                        // root has all permissions
                        assertTrue(isWritable(file));
                        assertTrue(isExecutable(file));
                    } else {
                        assertTrue(!isWritable(file));
                        assertTrue(!isExecutable(file));
                    }
                } finally {
                    // Restore ACL
                    acl.remove(0);
                    view.setAcl(acl);
                }
            }

            /**
             * Test: Windows DOS read-only attribute
             */
            if (Platform.isWindows()) {
                setAttribute(file, "dos:readonly", true);
                try {
                    assertTrue(!isWritable(file));
                } finally {
                    setAttribute(file, "dos:readonly", false);
                }

                // Read-only attribute does not make direcory read-only
                DosFileAttributeView view =
                    getFileAttributeView(tmpdir, DosFileAttributeView.class);
                boolean save = view.readAttributes().isReadOnly();
                view.setReadOnly(true);
                try {
                    assertTrue(isWritable(file));
                } finally {
                    view.setReadOnly(save);
                }
            }
        } finally {
            delete(file);
        }
    }

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion Failed");
    }

    private static boolean isRoot() {
        if (Platform.isWindows())
            return false;

        Path passwd = Path.of("/etc/passwd");
        return Files.isWritable(passwd);
    }
}
