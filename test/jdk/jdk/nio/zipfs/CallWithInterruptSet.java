/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316882
 * @run testng CallWithInterruptSet
 * @summary Test invoking ZipFS methods with the interrupt status set
 */

import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class CallWithInterruptSet {

    @Test
    public void testReadAllBytes() throws Exception {
        Path file = Files.createTempFile(Path.of("."), "tmp", ".zip");
        try (var zout = new ZipOutputStream(Files.newOutputStream(file))) {
            zout.putNextEntry(new ZipEntry("entry"));
            zout.write("HEHE".getBytes(StandardCharsets.UTF_8), 0, 4);
            zout.closeEntry();
        }
        try (var zipfs = FileSystems.newFileSystem(file)) {
            var zippath = zipfs.getPath("entry");
            Thread.currentThread().interrupt();
            assertEquals(
                    Files.readAllBytes(zippath),
                    "HEHE".getBytes(StandardCharsets.UTF_8));
        }
        assertTrue(Thread.interrupted()); // clear interrupt
    }

}

