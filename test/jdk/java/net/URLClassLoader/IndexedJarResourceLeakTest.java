/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.util.JarBuilder;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @test
 * @bug 8227020
 * @summary Tests that URLClassLoader properly closes resources corresponding to jar files loaded
 * from those listed in META-INF/INDEX.LIST
 * @library /test/lib
 * @run testng/othervm -Djdk.net.URLClassPath.enableJarIndex=true IndexedJarResourceLeakTest
 */
public class IndexedJarResourceLeakTest {

    private static Path jarFile;
    private static Path siblingJarFile;
    private static final String RESOURCE_CONTENT = "hello";
    private static final Path CWD = Path.of(".").toAbsolutePath().normalize();

    @BeforeTest
    public void beforeTest() throws Exception {
        jarFile = Files.createTempFile(CWD, "JDK-8227020-", ".jar");
        siblingJarFile = Files.createTempFile(CWD, "JDK-8227020-sibling", ".jar");
        // create jar index which will be as follows:
        // JarIndex-Version: 1.0
        //
        // sibling.jar
        // hello
        String jarIndexContent = "JarIndex-Version: 1.0 \n\n"
                + siblingJarFile.getFileName().toString() + "\n"
                + "hello\n\n";
        // create a jar with a jar index which contains an index entry for a sibling jar resources
        new JarBuilder(jarFile.toString())
                .addEntry("META-INF/INDEX.LIST", jarIndexContent.getBytes(StandardCharsets.UTF_8))
                .build();
        // create the sibling jar with the hello/ dir and hello/hello.txt entries
        new JarBuilder(siblingJarFile.toString())
                .addEntry("hello/", new byte[0])
                .addEntry("hello/hello.txt", RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    @AfterTest
    public void afterTest() throws IOException {
        Files.deleteIfExists(jarFile);
        Files.deleteIfExists(siblingJarFile);
    }

    /**
     * Creates a URLClassLoader with a path to one single jar file containing the
     * META-INF/INDEX.LIST. The index points to a sibling jar file for a particular
     * resource. The test then loads that resource through the URLClassLoader and
     * expects the resource to be found. Finally, the URLClassLoader is closed
     * and both the jar files (one that was passed to the URL classpath and
     * the other sibling jar which was listed in the index) are deleted.
     * The test expects that the deletion of these jar files works fine after the
     * URLClassLoader is closed.
     */
    @Test
    public void testIndexedResource() throws Exception {
        // Create a URLClassLoader with just the jar file in the classpath.
        // The sibling jar isn't added in the classpath list and instead is
        // expected to be picked up through the META-INF/INDEX.LIST entry
        try (URLClassLoader urlClassLoader = new URLClassLoader(
                new URL[]{jarFile.toUri().toURL()}, null)) {
            // load a resource that is part of the sibling jar
            try (InputStream is = urlClassLoader.getResourceAsStream("hello/hello.txt")) {
                Assert.assertNotNull(is, "Missing resource hello/hello.txt from URLClassLoader");
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                Assert.assertEquals(content, RESOURCE_CONTENT,
                        "Unexpected content in resource returned by classloader");
            }
        }
        // now attempt deleting each of these jars and expect the deletion to succeed
        Files.delete(jarFile);
        System.out.println("Successfully deleted " + jarFile);
        Files.delete(siblingJarFile);
        System.out.println("Successfully deleted " + siblingJarFile);
    }
}
