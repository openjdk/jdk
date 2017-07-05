/*
 * Copyright (c) 2008, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333 6867101
 * @summary Unit test for java.nio.file.Path for miscellenous methods not
 *   covered by other tests
 * @library ..
 */

import java.nio.file.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

public class Misc {
    static final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");
    static boolean supportsLinks;

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            supportsLinks = TestUtil.supportsLinks(dir);

            // equals and hashCode methods
            equalsAndHashCode();

            // checkAccess method
            checkAccessTests(dir);

            // getFileAttributeView methods
            getFileAttributeViewTests(dir);

            // toRealPath method
            toRealPathTests(dir);

            // isSameFile method
            isSameFileTests(dir);

            // isHidden method
            isHiddenTests(dir);

        } finally {
            TestUtil.removeAll(dir);
        }
    }

    /**
     * Exercise equals and hashCode methods
     */
    static void equalsAndHashCode() {

        Path thisFile = Paths.get("this");
        Path thatFile = Paths.get("that");

        assertTrue(thisFile.equals(thisFile));
        assertTrue(!thisFile.equals(thatFile));

        assertTrue(!thisFile.equals(null));
        assertTrue(!thisFile.equals(new Object()));

        Path likeThis = Paths.get("This");
        if (isWindows) {
            // case insensitive
            assertTrue(thisFile.equals(likeThis));
            assertTrue(thisFile.hashCode() == likeThis.hashCode());
        } else {
            // case senstive
            assertTrue(!thisFile.equals(likeThis));
        }
    }

    /**
     * Exercise checkAccess method
     */
    static void checkAccessTests(Path dir) throws IOException {
        final Path file = dir.resolve("foo").createFile();

        /**
         * Test: This directory should readable and writable
         */
        dir.checkAccess();
        dir.checkAccess(AccessMode.READ);
        dir.checkAccess(AccessMode.WRITE);
        dir.checkAccess(AccessMode.READ, AccessMode.WRITE);

        /**
         * Test: Check access to all files in all root directories.
         * (A useful test on Windows for special files such as pagefile.sys)
         */
        for (Path root: FileSystems.getDefault().getRootDirectories()) {
            DirectoryStream<Path> stream;
            try {
                stream = root.newDirectoryStream();
            } catch (IOException x) {
                continue; // skip root directories that aren't accessible
            }
            try {
                for (Path entry: stream) {
                    try {
                        entry.checkAccess();
                    } catch (AccessDeniedException ignore) { }
                }
            } finally {
                stream.close();
            }
        }

        /**
         * Test: File does not exist
         */
        Path doesNotExist = dir.resolve("thisDoesNotExists");
        try {
            doesNotExist.checkAccess();
            throw new RuntimeException("NoSuchFileException expected");
        } catch (NoSuchFileException x) {
        }
        try {
            doesNotExist.checkAccess(AccessMode.READ);
            throw new RuntimeException("NoSuchFileException expected");
        } catch (NoSuchFileException x) {
        }
        try {
            doesNotExist.checkAccess(AccessMode.WRITE);
            throw new RuntimeException("NoSuchFileException expected");
        } catch (NoSuchFileException x) {
        }
        try {
            doesNotExist.checkAccess(AccessMode.EXECUTE);
            throw new RuntimeException("NoSuchFileException expected");
        } catch (NoSuchFileException x) {
        }

        /**
         * Test: Edit ACL to deny WRITE and EXECUTE
         */
        AclFileAttributeView view = file
            .getFileAttributeView(AclFileAttributeView.class);
        if (view != null &&
            file.getFileStore().supportsFileAttributeView("acl"))
        {
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
                file.checkAccess(AccessMode.WRITE);
                throw new RuntimeException("AccessDeniedException expected");
            } catch (AccessDeniedException x) {
            }

            try {
                file.checkAccess(AccessMode.EXECUTE);
                throw new RuntimeException("AccessDeniedException expected");
            } catch (AccessDeniedException x) {
            }


            // Restore ACL
            acl.remove(0);
            view.setAcl(acl);
        }

        /**
         * Test: Windows DOS read-only attribute
         */
        if (isWindows) {
            DosFileAttributeView dview =
                file.getFileAttributeView(DosFileAttributeView.class);
            dview.setReadOnly(true);
            try {
                file.checkAccess(AccessMode.WRITE);
                throw new RuntimeException("AccessDeniedException expected");
            } catch (AccessDeniedException x) {
            }
            dview.setReadOnly(false);

            // Read-only attribute does not make direcory read-only
            dview = dir.getFileAttributeView(DosFileAttributeView.class);
            boolean save = dview.readAttributes().isReadOnly();
            dview.setReadOnly(true);
            dir.checkAccess(AccessMode.WRITE);
            dview.setReadOnly(save);
        }

        /**
         * Test: null
         */
        try {
            file.checkAccess((AccessMode)null);
            throw new RuntimeException("NullPointerException expected");
        } catch (NullPointerException ignore) { }

        // clean-up
        file.delete();
    }

    /**
     * Exercise getFileAttributeFile methods
     */
    static void getFileAttributeViewTests(Path dir) {
        assertTrue(dir.getFileAttributeView(BasicFileAttributeView.class)
            instanceof BasicFileAttributeView);
        assertTrue(dir.getFileAttributeView(BasicFileAttributeView.class, NOFOLLOW_LINKS)
            instanceof BasicFileAttributeView);
        assertTrue(dir.getFileAttributeView(BogusFileAttributeView.class) == null);
        try {
            dir.getFileAttributeView((Class<FileAttributeView>)null);
        } catch (NullPointerException ignore) { }
        try {
            dir.getFileAttributeView(BasicFileAttributeView.class, (LinkOption[])null);
        } catch (NullPointerException ignore) { }
        try {
            dir.getFileAttributeView(BasicFileAttributeView.class, (LinkOption)null);
        } catch (NullPointerException ignore) { }

    }
    interface BogusFileAttributeView extends FileAttributeView { }

    /**
     * Exercise toRealPath method
     */
    static void toRealPathTests(Path dir) throws IOException {
        final Path file = dir.resolve("foo").createFile();
        final Path link = dir.resolve("link");

        /**
         * Test: toRealPath(true) will access same file as toRealPath(false)
         */
        assertTrue(file.toRealPath(true).isSameFile(file.toRealPath(false)));

        /**
         * Test: toRealPath should fail if file does not exist
         */
        Path doesNotExist = dir.resolve("DoesNotExist");
        try {
            doesNotExist.toRealPath(true);
            throw new RuntimeException("IOException expected");
        } catch (IOException expected) {
        }
        try {
            doesNotExist.toRealPath(false);
            throw new RuntimeException("IOException expected");
        } catch (IOException expected) {
        }

        /**
         * Test: toRealPath(true) should resolve links
         */
        if (supportsLinks) {
            link.createSymbolicLink(file.toAbsolutePath());
            assertTrue(link.toRealPath(true).equals(file.toRealPath(true)));
            link.delete();
        }


        /**
         * Test: toRealPath(false) should not resolve links
         */
        if (supportsLinks) {
            link.createSymbolicLink(file.toAbsolutePath());
            assertTrue(link.toRealPath(false).getName().equals(link.getName()));
            link.delete();
        }

        /**
         * Test: toRealPath(false) with broken link
         */
        if (supportsLinks) {
            Path broken = dir.resolve("doesNotExist");
            link.createSymbolicLink(broken);
            assertTrue(link.toRealPath(false).getName().equals(link.getName()));
            link.delete();
        }

        /**
         * Test: toRealPath should eliminate "."
         */
        assertTrue(dir.resolve(".").toRealPath(true).equals(dir.toRealPath(true)));
        assertTrue(dir.resolve(".").toRealPath(false).equals(dir.toRealPath(false)));

        /**
         * Test: toRealPath should eliminate ".." when it doesn't follow a
         *       symbolic link
         */
        Path subdir = dir.resolve("subdir").createDirectory();
        assertTrue(subdir.resolve("..").toRealPath(true).equals(dir.toRealPath(true)));
        assertTrue(subdir.resolve("..").toRealPath(false).equals(dir.toRealPath(false)));
        subdir.delete();

        // clean-up
        file.delete();
    }

    /**
     * Exercise isSameFile method
     */
    static void isSameFileTests(Path dir) throws IOException {
        Path thisFile = dir.resolve("thisFile");
        Path thatFile = dir.resolve("thatFile");

        /**
         * Test: isSameFile for self and null
         */
        assertTrue(thisFile.isSameFile(thisFile));
        assertTrue(!thisFile.isSameFile(null));

        /**
         * Test: Neither files exist
         */
        try {
            thisFile.isSameFile(thatFile);
            throw new RuntimeException("IOException not thrown");
        } catch (IOException x) {
        }
        try {
            thatFile.isSameFile(thisFile);
            throw new RuntimeException("IOException not thrown");
        } catch (IOException x) {
        }

        thisFile.createFile();
        try {
            /**
             * Test: One file exists
             */
            try {
                thisFile.isSameFile(thatFile);
                throw new RuntimeException("IOException not thrown");
            } catch (IOException x) {
            }
            try {
                thatFile.isSameFile(thisFile);
                throw new RuntimeException("IOException not thrown");
            } catch (IOException x) {
            }

            thatFile.createFile();

            /**
             * Test: Both file exists
             */
            try {
                assertTrue(!thisFile.isSameFile(thatFile));
                assertTrue(!thatFile.isSameFile(thisFile));
            } finally {
                TestUtil.deleteUnchecked(thatFile);
            }

            /**
             * Test: Symbolic links
             */
            if (supportsLinks) {
                thatFile.createSymbolicLink(thisFile);
                try {
                    assertTrue(thisFile.isSameFile(thatFile));
                    assertTrue(thatFile.isSameFile(thisFile));
                } finally {
                    TestUtil.deleteUnchecked(thatFile);
                }
            }
        } finally {
            thisFile.delete();
        }
    }

    /**
     * Exercise isHidden method
     */
    static void isHiddenTests(Path dir) throws IOException {
        assertTrue(!dir.isHidden());

        Path file = dir.resolve(".foo");
        if (isWindows) {
            file.createFile();
            try {
                file.setAttribute("dos:hidden", true);
                assertTrue(file.isHidden());
            } finally {
                file.delete();
            }
        } else {
            assertTrue(file.isHidden());
        }
    }

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion Failed");
    }
}
