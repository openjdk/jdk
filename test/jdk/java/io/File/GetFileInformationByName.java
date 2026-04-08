/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Functional test for GetFileInformationByName fast paths
 * @requires os.family == "windows"
 * @library /test/lib
 * @build jdk.test.lib.Asserts
 * @run main GetFileInformationByName
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.Asserts;

public class GetFileInformationByName {
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("fileinfo-test");
        try {
            testNonExistent(dir);
            testRegularFile(dir);
            testIsSameFile(dir);
            testFileSymlink(dir);

            testDirectory(dir);
            testDirectorySymlink(dir);
        } finally {
            try {
                Files.walk(dir)
                     .sorted(java.util.Comparator.reverseOrder())
                     .forEach(p -> {
                         try { Files.deleteIfExists(p); }
                         catch (IOException ignore) {}
                     });
            } catch (IOException ignore) {}
        }
    }

    static void testNonExistent(Path dir) throws Exception {
        File ghost = dir.resolve("non-existent-file.txt").toFile();
        Asserts.assertFalse(ghost.exists(), "ghost should not exist");
        Asserts.assertFalse(ghost.isFile(), "ghost should not be a file");
        Asserts.assertFalse(ghost.isDirectory(),"ghost should not be a directory");
        Asserts.assertTrue(ghost.length() == 0, "ghost length should be 0");
    }

    static void testRegularFile(Path dir) throws Exception {
        Path file = dir.resolve("regular.txt");
        Files.writeString(file, "hello");

        File f = file.toFile();
        Asserts.assertTrue(f.exists(), "regular file should exist");
        Asserts.assertTrue(f.isFile(), "should be a regular file");
        Asserts.assertFalse(f.isDirectory(), "should not be a directory");
        Asserts.assertTrue(f.length() == 5, "length should be 5, got " + f.length());
        Asserts.assertTrue(f.canRead(), "should be readable");
        Asserts.assertTrue(f.lastModified() > 0, "lastModified should be positive");
    }

    static void testIsSameFile(Path dir) throws Exception {
        Path file1 = dir.resolve("samefile1.txt");
        Path file2 = dir.resolve("samefile2.txt");
        Files.writeString(file1, "a");
        Files.writeString(file2, "b");

        Asserts.assertTrue(Files.isSameFile(file1, file1), "same path should be same file");
        Asserts.assertFalse(Files.isSameFile(file1, file2), "different files should not be same file");

        // On NTFS, file names _can_ be case sensitive but this is disabled by
        // default.  So we first check if the OS tells whether a file with the
        // upper-case name (that we did not create) already exists, and if so,
        // we determine that file names are case-insensitive, in which case, we
        // proceed with the isSameFile check.
        Path upper = dir.resolve("SAMEFILE1.TXT");
        if (Files.exists(upper)) {
            Asserts.assertTrue(Files.isSameFile(file1, upper), "case-variant path should be same file");
        }
    }

    static void testFileSymlink(Path dir) throws Exception {
        Path file1 = dir.resolve("samefile1.txt");
        Path file2 = dir.resolve("samefile2.txt");
        Files.writeString(file1, "a");
        Files.writeString(file2, "b");

        Path slink = dir.resolve("samelink");

        // On Windows, symlinking is a privileged operation and FAT32 doesn't
        // support symbolic links, so the following call may throw an exception
        // unrelated to the code that we're trying to exercise.  So if we see
        // one of these exceptions, ignore it and move on.
        try {
            Files.createSymbolicLink(slink, file1);
            Asserts.assertTrue(Files.isSameFile(file1, slink), "symlink and target should be same file");
            Asserts.assertFalse(Files.isSameFile(file2, slink), "symlink to file1 should not be same as file2");
        } catch (UnsupportedOperationException | IOException e) {
            System.out.println("Symlink part skipped: " + e);
        }
    }

    static void testDirectory(Path dir) throws Exception {
        Path sub = dir.resolve("subdirectory");
        Files.createDirectory(sub);

        File d = sub.toFile();
        Asserts.assertTrue(d.exists(), "directory should exist");
        Asserts.assertTrue(d.isDirectory(), "should be a directory");
        Asserts.assertFalse(d.isFile(), "directory should not be a file");
    }

    static void testDirectorySymlink(Path dir) throws Exception {
        Path target = dir.resolve("symtarget.txt");
        String content = "symlink content";
        Files.writeString(target, content);
        Path link = dir.resolve("symlink.lnk");

        // On Windows, symlinking is a privileged operation and FAT32 doesn't
        // support symbolic links, so the following call may throw an exception
        // unrelated to the code that we're trying to exercise.  So if we see
        // one of these exceptions, ignore it and move on.
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            System.out.println("unable to create symlinks: " + e);
            return;
        }

        File lf = link.toFile();
        Asserts.assertTrue(lf.exists(), "symlink should exist (follows target)");
        Asserts.assertTrue(lf.isFile(), "symlink target should be a file");
        Asserts.assertFalse(lf.isDirectory(), "symlink target should not be a directory");
        Asserts.assertTrue(lf.length() == content.length(),
                "symlink target length should be " + content.length() + ", got " + lf.length());

        // Test with following symbolic links (which is the default)
        var attrs = Files.readAttributes(link,
                java.nio.file.attribute.BasicFileAttributes.class);
        Asserts.assertFalse(attrs.isSymbolicLink(), "followLinks=true: not a symlink");
        Asserts.assertTrue(attrs.isRegularFile(), "followLinks=true: regular file");
        Asserts.assertTrue(attrs.size() == content.length(),
                "NIO size should be " + content.length() + ", got " + attrs.size());

        // Test without following symbolic links
        var lattrs = Files.readAttributes(link,
                java.nio.file.attribute.BasicFileAttributes.class,
                java.nio.file.LinkOption.NOFOLLOW_LINKS);
        Asserts.assertTrue(lattrs.isSymbolicLink(), "nofollow: should be a symlink");
    }
}
