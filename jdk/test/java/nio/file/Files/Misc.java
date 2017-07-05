/*
 * Copyright 2008-2009 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/* @test
 * @bug 4313887
 * @summary Unit test for java.nio.file.Files for miscellenous cases not
 *   covered by other tests
 * @library ..
 */

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public class Misc {

    static void npeExpected() {
        throw new RuntimeException("NullPointerException expected");
    }

    public static void main(String[] args) throws IOException {
        try {
            Files.probeContentType(null);
            npeExpected();
        } catch (NullPointerException e) {
        }

        try {
            Files.withDirectory(null, "*", new FileAction<Path>() {
                public void invoke(Path entry) {
                }
            });
            npeExpected();
        } catch (NullPointerException e) {
        }

       try {
            Files.withDirectory(Paths.get("."), (String)null, new FileAction<Path>() {
                public void invoke(Path entry) {
                }
            });
            npeExpected();
        } catch (NullPointerException e) {
        }

        try {
            Files.withDirectory(Paths.get("."), "*", null);
            npeExpected();
        } catch (NullPointerException e) {
        }

        // test propogation of IOException
        Path tmpdir = TestUtil.createTemporaryDirectory();
        try {
            tmpdir.resolve("foo").createFile();
            try {
                Files.withDirectory(tmpdir, new FileAction<Path>() {
                    public void invoke(Path entry) throws IOException {
                        throw new IOException();
                    }
                });
                throw new RuntimeException("IOException expected");
            } catch (IOException e) {
            }
        } finally {
            TestUtil.removeAll(tmpdir);
        }

        try {
            Files.walkFileTree(null, EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE, new SimpleFileVisitor<Path>(){});
            npeExpected();
        } catch (NullPointerException e) {
        }

        try {
            Files.walkFileTree(Paths.get("."), null, Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>(){});
            npeExpected();
        } catch (NullPointerException e) {
        }

        try {
            Files.walkFileTree(Paths.get("."), EnumSet.noneOf(FileVisitOption.class),
                -1, new SimpleFileVisitor<Path>(){});
            throw new RuntimeException("IllegalArgumentExpected expected");
        } catch (IllegalArgumentException e) {
        }

        try {
            Set<FileVisitOption> opts = new HashSet<FileVisitOption>(1);
            opts.add(null);
            Files.walkFileTree(Paths.get("."), opts, Integer.MAX_VALUE,
                new SimpleFileVisitor<Path>(){});
            npeExpected();
        } catch (NullPointerException e) {
        }

        try {
            Files.walkFileTree(Paths.get("."), EnumSet.noneOf(FileVisitOption.class),
                Integer.MAX_VALUE, null);
            npeExpected();
        } catch (NullPointerException e) {
        }
    }
}
