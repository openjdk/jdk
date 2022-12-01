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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

public class ManifestDirectoryCompression {
    private static final ToolProvider JAR_TOOL =
            ToolProvider.findFirst("jar")
                    .orElseThrow(() -> new RuntimeException("jar tool not found"));

    private Path tempDir;

    @BeforeMethod
    private void setUp() throws Exception {
        tempDir = Files.createTempDirectory("temp");
    }

    /** Remove dirs & files needed for test. */
    @AfterMethod
    private void cleanup() {
        deleteRecursively(tempDir);
    }

    private static void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> s = Files.list(path)) {
                    s.forEach(p -> deleteRecursively(p));
                }
            }
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    public void run() throws Exception {
        Path entryPath = Files.writeString(tempDir.resolve("test.txt"), "Some text...");
        Path jar = tempDir.resolve("test.jar");
        String[] jarArgs = new String[] {"cf", jar.toString(), entryPath.toString()};
        if (JAR_TOOL.run(System.out, System.err, jarArgs) != 0) {
            fail("Could not create jar file: " + List.of(jarArgs));
        }
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            ZipEntry zipEntry = jarFile.getEntry("META-INF/");
            assertNotNull(zipEntry);
            assertEquals(zipEntry.getMethod(), ZipEntry.STORED);
            assertEquals(zipEntry.getSize(), 0);
            assertEquals(zipEntry.getCompressedSize(), 0);
        }
    }
}
