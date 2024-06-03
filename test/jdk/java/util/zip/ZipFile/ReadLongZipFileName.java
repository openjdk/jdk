/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/*
 * @test
 * @bug 6374379
 * @summary Verify that we can read zip file names > 255 chars long
 * @run junit ReadLongZipFileName
 */
public class ReadLongZipFileName {
    private static final String ENTRY_NAME = "testFile.txt";
    private static final String LONG_DIR_NAME = "abcdefghijklmnopqrstuvwx"; // 24 chars
    private static final String JAR_FILE_NAME = "areallylargejarfilename.jar"; // 27 chars

    /*
     * Creates a jar file at a path whose path name length and jar file name length
     * combined is large. Then use the java.util.jar.JarFile APIs to open and read the jar file
     * to verify the APIs work against those jar/zip files.
     */
    @Test
    public void testOpenAndReadJarFile() throws Exception {
        int minRequiredPathLength = 600; // long enough to definitely fail.
        Path tmpDir = Files.createTempDirectory(Path.of("."), "ReadLongZipFileName-test")
                .normalize().toAbsolutePath();
        // Create a directory structure long enough that the filename will
        // put us over the minRequiredLength.
        int currentPathLength = 0;
        Path jarFileDir = tmpDir;
        do {
            jarFileDir = jarFileDir.resolve(LONG_DIR_NAME);
            Files.createDirectories(jarFileDir);
            currentPathLength = jarFileDir.toFile().getCanonicalPath().length();
        } while (currentPathLength < (minRequiredPathLength - JAR_FILE_NAME.length()));

        // Create a new Jar file: use jar instead of zip to make sure long
        // names work for both zip and jar subclass.
        Path jarFilePath = jarFileDir.resolve(JAR_FILE_NAME);
        System.out.println("creating a jar file at " + jarFilePath);
        try (JarOutputStream out = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(jarFilePath)))) {
            out.putNextEntry(new JarEntry(ENTRY_NAME));
            out.write(1);
        }
        assertTrue(Files.isRegularFile(jarFilePath),
                "jar file " + jarFilePath + " does not exist or is not a file");

        try (JarFile readJarFile = new JarFile(jarFilePath.toFile())) {
            JarEntry je = readJarFile.getJarEntry(ENTRY_NAME);
            assertNotNull(je, "missing jar entry: " + ENTRY_NAME + " in jar file " + jarFilePath);
            DataInputStream dis = new DataInputStream(readJarFile.getInputStream(je));
            byte val = dis.readByte();
            assertEquals(1, val, "unexpected byte " + val + " read from entry " + ENTRY_NAME);
            try {
                dis.readByte();
                Assertions.fail("Read past expected EOF");
            } catch (IOException e) {
                // expected
                System.out.println("received the expected exception: " + e);
            }
        }
        System.out.println("Successfully opened and read contents from a jar file with a name "
                + jarFilePath.toFile().getCanonicalPath().length() + " characters long");
    }
}
