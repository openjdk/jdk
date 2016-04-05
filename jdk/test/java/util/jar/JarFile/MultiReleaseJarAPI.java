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
 * @run testng MultiReleaseJarAPI
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jdk.Version;

import static java.util.jar.JarFile.Release;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;


public class MultiReleaseJarAPI {

    static final int MAJOR_VERSION = Version.current().major();

    String userdir = System.getProperty("user.dir",".");
    File unversioned = new File(userdir, "unversioned.jar");
    File multirelease = new File(userdir, "multi-release.jar");
    File signedmultirelease = new File(userdir, "signed-multi-release.jar");
    Release[] values = JarFile.Release.values();


    @BeforeClass
    public void initialize() throws Exception {
        CreateMultiReleaseTestJars creator =  new CreateMultiReleaseTestJars();
        creator.compileEntries();
        creator.buildUnversionedJar();
        creator.buildMultiReleaseJar();
        creator.buildSignedMultiReleaseJar();
    }

    @AfterClass
    public void close() throws IOException {
        Files.delete(unversioned.toPath());
        Files.delete(multirelease.toPath());
        Files.delete(signedmultirelease.toPath());
    }

    @Test
    public void isMultiReleaseJar() throws Exception {
        try (JarFile jf = new JarFile(unversioned)) {
            Assert.assertFalse(jf.isMultiRelease());
        }

        try (JarFile jf = new JarFile(multirelease)) {
            Assert.assertFalse(jf.isMultiRelease());
        }

        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, Release.RUNTIME)) {
            Assert.assertTrue(jf.isMultiRelease());
        }
    }

    @Test
    public void testVersioning() throws Exception {
        // multi-release jar
        JarFile jar = new JarFile(multirelease);
        Assert.assertEquals(Release.BASE, jar.getVersion());
        jar.close();

        for (Release value : values) {
            System.err.println("test versioning for Release " + value);
            try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, value)) {
                Assert.assertEquals(value, jf.getVersion());
            }
        }

        // regular, unversioned, jar
        for (Release value : values) {
            try (JarFile jf = new JarFile(unversioned, true, ZipFile.OPEN_READ, value)) {
                Assert.assertEquals(Release.BASE, jf.getVersion());
            }
        }

        // assure that we have a Release object corresponding to the actual runtime version
        String version = "VERSION_" + MAJOR_VERSION;
        boolean runtimeVersionExists = false;
        for (Release value : values) {
            if (version.equals(value.name())) runtimeVersionExists = true;
        }
        Assert.assertTrue(runtimeVersionExists);
    }

    @Test
    public void testAliasing() throws Exception {
        for (Release value : values) {
            System.err.println("test aliasing for Release " + value);
            String name = value.name();
            String prefix;
            if (name.equals("BASE")) {
                prefix = "";
            } else if (name.equals("RUNTIME")) {
                prefix = "META-INF/versions/" + MAJOR_VERSION + "/";
            } else {
                prefix = "META-INF/versions/" + name.substring(8) + "/";
            }
            // test both multi-release jars
            readAndCompare(multirelease, value, "README", prefix + "README");
            readAndCompare(multirelease, value, "version/Version.class", prefix + "version/Version.class");
            // and signed multi-release jars
            readAndCompare(signedmultirelease, value, "README", prefix + "README");
            readAndCompare(signedmultirelease, value, "version/Version.class", prefix + "version/Version.class");
        }
    }

    private void readAndCompare(File jar, Release version, String name, String realName) throws Exception {
        byte[] baseBytes;
        byte[] versionedBytes;
        try (JarFile jf = new JarFile(jar, true, ZipFile.OPEN_READ, Release.BASE)) {
            ZipEntry ze = jf.getEntry(realName);
            try (InputStream is = jf.getInputStream(ze)) {
                baseBytes = is.readAllBytes();
            }
        }
        assert baseBytes.length > 0;

        try (JarFile jf = new JarFile(jar, true, ZipFile.OPEN_READ, version)) {
            ZipEntry ze = jf.getEntry(name);
            try (InputStream is = jf.getInputStream(ze)) {
                versionedBytes = is.readAllBytes();
            }
        }
        assert versionedBytes.length > 0;

        Assert.assertTrue(Arrays.equals(baseBytes, versionedBytes));
    }

    @Test
    public void testNames() throws Exception {
        String rname = "version/Version.class";
        String vname = "META-INF/versions/9/version/Version.class";
        ZipEntry ze1;
        ZipEntry ze2;
        try (JarFile jf = new JarFile(multirelease)) {
            ze1 = jf.getEntry(vname);
        }
        Assert.assertEquals(ze1.getName(), vname);
        try (JarFile jf = new JarFile(multirelease, true, ZipFile.OPEN_READ, Release.VERSION_9)) {
            ze2 = jf.getEntry(rname);
        }
        Assert.assertEquals(ze2.getName(), rname);
        Assert.assertNotEquals(ze1.getName(), ze2.getName());
    }
}
