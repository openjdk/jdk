/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333
 * @summary Unit test for miscellenous java.nio.file.Path methods
 * @library ..
 */

import java.nio.file.*;
import java.io.*;

public class Misc {
    static final boolean isWindows =
        System.getProperty("os.name").startsWith("Windows");
    static boolean supportsLinks;

    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            supportsLinks = TestUtil.supportsLinks(dir);

            // equals and hashCode methods
            testEqualsAndHashCode();

            // toFile method
            testToFile(dir);

            // toRealPath method
            testToRealPath(dir);


        } finally {
            TestUtil.removeAll(dir);
        }
    }

    /**
     * Exercise equals and hashCode methods
     */
    static void testEqualsAndHashCode() {
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
     * Exercise toFile method
     */
    static void testToFile(Path dir) throws IOException {
        File d = dir.toFile();
        assertTrue(d.toString().equals(dir.toString()));
        assertTrue(d.toPath().equals(dir));
    }

    /**
     * Exercise toRealPath method
     */
    static void testToRealPath(Path dir) throws IOException {
        final Path file = Files.createFile(dir.resolve("foo"));
        final Path link = dir.resolve("link");

        /**
         * Test: totRealPath(true) will access same file as toRealPath(false)
         */
        assertTrue(Files.isSameFile(file.toRealPath(true), file.toRealPath(false)));

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
            Files.createSymbolicLink(link, file.toAbsolutePath());
            assertTrue(link.toRealPath(true).equals(file.toRealPath(true)));
            Files.delete(link);
        }

        /**
         * Test: toRealPath(false) should not resolve links
         */
        if (supportsLinks) {
            Files.createSymbolicLink(link, file.toAbsolutePath());
            assertTrue(link.toRealPath(false).getFileName().equals(link.getFileName()));
            Files.delete(link);
        }

        /**
         * Test: toRealPath(false) with broken link
         */
        if (supportsLinks) {
            Path broken = Files.createSymbolicLink(link, doesNotExist);
            assertTrue(link.toRealPath(false).getFileName().equals(link.getFileName()));
            Files.delete(link);
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
        Path subdir = Files.createDirectory(dir.resolve("subdir"));
        assertTrue(subdir.resolve("..").toRealPath(true).equals(dir.toRealPath(true)));
        assertTrue(subdir.resolve("..").toRealPath(false).equals(dir.toRealPath(false)));
        Files.delete(subdir);

        // clean-up
        Files.delete(file);
    }

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion Failed");
    }
}
