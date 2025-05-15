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
 * @library .. /test/lib
 * @build jdk.test.lib.Platform
 * @run junit DeleteReadOnly
 * @run junit/othervm -Djdk.io.File.allowDeleteReadOnlyFiles=true DeleteReadOnly
 */
import java.io.File;
import java.io.IOException;

import jdk.test.lib.Platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DeleteReadOnly {
    private static final String PROP = "jdk.io.File.allowDeleteReadOnlyFiles";
    private static final boolean DELETE_READ_ONLY = Boolean.getBoolean(PROP);

    private static final File DIR = new File(".", "dir");
    private static final File FILE = new File(DIR, "file");

    void deleteReadOnlyFile(File f) {
        f.setReadOnly();
        assertTrue(f.delete());
    }

    @Test
    void deleteReadOnlyRegularFile() throws IOException {
        assertTrue(DIR.mkdir());
        assertTrue(FILE.createNewFile());

        FILE.setReadOnly();

        boolean deleted = FILE.delete();
        boolean shouldBeDeleted = !Platform.isWindows() || DELETE_READ_ONLY;
        assertEquals(shouldBeDeleted, deleted);

        if (!deleted) {
            FILE.setWritable(true);
            assertTrue(FILE.delete());
        }

        assertTrue(DIR.delete());
    }

    @Test
    void deleteReadOnlyDirectory() throws IOException {
        assertTrue(DIR.mkdir());
        deleteReadOnlyFile(DIR);
    }
}
