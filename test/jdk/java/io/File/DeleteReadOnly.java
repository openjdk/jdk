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
 * @bug 8355954
 * @summary Verify correct behavior of File.delete
 * @run junit DeleteReadOnly
 * @run junit/othervm -Djdk.io.File.allowDeleteReadOnlyFiles=true DeleteReadOnly
 */
import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeleteReadOnly {
    private static final String PROP = "jdk.io.File.allowDeleteReadOnlyFiles";
    private static final boolean DELETE_READ_ONLY = Boolean.getBoolean(PROP);

    private static final File DIR = new File(".", "dir");
    private static final File FILE = new File(DIR, "file");

    @BeforeAll
    static void createFiles() throws IOException {
        DIR.mkdir();
        FILE.createNewFile();
    }

    // This test must be run first
    @Test
    @Order(1)
    @EnabledOnOs({OS.AIX, OS.LINUX, OS.MAC})
    void deleteReadOnlyFile() {
        FILE.setReadOnly();
        assertTrue(FILE.delete());
    }

    // This test must be run first
    @Test
    @Order(1)
    @EnabledOnOs({OS.WINDOWS})
    void deleteReadOnlyFileWin() {
        FILE.setReadOnly();

        boolean deleted = FILE.delete();
        assertEquals(DELETE_READ_ONLY, deleted);

        if (!deleted) {
            FILE.setWritable(true);
            assertTrue(FILE.delete());
        }
    }

    // This test must be run after DIR is empty
    @Test
    @Order(2)
    @EnabledOnOs({OS.AIX, OS.LINUX, OS.MAC})
    void deleteReadOnlyDir() {
        DIR.setReadOnly();
        assertTrue(DIR.delete());
    }

    // This test must be run after DIR is empty
    @Test
    @Order(2)
    @EnabledOnOs({OS.WINDOWS})
    void deleteReadOnlyDirWin() {
        DIR.setReadOnly();
        assertTrue(DIR.delete());
    }
}
