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

/* @test
 * @bug 8356678
 * @requires (os.family != "windows")
 * @summary Test behavior of Files methods for regular file "foo/bar"
 * @run junit NotADirectory
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributes;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NotADirectory {
    private static final String DIR = ".";
    private static final String PARENT = "foo";
    private static final String LEAF = "bar";

    private static Path parent;
    private static Path leaf;

    @BeforeAll
    public static void init() throws IOException {
        parent = Path.of(DIR).resolve(PARENT);
        Files.createFile(parent);
        leaf = parent.resolve(LEAF);
    }

    @AfterAll
    public static void clean() throws IOException {
        Files.delete(parent);
    }

    @Test
    public void copy() throws IOException {
        try {
            Files.copy(leaf, Path.of("junk"));
        } catch (NoSuchFileException expected) {
        }
    }

    @Test
    public void readBasic() throws IOException {
        try {
            Files.readAttributes(leaf, BasicFileAttributes.class);
        } catch (NoSuchFileException expected) {
        }
    }

    @Test
    public void readPosix() throws IOException {
        try {
            Files.readAttributes(leaf, PosixFileAttributes.class);
        } catch (NoSuchFileException expected) {
        }
    }

    @Test
    public void exists() throws IOException {
        assertFalse(Files.exists(leaf));
    }

    @Test
    public void notExists() throws IOException {
        assertTrue(Files.notExists(leaf));
    }
}
