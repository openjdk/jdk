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
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(OrderAnnotation.class)
public class EmptyPath {
    static File f;

    @BeforeAll
    public static void init() {
        f = new File("");
    }

    @Test
    @Order(1)
    public void mkdir() {
        assertFalse(f.mkdir());
    }

    @Test
    @Order(1)
    public void createNewFile() {
        assertThrows(IOException.class, () -> f.createNewFile());
    }

    @Test
    @Order(1)
    public void close() throws FileNotFoundException {
        assertThrows(FileNotFoundException.class,
                     () -> new FileInputStream(f));
    }

    @Test
    @Order(1)
    public void exists() {
        assertTrue(f.exists());
    }

    @Test
    @Order(1)
    public void canExecute() {
        assertTrue(f.canExecute());
    }

    @Test
    @Order(1)
    public void canRead() {
        assertTrue(f.canRead());
    }

    @Test
    @Order(1)
    public void canWrite() {
        assertTrue(f.canWrite());
    }

    @Test
    @Order(1)
    public void getFreeSpace() {
        assertTrue(f.getFreeSpace() > 0);
    }

    @Test
    @Order(1)
    public void getTotalSpace() {
        assertTrue(f.getTotalSpace() > 0);
    }

    @Test
    @Order(1)
    public void getUsableSpace() {
        assertTrue(f.getUsableSpace() > 0);
    }

    @Test
    @Order(1)
    public void isDirectory() {
        assertTrue(f.isDirectory());
    }

    @Test
    @Order(1)
    public void isFile() {
        assertFalse(f.isFile());
    }

    @Test
    @Order(1)
    public void isHidden() {
        assertFalse(f.isHidden());
    }

    @Test
    @Order(1)
    public void lastModified() {
        assertTrue(f.lastModified() > 0);
    }

    @Test
    @Order(1)
    @DisabledOnOs({OS.WINDOWS})
    public void lengthUnix() throws IOException {
        assertTrue(f.length() > 0);
    }

    // Note: On Windows, File.length() can return zero when run in a
    // scratch directory.
    @Test
    @Order(1)
    @EnabledOnOs({OS.WINDOWS})
    public void lengthWindows() throws IOException {
        long len = f.length();
        assertTrue(len > 0 || (len == 0 && Files.size(f.toPath()) == 0));
    }

    @Test
    @Order(1)
    public void list() {
        String[] files = f.list();
        assertNotNull(files);
    }

    @Test
    @Order(1)
    public void listFiles() {
        File[] files = f.listFiles();
        assertNotNull(files);
    }

    // Note: Testing File.setExecutable is omitted because calling
    // File.setExecutable(false) makes it impossible to set the CWD to
    // exeuctable again which makes subsequent tests fail

    @Test
    @Order(3)
    @DisabledOnOs({OS.WINDOWS})
    public void setReadable() {
        assertTrue(f.canRead());
        assertTrue(f.setReadable(false));
        assertFalse(f.canRead());
        assertTrue(f.setReadable(true));
        assertTrue(f.canRead());
    }

    @Test
    @Order(3)
    @DisabledOnOs({OS.WINDOWS})
    public void setReadOnly() {
        assertTrue(f.canExecute());
        assertTrue(f.canRead());
        assertTrue(f.canWrite());
        assertTrue(f.setReadOnly());
        assertTrue(f.canRead());
        assertFalse(f.canWrite());
        assertTrue(f.setWritable(true));
        assertTrue(f.canWrite());
    }

    @Test
    @Order(3)
    @DisabledOnOs({OS.WINDOWS})
    public void setWritable() {
        assertTrue(f.canWrite());
        assertTrue(f.setWritable(false));
        assertFalse(f.canWrite());
        assertTrue(f.setWritable(true));
        assertTrue(f.canWrite());
    }

    @Test
    @Order(2)
    public void setLastModified() {
        long t0 = f.lastModified();
        long t = System.currentTimeMillis();
        assertTrue(f.setLastModified(t));
        assertEquals(t, f.lastModified());
        assertTrue(f.setLastModified(t0));
        assertEquals(t0, f.lastModified());
    }

    @Test
    @Order(1)
    public void toURI() {
        assertEquals(f.toURI(), f.toPath().toUri());
    }
}
