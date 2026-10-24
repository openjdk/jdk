/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @test
 * @bug 4313887 8062949 8191872
 * @library ..
 * @run junit SetLastModifiedTime
 * @summary Unit test for Files.setLastModifiedTime
 */

public class SetLastModifiedTime {

    static Path testDir;

    @BeforeAll
    static void createTestDirectory() throws Exception {
        testDir = TestUtil.createTemporaryDirectory();
    }

    @AfterAll
    static void removeTestDirectory() throws Exception {
        TestUtil.removeAll(testDir);
    }

    /**
     * Exercise Files.setLastModifiedTime on the given file
     */
    void test(Path path) throws Exception {
        FileTime now = Files.getLastModifiedTime(path);
        FileTime zero = FileTime.fromMillis(0L);

        Path result = Files.setLastModifiedTime(path, zero);
        assertSame(path, result);
        assertEquals(zero, Files.getLastModifiedTime(path));

        result = Files.setLastModifiedTime(path, now);
        assertSame(path, result);
        assertEquals(now, Files.getLastModifiedTime(path));
    }

    @Test
    public void testRegularFile() throws Exception {
        Path file = Files.createFile(testDir.resolve("file"));
        test(file);
    }

    @Test
    public void testDirectory() throws Exception {
        Path dir = Files.createDirectory(testDir.resolve("dir"));
        test(dir);
    }

    @Test
    public void testSymbolicLink() throws Exception {
        if (TestUtil.supportsSymbolicLinks(testDir)) {
            Path target = Files.createFile(testDir.resolve("target"));
            Path link = testDir.resolve("link");
            Files.createSymbolicLink(link, target);
            test(link);
        }
    }

    @Test
    public void testNulls() throws Exception {
        Path path = Paths.get("foo");
        FileTime zero = FileTime.fromMillis(0L);

        assertThrows(NullPointerException.class,
                     () -> Files.setLastModifiedTime(null, zero));

        assertThrows(NullPointerException.class,
                     () -> Files.setLastModifiedTime(path, null));

        assertThrows(NullPointerException.class,
                     () -> Files.setLastModifiedTime(null, null));
    }

    @Test
    public void testCompare() throws Exception {
        Path path = Files.createFile(testDir.resolve("path"));
        long timeMillis = 1512520600195L;
        FileTime fileTime = FileTime.fromMillis(timeMillis);
        Files.setLastModifiedTime(path, fileTime);
        File file = path.toFile();
        long ioTime = file.lastModified();
        long nioTime = Files.getLastModifiedTime(path).toMillis();
        assertTrue(ioTime == timeMillis || ioTime == 1000*(timeMillis/1000),
            "File.lastModified() not in {time, 1000*(time/1000)}");
        assertEquals(ioTime, nioTime,
            "File.lastModified() != Files.getLastModifiedTime().toMillis()");
    }
}

