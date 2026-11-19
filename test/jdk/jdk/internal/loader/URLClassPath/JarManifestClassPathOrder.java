/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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


import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
 * @test
 * @summary Verify that URLClassPath discovers JAR Class-Path URLs in the expected order
 * @run junit JarManifestClassPathOrder
 */
public class JarManifestClassPathOrder {

    // Name of the JAR resource use for lookups
    private static final String ENTRY_NAME = "JarClassPathOrdering.txt";

    /**
     * Verify that URLClassPath discovers JAR files in the expected order when
     * only root JARs are on the 'original' class path and the other JARs are
     * found via multiple levels of Class-Path Manifest attributes.
     *
     * @throws IOException if an unexpected error occurs
     */
    @Test
    public void shouldLoadJARsInExpectedOrder() throws IOException {

        // Set up a 'Class-Path' JAR tree of depth 3
        JarTree specs = new JarTree();
        specs.addJars(3);

        // List of "root" JAR file URLs to use for URLClassLoader
        List<URL> searchPath = new ArrayList<>();

        // Order of JAR files we expect URLClassPath to find after a DFS search
        List<String> expectedJarNames = new ArrayList<>();

        for (JarSpec spec : specs.dfs) {
            Path jar = createJar(spec);
            expectedJarNames.add(jar.getFileName().toString());
            // Only root JARs in the search path, others are discovered transitively via "Class-Path"
            if (spec.isRoot()) {
                searchPath.add(jar.toUri().toURL());
            }
        }

        // Load ENTRY_NAME to identify all JARs found by URLClassPath
        try (URLClassLoader loader = new URLClassLoader(searchPath.toArray(new URL[0]))) {
            Enumeration<URL> resources = loader.getResources(ENTRY_NAME);
            // Collect all JAR file names in the order discovered by URLClassPath
            List<String> actualJarNames = Collections.list(resources)
                    .stream()
                    .map(this::extractJarName)
                    .collect(Collectors.toList());
            // JARs should be found in DFS order
            assertEquals(expectedJarNames, actualJarNames, "JAR files not found in expected order");
        }
    }

    // Extract file name of JAR file from a JAR URL
    private String extractJarName(URL url) {
        String jarPath = url.getPath().substring(0, url.getPath().indexOf("!/"));
        String jarName = jarPath.substring(jarPath.lastIndexOf('/') + 1);
        return jarName;
    }

    // Create a JAR file according to the spec, possibly including a Class-Path attribute
    private Path createJar(JarSpec spec) throws IOException {
        Path file = Path.of(spec.name +".jar");
        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");

        // Set Class-Path attribute
        if (!spec.children.isEmpty()) {
            String path = String.join(" ", spec.children
                    .stream()
                    .map(js -> js.name +".jar")
                    .collect(Collectors.toList()));
            attrs.put(Attributes.Name.CLASS_PATH, path);
        }

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file), man)) {
            // All JARs include the same entry
            out.putNextEntry(new JarEntry(ENTRY_NAME));
        }

        return file;
    }


    // Helper class to represent a tree of JARs related by "Class-Path"
    static class JarTree {
        static final List<String> NAMES = List.of("a", "b", "c");
        List<JarSpec> dfs = new ArrayList<>();

        private void addJar(String prefix, String name, JarSpec parent, int depth, int maxDepth) {
            if (depth > maxDepth) {
                return;
            }
            JarSpec spec = new JarSpec(prefix + name, parent);
            dfs.add(spec);

            if (parent != null) {
                parent.children.add(spec);
            }

            for (String childName : NAMES) {
                addJar(prefix + name, childName, spec, depth + 1, maxDepth);
            }
        }

        /* Make a tree of JARs related by the 'Class-Path' Manifest attribute:
         * a.jar
         *  aa.jar
         *    aaa.jar
         *    aab.jar
         * [...]
         *    ccc.jar
         */
        public void addJars(int maxDepth) {
            for (String name : NAMES) {
                addJar("", name, null, 1, maxDepth);
            }
        }
    }

    // Helper class to represent a JAR file to be found by URLClassPath
    static class JarSpec {
        final String name;
        final JarSpec parent;
        final List<JarSpec> children = new ArrayList<>();

        JarSpec(String name, JarSpec parent) {
            this.name = name;
            this.parent = parent;
        }
        boolean isRoot() {
            return parent == null;
        }
    }
}
