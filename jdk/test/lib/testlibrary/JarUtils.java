/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class consists exclusively of static utility methods that are useful
 * for creating and manipulating JAR files.
 */

public final class JarUtils {
    private JarUtils() { }

    /**
     * Creates a JAR file.
     *
     * Equivalent to {@code jar cfm <jarfile> <manifest> -C <dir> file...}
     *
     * The input files are resolved against the given directory. Any input
     * files that are directories are processed recursively.
     */
    public static void createJarFile(Path jarfile, Manifest man, Path dir, Path... file)
        throws IOException
    {
        // create the target directory
        Path parent = jarfile.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        List<Path> entries = new ArrayList<>();
        for (Path entry : file) {
            Files.find(dir.resolve(entry), Integer.MAX_VALUE,
                        (p, attrs) -> attrs.isRegularFile())
                    .map(e -> dir.relativize(e))
                    .forEach(entries::add);
        }

        try (OutputStream out = Files.newOutputStream(jarfile);
             JarOutputStream jos = new JarOutputStream(out))
        {
            if (man != null) {
                JarEntry je = new JarEntry(JarFile.MANIFEST_NAME);
                jos.putNextEntry(je);
                man.write(jos);
                jos.closeEntry();
            }

            for (Path entry : entries) {
                String name = toJarEntryName(entry);
                jos.putNextEntry(new JarEntry(name));
                Files.copy(dir.resolve(entry), jos);
                jos.closeEntry();
            }
        }
    }

    /**
     * Creates a JAR file.
     *
     * Equivalent to {@code jar cf <jarfile>  -C <dir> file...}
     *
     * The input files are resolved against the given directory. Any input
     * files that are directories are processed recursively.
     */
    public static void createJarFile(Path jarfile, Path dir, Path... file)
        throws IOException
    {
        createJarFile(jarfile, null, dir, file);
    }

    /**
     * Creates a JAR file.
     *
     * Equivalent to {@code jar cf <jarfile> -C <dir> file...}
     *
     * The input files are resolved against the given directory. Any input
     * files that are directories are processed recursively.
     */
    public static void createJarFile(Path jarfile, Path dir, String... input)
        throws IOException
    {
        Path[] paths = Stream.of(input).map(Paths::get).toArray(Path[]::new);
        createJarFile(jarfile, dir, paths);
    }

    /**
     * Creates a JAR file from the contents of a directory.
     *
     * Equivalent to {@code jar cf <jarfile> -C <dir> .}
     */
    public static void createJarFile(Path jarfile, Path dir) throws IOException {
        createJarFile(jarfile, dir, Paths.get("."));
    }

    /**
     * Update a JAR file.
     *
     * Equivalent to {@code jar uf <jarfile> -C <dir> file...}
     *
     * The input files are resolved against the given directory. Any input
     * files that are directories are processed recursively.
     */
    public static void updateJarFile(Path jarfile, Path dir, Path... file)
        throws IOException
    {
        List<Path> entries = new ArrayList<>();
        for (Path entry : file) {
            Files.find(dir.resolve(entry), Integer.MAX_VALUE,
                    (p, attrs) -> attrs.isRegularFile())
                    .map(e -> dir.relativize(e))
                    .forEach(entries::add);
        }

        Set<String> names = entries.stream()
                .map(JarUtils::toJarEntryName)
                .collect(Collectors.toSet());

        Path tmpfile = Files.createTempFile("jar", "jar");

        try (OutputStream out = Files.newOutputStream(tmpfile);
             JarOutputStream jos = new JarOutputStream(out))
        {
            // copy existing entries from the original JAR file
            try (JarFile jf = new JarFile(jarfile.toString())) {
                Enumeration<JarEntry> jentries = jf.entries();
                while (jentries.hasMoreElements()) {
                    JarEntry jentry = jentries.nextElement();
                    if (!names.contains(jentry.getName())) {
                        jos.putNextEntry(jentry);
                        jf.getInputStream(jentry).transferTo(jos);
                    }
                }
            }

            // add the new entries
            for (Path entry : entries) {
                String name = toJarEntryName(entry);
                jos.putNextEntry(new JarEntry(name));
                Files.copy(dir.resolve(entry), jos);
            }
        }

        // replace the original JAR file
        Files.move(tmpfile, jarfile, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Update a JAR file.
     *
     * Equivalent to {@code jar uf <jarfile> -C <dir> .}
     */
    public static void updateJarFile(Path jarfile, Path dir) throws IOException {
        updateJarFile(jarfile, dir, Paths.get("."));
    }


    /**
     * Map a file path to the equivalent name in a JAR file
     */
    private static String toJarEntryName(Path file) {
        Path normalized = file.normalize();
        return normalized.subpath(0, normalized.getNameCount())  // drop root
                .toString()
                .replace(File.separatorChar, '/');
    }
}
