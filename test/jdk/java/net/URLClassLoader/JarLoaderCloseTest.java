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

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @test
 * @bug 8338445
 * @summary verify that the jdk.internal.loader.URLClassPath closes the JarFile
 *          instances that it no longer uses for loading
 * @library /test/lib
 * @build jdk.test.lib.util.JarUtils
 * @comment This test expects MalformedURLException for some specific URLs.
 *          We use othervm to prevent interference from other tests which
 *          might have installed custom URLStreamHandler(s)
 * @run junit/othervm JarLoaderCloseTest
 */
public class JarLoaderCloseTest {

    private static final String RESOURCE_NAME = "foo-bar.txt";
    private static final String RESOURCE_CONTENT = "Hello world";
    private static final Path TEST_SCRATCH_DIR = Path.of(".");

    @BeforeAll
    static void beforeAll() throws Exception {
        // create a file which will be added to the JAR file that gets tested
        Files.writeString(TEST_SCRATCH_DIR.resolve(RESOURCE_NAME), RESOURCE_CONTENT);
    }

    /*
     * Creates a JAR file with a manifest which has a Class-Path entry value with malformed URLs.
     * Then uses a URLClassLoader backed by the JAR file in its classpath, loads some resource,
     * closes the URLClassLoader and then expects that the underlying JAR file can be deleted
     * from the filesystem.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "C:\\foo\\bar\\hello/world.jar lib2.jar",
            "C:/hello/world/foo.jar",
            "lib4.jar C:\\bar\\foo\\world/hello.jar"
    })
    public void testMalformedClassPathEntry(final String classPathValue) throws Exception {
        final Manifest manifest = createManifestWithClassPath(classPathValue);
        final Path jar = Files.createTempFile(TEST_SCRATCH_DIR, "8338445", ".jar");
        // create the JAR file with the given manifest and an arbitrary file
        JarUtils.createJarFile(jar, manifest, TEST_SCRATCH_DIR, Path.of(RESOURCE_NAME));
        System.out.println("created jar at " + jar + " with manifest:");
        manifest.write(System.out);
        final URL[] urlClassPath = new URL[]{jar.toUri().toURL()};
        // Create a URLClassLoader backed by the JAR file and load a non-existent resource just to
        // exercise the URLClassPath code of loading the jar and parsing the Class-Path entry.
        // Then close the classloader. After the classloader is closed
        // issue a delete on the underlying JAR file on the filesystem. The delete is expected
        // to succeed.
        try (final URLClassLoader cl = new URLClassLoader(urlClassPath)) {
            try (final InputStream is = cl.getResourceAsStream("non-existent.txt")) {
                assertNull(is, "unexpectedly found a resource in classpath "
                        + Arrays.toString(urlClassPath));
            }
        }
        // now delete the JAR file and verify the delete worked
        Files.delete(jar);
        assertFalse(Files.exists(jar), jar + " exists even after being deleted");
    }

    /*
     * Creates a JAR file with a manifest which has a Class-Path entry value with URLs
     * that are parsable but point to files that don't exist on the filesystem.
     * Then uses a URLClassLoader backed by the JAR file in its classpath, loads some resource,
     * closes the URLClassLoader and then expects that the underlying JAR file can be deleted
     * from the filesystem.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/home/me/hello/world.jar lib9.jar",
            "lib10.jar"
    })
    public void testParsableClassPathEntry(final String classPathValue) throws Exception {
        final Manifest manifest = createManifestWithClassPath(classPathValue);
        final Path jar = Files.createTempFile(TEST_SCRATCH_DIR, "8338445", ".jar");
        // create the JAR file with the given manifest and an arbitrary file
        JarUtils.createJarFile(jar, manifest, TEST_SCRATCH_DIR, Path.of(RESOURCE_NAME));
        System.out.println("created jar at " + jar + " with manifest:");
        manifest.write(System.out);
        final URL[] urlClassPath = new URL[]{jar.toUri().toURL()};
        // Create a URLClassLoader backed by the JAR file and load a resource
        // and verify the resource contents.
        // Then close the classloader. After the classloader is closed
        // issue a delete on the underlying JAR file on the filesystem. The delete is expected
        // to succeed.
        try (final URLClassLoader cl = new URLClassLoader(urlClassPath)) {
            try (final InputStream is = cl.getResourceAsStream(RESOURCE_NAME)) {
                assertNotNull(is, RESOURCE_NAME + " not located by classloader in classpath "
                        + Arrays.toString(urlClassPath));
                final String content = new String(is.readAllBytes(), US_ASCII);
                assertEquals(RESOURCE_CONTENT, content, "unexpected content in " + RESOURCE_NAME);
            }
        }
        // now delete the JAR file and verify the delete worked
        Files.delete(jar);
        assertFalse(Files.exists(jar), jar + " exists even after being deleted");
    }

    private static Manifest createManifestWithClassPath(final String classPathValue) {
        final Manifest manifest = new Manifest();
        final Attributes mainAttributes = manifest.getMainAttributes();
        mainAttributes.putValue("Manifest-Version", "1.0");
        mainAttributes.putValue("Class-Path", classPathValue);
        return manifest;
    }
}
