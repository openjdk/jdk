/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4842706 8024695
 * @summary Test some file operations with empty path
 * @run junit EmptyPath
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

public class EmptyPath {
    private static final String EMPTY_STRING = "";

    static File f;
    static Path p;

    @BeforeAll
    public static void init() {
        f = new File(EMPTY_STRING);
        p = Path.of(EMPTY_STRING);
    }

    @Test
    public void canExecute() {
        assertTrue(f.canExecute());
    }

    @Test
    public void canRead() {
        assertTrue(f.canRead());
    }

    @Test
    public void canWrite() {
        assertTrue(f.canWrite());
    }

    @Test
    public void compareTo() {
        assertEquals(0, f.compareTo(p.toFile()));
    }

    @Test
    public void createNewFile() {
        assertThrows(IOException.class, () -> f.createNewFile());
    }

    @Test
    public void open() throws FileNotFoundException {
        assertThrows(FileNotFoundException.class,
                     () -> new FileInputStream(f));
    }

    @Test
    public void delete() {
        assertFalse(f.delete());
    }

    @Test
    public void equals() {
        assertTrue(f.equals(p.toFile()));
    }

    @Test
    public void exists() {
        assertTrue(f.exists());
    }

    @Test
    public void getAbsolutePath() {
        System.out.println(p.toAbsolutePath().toString() + "\n" +
                           f.getAbsolutePath());
        assertEquals(p.toAbsolutePath().toString(), f.getAbsolutePath());
    }

    private void checkSpace(long expected, long actual) {
        if (expected == 0) {
            assertEquals(0L, actual);
        } else {
            assertTrue(actual > 0);
        }
    }

    @Test
    public void getFreeSpace() throws IOException {
        FileStore fs = Files.getFileStore(f.toPath());
        checkSpace(fs.getUnallocatedSpace(), f.getFreeSpace());
    }

    @Test
    public void getName() {
        assertEquals(p.getFileName().toString(), f.getName());
    }

    @Test
    public void getParent() {
        assertNull(f.getParent());
    }

    @Test
    public void getPath() {
        assertEquals(p.toString(), f.getPath());
    }

    @Test
    public void getTotalSpace() throws IOException {
        FileStore fs = Files.getFileStore(f.toPath());
        checkSpace(fs.getTotalSpace(), f.getTotalSpace());
    }

    @Test
    public void getUsableSpace() throws IOException {
        FileStore fs = Files.getFileStore(f.toPath());
        checkSpace(fs.getUsableSpace(), f.getUsableSpace());
    }

    @Test
    public void isNotAbsolute() {
        assertFalse(f.isAbsolute());
    }

    @Test
    public void isAbsolute() {
        assertTrue(f.getAbsoluteFile().isAbsolute());
    }

    @Test
    public void isDirectory() {
        assertTrue(f.isDirectory());
    }

    @Test
    public void isFile() {
        assertFalse(f.isFile());
    }

    @Test
    public void isHidden() {
        assertFalse(f.isHidden());
    }

    @Test
    public void lastModified() {
        assertTrue(f.lastModified() > 0);
    }

    @Test
    public void length() throws IOException {
        assertEquals(Files.size(f.toPath()), f.length());
    }

    @Test
    public void list() throws IOException {
        String[] files = f.list();
        assertNotNull(files);
        Set<String> ioSet = new HashSet(Arrays.asList(files));
        Set<String> nioSet = new HashSet();
        Files.list(p).forEach((x) -> nioSet.add(x.toString()));
        assertEquals(nioSet, ioSet);
    }

    @Test
    public void mkdir() {
        assertFalse(f.mkdir());
    }

    @Test
    public void setLastModified() {
        long t0 = f.lastModified();
        long t = System.currentTimeMillis();
        try {
            assertTrue(f.setLastModified(t));
            assertEquals(t, f.lastModified());
            assertTrue(f.setLastModified(t0));
            assertEquals(t0, f.lastModified());
        } finally {
            f.setLastModified(t0);
        }
    }

    // Note: Testing File.setExecutable is omitted because calling
    // File.setExecutable(false) makes it impossible to set the CWD to
    // executable again which makes subsequent tests fail

    @Test
    @DisabledOnOs({OS.WINDOWS})
    public void setReadable() {
        assertTrue(f.canRead());
        try {
            assertTrue(f.setReadable(false));
            assertFalse(f.canRead());
            assertTrue(f.setReadable(true));
            assertTrue(f.canRead());
        } finally {
            f.setReadable(true);
        }
    }

    @Test
    @DisabledOnOs({OS.WINDOWS})
    public void setReadOnly() {
        assertTrue(f.canExecute());
        assertTrue(f.canRead());
        assertTrue(f.canWrite());
        try {
            assertTrue(f.setReadOnly());
            assertTrue(f.canRead());
            assertFalse(f.canWrite());
            assertTrue(f.setWritable(true, true));
            assertTrue(f.canWrite());
        } finally {
            f.setWritable(true, true);
        }
    }

    @Test
    @DisabledOnOs({OS.WINDOWS})
    public void setWritable() {
        assertTrue(f.canWrite());
        try {
            assertTrue(f.setWritable(false, true));
            assertFalse(f.canWrite());
            assertTrue(f.setWritable(true, true));
            assertTrue(f.canWrite());
        } finally {
            f.setWritable(true, true);
        }
    }

    @Test
    public void toPath() {
        assertEquals(p, f.toPath());
    }

    @Test
    public void toURI() {
        assertEquals(f.toPath().toUri(), f.toURI());
    }
}
