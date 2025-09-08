/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4129479 8342086
 * @summary Test that available throws an IOException if the stream is
 *          closed, and that available works correctly with the NUL
 *          device on Windows
 * @run junit Available
 */

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.*;

public class Available {
    @Test
    void throwAfterClose() throws IOException {
        File file = new File(System.getProperty("test.src", "."),
                             "Available.java");
        FileInputStream fis = new FileInputStream(file);
        fis.close();
        assertThrows(IOException.class, () -> fis.available());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void nulDevice() throws IOException {
        File file = new File("nul");
        FileInputStream fis = new FileInputStream(file);
        int n = fis.available();
        assertEquals(0, n, "available() returned non-zero value");
    }
}
