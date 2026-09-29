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
 * @bug 8344908
 * @summary verify that when locating resources, the URLClassPath can function properly
 *          without throwing unexpected exceptions when any URL in the classpath is unusable
 * @modules java.base/jdk.internal.loader
 * @run junit ClassPathUnusableURLs
 */
public class ClassPathUnusableURLs {

    private static final Path SCRATCH_DIR = Path.of(".").normalize();
    private static final String RESOURCE_NAME = "foo.txt";
    private static final String SMILEY_EMOJI = "\uD83D\uDE00";

    private static Path ASCII_DIR;
    private static Path EMOJI_DIR;
    private static Path JAR_FILE_IN_EMOJI_DIR;
    private static int NUM_EXPECTED_LOCATED_RESOURCES;


    @BeforeAll
    static void beforeAll() throws Exception {
        try {
            EMOJI_DIR = Files.createTempDirectory(SCRATCH_DIR, SMILEY_EMOJI);
        } catch (IllegalArgumentException iae) {
            iae.printStackTrace(); // for debug purpose
            // if we can't create a directory with an emoji in its path name,
            // then skip the entire test
            abort("Skipping test since emoji directory couldn't be created: " + iae);
        }
        // successful creation of the dir, continue with the test
        Files.createFile(EMOJI_DIR.resolve(RESOURCE_NAME));

        ASCII_DIR = Files.createTempDirectory(SCRATCH_DIR, "test-urlclasspath");
        Files.createFile(ASCII_DIR.resolve(RESOURCE_NAME));

        // create a jar file containing the resource
        JAR_FILE_IN_EMOJI_DIR = Files.createTempDirectory(SCRATCH_DIR, SMILEY_EMOJI)
                .resolve("foo.jar");
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        try (OutputStream fos = Files.newOutputStream(JAR_FILE_IN_EMOJI_DIR);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            final JarEntry jarEntry = new JarEntry(RESOURCE_NAME);
            jos.putNextEntry(jarEntry);
            jos.write("hello".getBytes(US_ASCII));
            jos.closeEntry();
        }
        // Even if the resource is present in more than one classpath element,
        // we expect it to be found by the URLClassPath only in the path which has just ascii
        // characters. URLClassPath currently doesn't have the ability to serve resources
        // from paths containing emoji character(s).
        NUM_EXPECTED_LOCATED_RESOURCES = 1;
    }

    /**
     * Constructs a URLClassPath and then exercises the URLClassPath.findResource()
     * and URLClassPath.findResources() methods and expects them to return the expected
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
     * and URLClassPath.getResources() methods and expects them to return the expected
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
        // Maintain the order - in context of this test, paths with emojis
        // or those which can't serve the resource should come before the
        // path that can serve the resource.
        return new String[]{
                // non-existent path
                ASCII_DIR.resolve("non-existent").toString(),
                // existing emoji dir
                EMOJI_DIR.toString(),
                // existing jar file in a emoji dir
                JAR_FILE_IN_EMOJI_DIR.toString(),
                // existing ascii dir
                ASCII_DIR.toString()
        };
    }
}
