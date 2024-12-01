/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.*;
import org.testng.SkipException;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8032220 8293792 8307976
 * @summary Test java.nio.file.Files.createDirectories method
 * @library ..
 * @run testng CreateDirectories
 */
public class CreateDirectories {

    /**
     * Test Files.createDirectories symbolic file with an existing directory.
     */
    @Test
    public void testSymlinkDir() throws Exception {
        // create a temp dir as the "root" in which we will run our tests.
        final Path top = TestUtil.createTemporaryDirectory();
        if (!TestUtil.supportsSymbolicLinks(top)) {
            System.out.println("Skipping tests since symbolic links isn't " +
                    "supported under directory "+ top);
            throw new SkipException("Symbolic links not supported");
        }
        System.out.println("Running tests under directory " + top.toAbsolutePath());
        final Path fooDir = Files.createDirectory(top.resolve("foo"));
        assertTrue(Files.isDirectory(fooDir),
                fooDir + " was expected to be a directory but wasn't");

        // now create a symlink to the "foo" dir
        final Path symlink = Files.createSymbolicLink(top.resolve("symlinkToFoo"),
                fooDir.toAbsolutePath());
        assertTrue(Files.isSymbolicLink(symlink),
                symlink + " was expected to be a symlink but wasn't");
        assertTrue(Files.isDirectory(symlink),
                symlink + " was expected to be a directory but wasn't");

        // now create a directory under the symlink (which effectively creates a directory under
        // "foo")
        final Path barDir = Files.createDirectory(symlink.resolve("bar"));
        assertTrue(Files.isDirectory(barDir),
                barDir + " was expected to be a directory but wasn't");
        // ultimately, we now have this directory structure:
        // <root-dir>
        //   |--- foo
        //   |     |--- bar
        //   |
        //   |--- symlinkToFoo -> (links to) <absolute-path-to-root-dir>/foo


        // now call Files.createDirectories on each of these existing directory/symlink paths
        // and expect each one to succeed
        Files.createDirectories(fooDir); // ./<root-dir>/foo
        Files.createDirectories(symlink); // ./<root-dir>/symlinkToFoo
        Files.createDirectories(barDir); // ./<root-dir>/symlinkToFoo/bar
    }

    /**
     * Tests Files.createDirectories
     */
    @Test
    public void testCreateDirectories() throws IOException {
        final Path tmpdir = TestUtil.createTemporaryDirectory();
        // a no-op
        Path d = Files.createDirectories(tmpdir);
        assertTrue(d == tmpdir, d + " != " + tmpdir);

        // create one directory
        Path subdir = tmpdir.resolve("a");
        Files.createDirectories(subdir);
        assertTrue(Files.isDirectory(subdir), subdir + " was expected to be a directory," +
                " but wasn't");

        // create parents
        subdir = subdir.resolve("b/c/d");
        Files.createDirectories(subdir);
        assertTrue(Files.isDirectory(subdir), subdir + " was expected to be a directory," +
                " but wasn't");

        // existing file is not a directory
        Path file = Files.createFile(tmpdir.resolve("x"));
        try {
            Files.createDirectories(file);
            throw new RuntimeException("failure expected");
        } catch (FileAlreadyExistsException x) { }
        try {
            Files.createDirectories(file.resolve("y"));
            throw new RuntimeException("failure expected");
        } catch (IOException x) { }

        // the root directory always exists
        Path root = Path.of("/");
        Files.createDirectories(root);
        Files.createDirectories(root.toAbsolutePath());

        // the returned path should not be absolute
        Path temp = Path.of(".temp/temp.abc/temp.def");
        Path a = Files.createDirectories(temp);
        assertTrue(a == temp, a + " != " + temp);
    }
}
