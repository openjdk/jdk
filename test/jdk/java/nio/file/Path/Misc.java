/*
 * Copyright (c) 2008, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4313887 6838333 7029979
 * @summary Unit test for miscellenous java.nio.file.Path methods
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run main Misc
 */

import java.io.*;
import java.nio.file.*;

import jdk.test.lib.Platform;

public class Misc {
    public static void main(String[] args) throws IOException {
        Path dir = TestUtil.createTemporaryDirectory();
        try {
            // equals and hashCode methods
            testEqualsAndHashCode();

            // toFile method
            testToFile(dir);
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
        if (Platform.isWindows()) {
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

    static void assertTrue(boolean okay) {
        if (!okay)
            throw new RuntimeException("Assertion Failed");
    }
}
