/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;

import org.testng.Assert;
import org.testng.annotations.Test;

/*
 * @test
 * @bug 8293792
 * @summary Test java.nio.file.Files.createDirectories method
 * @run testng CreateDirectories
 */
public class CreateDirectories {

    /**
     * Creates a symlink which points to a directory that exists. Then calls Files.createDirectories
     * method passing it the path to the symlink and verifies that no exception gets thrown
     */
    @Test
    public void testSymlinkDir() throws Exception {
        // create a temp dir as the "root" in which we will run our tests.
        final Path startingDir = Files.createTempDirectory(Path.of("."), "8293792");
        System.out.println("Running tests under directory " + startingDir.toAbsolutePath());
        final Path fooDir = Files.createDirectory(Path.of(startingDir.toString(), "foo"));
        Assert.assertTrue(Files.isDirectory(fooDir),
                fooDir + " was expected to be a directory but wasn't");

        // now create a symlink to the "foo" dir
        final Path symlink = Files.createSymbolicLink(
                Path.of(startingDir.toString(), "symlinkToFoo"), fooDir.toAbsolutePath());
        Assert.assertTrue(Files.isSymbolicLink(symlink),
                symlink + " was expected to be a symlink but wasn't");
        Assert.assertTrue(Files.isDirectory(symlink),
                symlink + " was expected to be a directory but wasn't");

        // now create a directory under the symlink (which effectively creates a directory under
        // "foo")
        final Path barDir = Files.createDirectory(Path.of(symlink.toString(), "bar"));
        Assert.assertTrue(Files.isDirectory(barDir),
                barDir + " was expected to be a directory but wasn't");
        // ultimately, we now have this directory structure:
        // <root-dir>
        //   |--- foo
        //   |	   |--- bar
        //   |
        //   |--- symlinkToFoo -> (links to) <absolute-path-to-root-dir>/foo


        // now call Files.createDirectories on each of these existing directory/symlink paths
        // and expect each one to succeed
        Files.createDirectories(fooDir); // ./<root-dir>/foo
        Files.createDirectories(symlink); // ./<root-dir>/symlinkToFoo
        Files.createDirectories(barDir); // ./<root-dir>/symlinkToFoo/bar
    }
}