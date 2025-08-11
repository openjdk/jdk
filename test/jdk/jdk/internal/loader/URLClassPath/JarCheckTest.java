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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import jdk.internal.loader.URLClassPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/*
 * @test
 * @bug 8341551
 * @summary verify the behaviour of URLClassPath in the presence/absence of
 *          sun.misc.URLClassPath.disableJarChecking system property
 *
 * @modules java.base/jdk.internal.loader
 *
 * @comment the following run is expected to run with jar checking enabled
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking=false JarCheckTest
 *
 * @comment the following runs are expected to run with jar checking disabled
 * @run junit JarCheckTest
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking= JarCheckTest
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking JarCheckTest
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking=true JarCheckTest
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking=FALSE JarCheckTest
 * @run junit/othervm -Dsun.misc.URLClassPath.disableJarChecking=foo JarCheckTest
 */
public class JarCheckTest {

    private static final Path SCRATCH_DIR = Path.of(".").normalize();
    private static final String SYS_PROP = "sun.misc.URLClassPath.disableJarChecking";
    private static final String RESOURCE_IN_NORMAL_JAR = "foo.txt";
    private static final String RESOURCE_IN_NOT_JUST_A_JAR = "bar.txt";


    private static final boolean jarCheckEnabled = "false".equals(System.getProperty(SYS_PROP));
    private static Path normalJar;
    private static Path notJustAJar; // JAR file with additional prefixed bytes

    @BeforeAll
    static void beforeAll() throws Exception {
        final Path tmpDir = Files.createTempDirectory(SCRATCH_DIR, "8341551");
        // create a normal JAR file
        normalJar = tmpDir.resolve("normal.jar");
        createJar(normalJar, RESOURCE_IN_NORMAL_JAR, false);

        // now create another JAR file and have its content prefixed with arbitrary bytes
        notJustAJar = tmpDir.resolve("notjustajar.jar");
        createJar(notJustAJar, RESOURCE_IN_NOT_JUST_A_JAR, true);
    }

    private static void createJar(final Path targetJarFile, final String entryName,
                                  final boolean prefixArbitraryBytes)
            throws IOException {

        Files.createFile(targetJarFile);
        if (prefixArbitraryBytes) {
            final byte[] arbitraryBytes = new byte[]{0x01, 0x02, 0x03, 0x04, 0x05, 0x06};
            Files.write(targetJarFile, arbitraryBytes);
        }
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        final OpenOption openOption = prefixArbitraryBytes
                ? StandardOpenOption.APPEND
                : StandardOpenOption.WRITE;
        try (OutputStream fos = Files.newOutputStream(targetJarFile, openOption);
             JarOutputStream jos = new JarOutputStream(fos, manifest)) {

            final JarEntry jarEntry = new JarEntry(entryName);
            jos.putNextEntry(jarEntry);
            jos.write("hello".getBytes(US_ASCII));
            jos.closeEntry();
        }
    }

    /*
     * Verifies that the URLClassPath always locates a resource from a normal JAR file
     * in the classpath and only conditionally locates a resource from a byte prefixed
     * JAR file in the classpath.
     */
    @Test
    public void testLocateResource() throws Exception {
        System.out.println("JAR check enabled=" + jarCheckEnabled);
        final URL[] classpath = new URL[]{
                new URI("jar:" + normalJar.toUri() + "!/").toURL(),
                new URI("jar:" + notJustAJar.toUri() + "!/").toURL()
        };
        final URLClassPath urlc = new URLClassPath(classpath);
        try {
            System.out.println(urlc + " will use classpath: " + Arrays.toString(classpath));
            // always expected to be found
            assertNotNull(urlc.findResource(RESOURCE_IN_NORMAL_JAR),
                    "missing resource " + RESOURCE_IN_NORMAL_JAR);
            // will be found only if jar check is disabled
            final URL resource = urlc.findResource(RESOURCE_IN_NOT_JUST_A_JAR);
            if (jarCheckEnabled) {
                assertNull(resource, "unexpectedly found " + RESOURCE_IN_NOT_JUST_A_JAR
                        + " at " + resource);
            } else {
                assertNotNull(resource, "missing resource " + RESOURCE_IN_NOT_JUST_A_JAR);
            }
        } finally {
            urlc.closeLoaders();
        }
    }
}
