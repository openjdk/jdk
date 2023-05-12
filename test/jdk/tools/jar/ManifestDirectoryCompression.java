/*
 * Copyright (c) 2022, Alphabet LLC. All rights reserved.
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

/**
 * @test
 * @bug 8297875
 * @summary jar should not compress the manifest directory entry
 * @modules jdk.jartool
 * @run testng ManifestDirectoryCompression
 */
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.zip.ZipEntry;

public class ManifestDirectoryCompression {
    private static final ToolProvider JAR_TOOL =
            ToolProvider.findFirst("jar")
                    .orElseThrow(() -> new RuntimeException("jar tool not found"));

    private static final String JAR_FILE_NAME = "test.jar";
    private static final String FILE_NAME = "test.txt";

    @AfterMethod
    private void cleanup() throws Exception {
        Files.deleteIfExists(Path.of(JAR_FILE_NAME));
        Files.deleteIfExists(Path.of(FILE_NAME));
    }

    @Test
    public void testDirectoryCompressionMethod() throws Exception {
        Files.writeString(Path.of(FILE_NAME), "Some text...");
        String[] jarArgs = new String[] {"cf", JAR_FILE_NAME, FILE_NAME};
        if (JAR_TOOL.run(System.out, System.err, jarArgs) != 0) {
            fail("Could not create jar file: " + List.of(jarArgs));
        }
        try (JarFile jarFile = new JarFile(JAR_FILE_NAME)) {
            ZipEntry zipEntry = jarFile.getEntry("META-INF/");
            assertNotNull(zipEntry);
            assertEquals(zipEntry.getMethod(), ZipEntry.STORED);
            assertEquals(zipEntry.getSize(), 0);
            assertEquals(zipEntry.getCompressedSize(), 0);
        }
    }
}
