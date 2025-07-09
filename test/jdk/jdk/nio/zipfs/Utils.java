/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Random;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Utility class for {@code ZipFileSystem} tests.
 */
final class Utils {
    private Utils() {}

    /**
     * Creates a JAR file of the given name with 0 or more named entries with
     * random content.
     *
     * <p>If an existing file of the same name already exists, it is silently
     * overwritten.
     *
     * @param name the file name of the jar file to create in the working directory.
     * @param entries entries JAR file entry names, whose content will be populated
     *                with random bytes
     * @return the absolute path to the newly created JAR file.
     */
    static Path createJarFile(String name, String... entries) throws IOException {
        Path jarFile = Path.of(name);
        Random rand = new Random();
        try (OutputStream out = Files.newOutputStream(jarFile);
             JarOutputStream jout = new JarOutputStream(out)) {
            int len = 100;
            for (String entry: entries) {
                JarEntry je = new JarEntry(entry);
                jout.putNextEntry(je);
                byte[] bytes = new byte[len];
                rand.nextBytes(bytes);
                jout.write(bytes);
                jout.closeEntry();
                len += 1024;
            }
        }
        return jarFile.toAbsolutePath();
    }

    /**
     * Creates a JAR file of the given name with 0 or more entries with specified
     * content.
     *
     * <p>If an existing file of the same name already exists, it is silently
     * overwritten.
     *
     * @param name the file name of the jar file to create in the working directory.
     * @param entries a map of JAR file entry names to entry content (stored as
     *                UTF-8 encoded bytes).
     * @return the absolute path to the newly created JAR file.
     */
    static Path createJarFile(String name, Map<String, String> entries) throws IOException {
        Path jarFile = Path.of(name);
        try (OutputStream out = Files.newOutputStream(jarFile);
             JarOutputStream jout = new JarOutputStream(out)) {
            for (var entry : entries.entrySet()) {
                JarEntry je = new JarEntry(entry.getKey());
                jout.putNextEntry(je);
                jout.write(entry.getValue().getBytes(UTF_8));
                jout.closeEntry();
            }
        }
        return jarFile.toAbsolutePath();
    }
}
