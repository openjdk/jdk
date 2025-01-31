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

import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jdk.internal.loader.Resource;
import jdk.internal.loader.URLClassPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.abort;

/*
 * @test
 * @bug 8258246
 * @summary verify that when locating resources, the URLClassPath can function properly
 *          when classpath elements contain Unicode characters with two, three or four
 *          byte UTF-8 encodings
 * @modules java.base/jdk.internal.loader
 * @run junit ClassPathUnicodeChars
 */
public class ClassPathUnicodeChars {

    private static final Path SCRATCH_DIR = Path.of(".").normalize();
    private static final String RESOURCE_NAME = "foo.txt";
    private static final String TWO_BYTE_CHAR = "\u00C4";
    private static final String THREE_BYTE_CHAR = "\u20AC";
    private static final String FOUR_BYTE_CHAR = "\uD83D\uDE00";

    private static Path TWO_BYTE_CHAR_DIR;
    private static Path THREE_BYTE_CHAR_DIR;
    private static Path FOUR_BYTE_CHAR_DIR;
    private static Path JAR_FILE_IN_TWO_BYTE_CHAR_DIR;
    private static Path JAR_FILE_IN_THREE_BYTE_CHAR_DIR;
    private static Path JAR_FILE_IN_FOUR_BYTE_CHAR_DIR;
    private static int NUM_EXPECTED_LOCATED_RESOURCES;

    @BeforeAll
    static void beforeAll() throws Exception {
        try {
            TWO_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, TWO_BYTE_CHAR);
            THREE_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, THREE_BYTE_CHAR);
            FOUR_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, FOUR_BYTE_CHAR);
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace(); // for debug purpose
            // if we can't create a directory with these characters in their
            // path name then skip the entire test
            abort("Skipping test since directory couldn't be created: " + iae);
        }
        // successful creation of the dir, continue with the test
        Files.createFile(TWO_BYTE_CHAR_DIR.resolve(RESOURCE_NAME));
        Files.createFile(THREE_BYTE_CHAR_DIR.resolve(RESOURCE_NAME));
        Files.createFile(FOUR_BYTE_CHAR_DIR.resolve(RESOURCE_NAME));

        // create jar files containing the resource
        JAR_FILE_IN_TWO_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, TWO_BYTE_CHAR)
                .resolve("foo.jar");
        JAR_FILE_IN_THREE_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, THREE_BYTE_CHAR)
                .resolve("foo.jar");
        JAR_FILE_IN_FOUR_BYTE_CHAR_DIR = Files.createTempDirectory(SCRATCH_DIR, FOUR_BYTE_CHAR)
                .resolve("foo.jar");
        for (Path jarFile : Arrays.asList(
                JAR_FILE_IN_TWO_BYTE_CHAR_DIR,
                JAR_FILE_IN_THREE_BYTE_CHAR_DIR,
                JAR_FILE_IN_FOUR_BYTE_CHAR_DIR)) {
            final Manifest manifest = new Manifest();
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
            try (OutputStream fos = Files.newOutputStream(jarFile);
                    JarOutputStream jos = new JarOutputStream(fos, manifest)) {

                final JarEntry jarEntry = new JarEntry(RESOURCE_NAME);
                jos.putNextEntry(jarEntry);
                jos.write("hello".getBytes(US_ASCII));
                jos.closeEntry();
            }
        }
        // We expect to find the resource in all classpath elements.
        NUM_EXPECTED_LOCATED_RESOURCES = 6;
    }

    /**
     * Constructs a URLClassPath and then exercises the URLClassPath.findResource()
     * and URLClassPath.findResources() methods and expects them to return the
     * expected
     * resources.
     */
    @Test
    void testFindResource() {
        // start an empty URL classpath
        final URLClassPath urlc = new URLClassPath(new URL[0]);
        final String[] classpathElements = getClassPathElements();
        try {
            // use addFile() to construct classpath
            for (final String path : classpathElements) {
                urlc.addFile(path);
            }
            // findResource()
            assertNotNull(urlc.findResource(RESOURCE_NAME), "findResource() failed to locate"
                    + " resource: " + RESOURCE_NAME + " in classpath: "
                    + Arrays.toString(classpathElements));
            // findResources()
            final Enumeration<URL> locatedResources = urlc.findResources(RESOURCE_NAME);
            assertNotNull(locatedResources, "findResources() failed to"
                    + " locate resource: " + RESOURCE_NAME + " in classpath: "
                    + Arrays.toString(classpathElements));
            int numFound = 0;
            while (locatedResources.hasMoreElements()) {
                System.out.println("located " + locatedResources.nextElement()
                        + " for resource " + RESOURCE_NAME);
                numFound++;
            }
            assertEquals(NUM_EXPECTED_LOCATED_RESOURCES, numFound,
                    "unexpected number of resources located for " + RESOURCE_NAME);
        } finally {
            urlc.closeLoaders();
        }
    }

    /**
     * Constructs a URLClassPath and then exercises the URLClassPath.getResource()
     * and URLClassPath.getResources() methods and expects them to return the
     * expected
     * resources.
     */
    @Test
    void testGetResource() {
        // start an empty URL classpath
        final URLClassPath urlc = new URLClassPath(new URL[0]);
        final String[] classpathElements = getClassPathElements();
        try {
            // use addFile() to construct classpath
            for (final String path : classpathElements) {
                urlc.addFile(path);
            }
            // getResource()
            assertNotNull(urlc.getResource(RESOURCE_NAME), "getResource() failed to locate"
                    + " resource: " + RESOURCE_NAME + " in classpath: "
                    + Arrays.toString(classpathElements));
            // getResources()
            final Enumeration<Resource> locatedResources = urlc.getResources(RESOURCE_NAME);
            assertNotNull(locatedResources, "getResources() failed to"
                    + " locate resource: " + RESOURCE_NAME + " in classpath: "
                    + Arrays.toString(classpathElements));
            int numFound = 0;
            while (locatedResources.hasMoreElements()) {
                System.out.println("located " + locatedResources.nextElement().getURL()
                        + " for resource " + RESOURCE_NAME);
                numFound++;
            }
            assertEquals(NUM_EXPECTED_LOCATED_RESOURCES, numFound,
                    "unexpected number of resources located for " + RESOURCE_NAME);
        } finally {
            urlc.closeLoaders();
        }
    }

    private static String[] getClassPathElements() {
        return new String[] {
                TWO_BYTE_CHAR_DIR.toString(),
                THREE_BYTE_CHAR_DIR.toString(),
                FOUR_BYTE_CHAR_DIR.toString(),
                JAR_FILE_IN_TWO_BYTE_CHAR_DIR.toString(),
                JAR_FILE_IN_THREE_BYTE_CHAR_DIR.toString(),
                JAR_FILE_IN_FOUR_BYTE_CHAR_DIR.toString()
        };
    }
}
