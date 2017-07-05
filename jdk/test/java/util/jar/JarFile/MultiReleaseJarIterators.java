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

/*
 * @test
 * @bug 8132734
 * @summary Test the extended API and the aliasing additions in JarFile that
 *          support multi-release jar files
 * @library /lib/testlibrary/java/util/jar
 * @build Compiler JarBuilder CreateMultiReleaseTestJars
 * @run testng MultiReleaseJarIterators
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import static java.util.jar.JarFile.Release;
import static sun.misc.Version.jdkMajorVersion;  // fixme JEP 223 Version

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class MultiReleaseJarIterators {
    String userdir = System.getProperty("user.dir", ".");
    File unversioned = new File(userdir, "unversioned.jar");
    File multirelease = new File(userdir, "multi-release.jar");
    Map<String,JarEntry> uvEntries = new HashMap<>();
    Map<String,JarEntry> mrEntries = new HashMap<>();
    Map<String,JarEntry> baseEntries = new HashMap<>();
    Map<String,JarEntry> v9Entries = new HashMap<>();
    Map<String, JarEntry> v10Entries = new HashMap<>();

    @BeforeClass
    public void initialize() throws Exception {
        CreateMultiReleaseTestJars creator = new CreateMultiReleaseTestJars();
        creator.compileEntries();
        creator.buildUnversionedJar();
        creator.buildMultiReleaseJar();

        try (JarFile jf = new JarFile(multirelease)) {
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = e.nextElement();
                String name = je.getName();
                mrEntries.put(name, je);
                if (name.startsWith("META-INF/versions/")) {
                    if (name.startsWith("META-INF/versions/9/")) {
                        v9Entries.put(name.substring(20), je);
                    } else if (name.startsWith("META-INF/versions/10/")) {
                        v10Entries.put(name.substring(21), je);
                    }
                } else {
                    baseEntries.put(name, je);
                }
            }
        }
        Assert.assertEquals(mrEntries.size(), 14);
        Assert.assertEquals(baseEntries.size(), 6);
        Assert.assertEquals(v9Entries.size(), 5);
        Assert.assertEquals(v10Entries.size(), 3);

        try (JarFile jf = new JarFile(unversioned)) {
            jf.entries().asIterator().forEachRemaining(je -> uvEntries.put(je.getName(), je));
        }
        Assert.assertEquals(uvEntries.size(), 6);
    }

    @AfterClass
    public void close() throws IOException {
        Files.delete(unversioned.toPath());
        Files.delete(multirelease.toPath());
    }

    @Test
    public void testMultiReleaseJar() throws IOException {
        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ)) {
            testEnumeration(jf, mrEntries);
            testStream(jf, mrEntries);
        }

        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, Release.BASE)) {
            testEnumeration(jf, baseEntries);
            testStream(jf, baseEntries);
        }

        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, Release.VERSION_9)) {
            testEnumeration(jf, v9Entries);
            testStream(jf, v9Entries);
        }

        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, Release.RUNTIME)) {
            Map<String,JarEntry> expectedEntries;
            switch (jdkMajorVersion()) {
                case 9:
                    expectedEntries = v9Entries;
                    break;
                case 10:  // won't get here until JDK 10
                    expectedEntries = v10Entries;
                    break;
                default:
                    expectedEntries = baseEntries;
                    break;
            }

            testEnumeration(jf, expectedEntries);
            testStream(jf, expectedEntries);
        }
    }

    @Test
    public void testUnversionedJar() throws IOException {
        try (JarFile jf = new JarFile(unversioned, true, ZipFile.OPEN_READ)) {
            testEnumeration(jf, uvEntries);
            testStream(jf, uvEntries);
        }

        try (JarFile jf = new JarFile(unversioned, true, ZipFile.OPEN_READ, Release.BASE)) {
            testEnumeration(jf, uvEntries);
            testStream(jf, uvEntries);
        }

        try (JarFile jf = new JarFile(unversioned, true, ZipFile.OPEN_READ, Release.VERSION_9)) {
            testEnumeration(jf, uvEntries);
            testStream(jf, uvEntries);
        }

        try (JarFile jf = new JarFile(unversioned, true, ZipFile.OPEN_READ, Release.RUNTIME)) {
            testEnumeration(jf, uvEntries);
            testStream(jf, uvEntries);
        }
    }

    private void testEnumeration(JarFile jf, Map<String,JarEntry> expectedEntries) {
        Map<String, JarEntry> actualEntries = new HashMap<>();
        for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
            JarEntry je = e.nextElement();
            actualEntries.put(je.getName(), je);
        }

        testEntries(jf, actualEntries, expectedEntries);
    }


    private void testStream(JarFile jf, Map<String,JarEntry> expectedEntries) {
        Map<String,JarEntry> actualEntries = jf.stream().collect(Collectors.toMap(je -> je.getName(), je -> je));

        testEntries(jf, actualEntries, expectedEntries);
    }

    private void testEntries(JarFile jf, Map<String,JarEntry> actualEntries, Map<String,JarEntry> expectedEntries) {
        /* For multi-release jar files constructed with a Release object,
         * actualEntries contain versionedEntries that are considered part of the
         * public API.  They have a 1-1 correspondence with baseEntries,
         * so entries that are not part of the public API won't be present,
         * i.e. those entries with a name that starts with version/PackagePrivate
         * in this particular jar file (multi-release.jar)
         */

        Map<String,JarEntry> entries;
        if (expectedEntries == mrEntries) {
            Assert.assertEquals(actualEntries.size(), mrEntries.size());
            entries = mrEntries;
        } else if (expectedEntries == uvEntries) {
            Assert.assertEquals(actualEntries.size(), uvEntries.size());
            entries = uvEntries;
        } else {
            Assert.assertEquals(actualEntries.size(), baseEntries.size());  // this is correct
            entries = baseEntries;
        }

        entries.keySet().forEach(name -> {
            JarEntry ee = expectedEntries.get(name);
            if (ee == null) ee = entries.get(name);
            JarEntry ae = actualEntries.get(name);
            try {
                compare(jf, ae, ee);
            } catch (IOException x) {
                throw new RuntimeException(x);
            }
        });
    }

    private void compare(JarFile jf, JarEntry actual, JarEntry expected) throws IOException {
        byte[] abytes;
        byte[] ebytes;

        try (InputStream is = jf.getInputStream(actual)) {
            abytes = is.readAllBytes();
        }

        try (InputStream is = jf.getInputStream(expected)) {
            ebytes = is.readAllBytes();
        }

        Assert.assertEquals(abytes, ebytes);
    }
}
