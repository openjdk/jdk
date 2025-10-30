/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;


public class FileUtilsTest {

    @ParameterizedTest
    @EnumSource(ExcludeType.class)
    public void test_copyRecursive_dir(ExcludeType exclude, @TempDir Path workdir) throws IOException {
        Files.createDirectories(workdir.resolve("from/foo/bar"));
        Files.createDirectories(workdir.resolve("from/foo/buz"));
        Files.writeString(workdir.resolve("from/foo/bar/file.txt"), "Hello");

        List<Path> excludes = new ArrayList<>();
        switch (exclude) {
            case EXCLUDE_FILE -> {
                excludes.add(Path.of("file.txt"));
            }
            case EXCLUDE_DIR -> {
                excludes.add(Path.of("bar"));
            }
            case EXCLUDE_SUBDIR -> {
                excludes.add(Path.of("foo"));
            }
            case EXCLUDE_NONE -> {
            }
        }

        FileUtils.copyRecursive(workdir.resolve("from"), workdir.resolve("to"), excludes);

        assertEquals("Hello", Files.readString(workdir.resolve("from/foo/bar/file.txt")));

        switch (exclude) {
            case EXCLUDE_FILE -> {
                assertFalse(Files.exists(workdir.resolve("to/foo/bar/file.txt")));
                assertTrue(Files.isDirectory(workdir.resolve("to/foo/bar")));
            }
            case EXCLUDE_DIR -> {
                assertFalse(Files.exists(workdir.resolve("to/foo/bar")));
                assertTrue(Files.isDirectory(workdir.resolve("to/foo/buz")));
            }
            case EXCLUDE_SUBDIR -> {
                assertFalse(Files.exists(workdir.resolve("to/foo")));
                assertTrue(Files.isDirectory(workdir.resolve("to")));
            }
            case EXCLUDE_NONE -> {
                assertEquals("Hello", Files.readString(workdir.resolve("to/foo/bar/file.txt")));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_copyRecursive_file(boolean exclude, @TempDir Path workdir) throws IOException {
        Files.createDirectories(workdir.resolve("from/foo/bar"));
        Files.writeString(workdir.resolve("from/foo/bar/file.txt"), "Hello");

        List<Path> excludes = new ArrayList<>();
        if (exclude) {
            excludes.add(Path.of("bar/file.txt"));
        }

        FileUtils.copyRecursive(workdir.resolve("from/foo/bar/file.txt"), workdir.resolve("to/foo/bar/file.txt"), excludes);

        assertEquals("Hello", Files.readString(workdir.resolve("from/foo/bar/file.txt")));
        if (exclude) {
            assertFalse(Files.exists(workdir.resolve("to")));
        } else {
            assertEquals("Hello", Files.readString(workdir.resolve("to/foo/bar/file.txt")));
        }
    }

    enum ExcludeType {
        EXCLUDE_NONE,
        EXCLUDE_FILE,
        EXCLUDE_DIR,
        EXCLUDE_SUBDIR,
    }
}
