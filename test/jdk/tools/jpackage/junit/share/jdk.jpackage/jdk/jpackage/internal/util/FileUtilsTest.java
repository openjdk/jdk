/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


public class FileUtilsTest {

    @Test
    public void test_copyRecursive_dir(@TempDir Path workdir) throws IOException {
        Files.createDirectories(workdir.resolve("from/foo/bar"));
        Files.createDirectories(workdir.resolve("from/foo/buz"));
        Files.writeString(workdir.resolve("from/foo/bar/file.txt"), "Hello");

        FileUtils.copyRecursive(workdir.resolve("from"), workdir.resolve("to"));

        assertEquals("Hello", Files.readString(workdir.resolve("from/foo/bar/file.txt")));
        assertEquals("Hello", Files.readString(workdir.resolve("to/foo/bar/file.txt")));
    }

    @Test
    public void test_copyRecursive_file(@TempDir Path workdir) throws IOException {
        Files.createDirectories(workdir.resolve("from/foo/bar"));
        Files.writeString(workdir.resolve("from/foo/bar/file.txt"), "Hello");

        FileUtils.copyRecursive(workdir.resolve("from/foo/bar/file.txt"), workdir.resolve("to/foo/bar/file.txt"));

        assertEquals("Hello", Files.readString(workdir.resolve("from/foo/bar/file.txt")));
        assertEquals("Hello", Files.readString(workdir.resolve("to/foo/bar/file.txt")));
    }
}
