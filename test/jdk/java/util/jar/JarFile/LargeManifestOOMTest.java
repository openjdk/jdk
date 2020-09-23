/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.JarUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.jar.JarFile;

/**
 * @test
 * @bug 8242882
 * @summary Verify that opening a jar file with a large manifest throws an OutOfMemoryError
 * and not a NegativeArraySizeException
 * @library /test/lib
 * @run testng LargeManifestOOMTest
 */
public class LargeManifestOOMTest {


    /**
     * Creates a jar which has a large manifest file and then uses the {@link JarFile} to
     * {@link JarFile#getManifest() load the manifest}. The call to the {@link JarFile#getManifest()}
     * is then expected to throw a {@link OutOfMemoryError}
     */
    @Test
    public void testOutOfMemoryError() throws Exception {
        final Path jarSourceRoot = Paths.get("jar-source");
        createLargeManifest(jarSourceRoot.resolve("META-INF"));
        final Path jarFilePath = Paths.get("oom-test.jar");
        JarUtils.createJarFile(jarFilePath.toAbsolutePath(), jarSourceRoot);
        final JarFile jar = new JarFile(jarFilePath.toFile());
        final OutOfMemoryError oome = Assert.expectThrows(OutOfMemoryError.class, () -> jar.getManifest());
        // additionally verify that the OOM was for the right/expected reason
        if (!"Required array size too large".equals(oome.getMessage())) {
            Assert.fail("Unexpected OutOfMemoryError", oome);
        }
    }

    /**
     * Creates a {@code MANIFEST.MF}, whose content is 2GB in size, in the {@code parentDir}
     *
     * @param parentDir The directory in which the MANIFEST.MF file will be created
     */
    private static void createLargeManifest(final Path parentDir) throws IOException {
        Files.createDirectories(parentDir.toAbsolutePath());
        final Path manifestFile = parentDir.resolve("MANIFEST.MF");
        try (final BufferedWriter bw = Files.newBufferedWriter(manifestFile)) {
            bw.write("Manifest-Version: 1.0");
            bw.newLine();
            bw.write("OOM-Test: ");
            for (long i = 0; i < 2147483648L; i++) {
                bw.write("a");
            }
            bw.newLine();
        }
    }

}
