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

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/* @test
 * @bug 8354450
 * @requires os.family == "windows"
 * @summary Verify behavior for file names with a trailing space
 * @run junit WinTrailingSpace
 */
public class WinTrailingSpace {
    private static final String FILENAME_NO_TRAILING_SPACE = "foobargus";
    private static final String FILENAME_TRAILING_SPACE = "foobargus ";
    private static final String DIRNAME_TRAILING_SPACE = "foo \\bar\\gus";

    @Test
    public void noTrailingSpace() throws IOException {
        File f = null;
        try {
            f = new File(".", FILENAME_NO_TRAILING_SPACE);
            f.delete();
            f.createNewFile();
            assertTrue(f.exists());
        } finally {
            if (f != null)
                f.delete();
        }
    }

    @Test
    public void trailingSpace() throws IOException {
        File f = null;
        try {
            f = new File(".", FILENAME_TRAILING_SPACE);
            f.delete();
            f.createNewFile();
            assertFalse(f.exists(), "Creation of " + f + " should have failed");
        } catch (IOException expected) {
        } finally {
            if (f != null)
                f.delete();
        }
    }

    @Test
    public void dirTrailingSpace() throws IOException {
        File f = null;
        try {
            f = new File(".", DIRNAME_TRAILING_SPACE);
            f.delete();
            assertFalse(f.mkdirs(), "Creation of " + f + " should have failed");
        } finally {
            if (f != null)
                f.delete();
        }
    }
}
