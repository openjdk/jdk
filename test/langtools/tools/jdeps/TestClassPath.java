/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @library lib
 * @build CompilerUtils
 * @run testng TestClassPath
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class TestClassPath {
    private static final ToolProvider JDEPS_TOOL = ToolProvider.findFirst("jdeps").orElseThrow();
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path CLASSES_DIR = Path.of("classes");
    private static final Path CLASSES = CLASSES_DIR.resolve("p");

    @BeforeTest
    public void setup() throws IOException {
        CompilerUtils.cleanDir(CLASSES_DIR);
        assertTrue(CompilerUtils.compile(Path.of(TEST_SRC, "p"), CLASSES_DIR));
    }

    @Test
    public void test() {
        int rc = JDEPS_TOOL.run(System.out, System.out, "-verbose:class", "-cp", ".", CLASSES.toString());
        assertTrue(rc == 0);
    }

    // Class file with no permission
    @Test
    public void ignoreNoPermissionFile() throws IOException {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("---------");
        FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(perms);
        Files.createDirectories(Path.of("dir1"));
        Path file = Path.of("dir1", "NoPerm.class");
        Files.createFile(file, attrs);
        int rc = JDEPS_TOOL.run(System.out, System.out, "--list-deps", "-cp", "dir1", CLASSES.toString());
        assertTrue(rc == 0);
        resetPermission(file);
    }

    // A directory with no permission
    @Test
    public void ignoreNoPermissionDir() throws IOException {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("---------");
        FileAttribute<Set<PosixFilePermission>> attrs = PosixFilePermissions.asFileAttribute(perms);
        Path dir = Path.of("dir2");
        Files.createDirectories(dir, attrs);
        int rc = JDEPS_TOOL.run(System.out, System.out, "--print-module-deps", "-cp", "dir2", CLASSES.toString());
        assertTrue(rc == 0);
        resetPermission(dir);
    }

    private void resetPermission(Path path) throws IOException {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwxrwxrwx");
        Files.setPosixFilePermissions(path, perms);
    }
}
