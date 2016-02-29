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
 * @summary Test that URL connections to multi-release jars can be runtime versioned
 * @library /lib/testlibrary/java/util/jar
 * @build Compiler JarBuilder CreateMultiReleaseTestJars
 * @run testng MultiReleaseJarURLConnection
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.jar.JarFile;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

public class MultiReleaseJarURLConnection {
    String userdir = System.getProperty("user.dir",".");
    String urlFile = "jar:file:" + userdir + "/multi-release.jar!/";
    String urlEntry = urlFile + "version/Version.java";

    @BeforeClass
    public void initialize() throws Exception {
        CreateMultiReleaseTestJars creator = new CreateMultiReleaseTestJars();
        creator.compileEntries();
        creator.buildMultiReleaseJar();
    }

    @AfterClass
    public void close() throws IOException {
        Files.delete(Paths.get(userdir, "multi-release.jar"));
    }

    @Test
    public void testRuntimeVersioning() throws Exception {
        Assert.assertTrue(readAndCompare(new URL(urlEntry), "return 8"));
        // #runtime is "magic"
        Assert.assertTrue(readAndCompare(new URL(urlEntry + "#runtime"), "return 9"));
        // #fragment or any other fragment is not magic
        Assert.assertTrue(readAndCompare(new URL(urlEntry + "#fragment"), "return 8"));
        // cached entities not affected
        Assert.assertTrue(readAndCompare(new URL(urlEntry), "return 8"));
    }

    @Test
    public void testCachedJars() throws Exception {
        URL rootUrl = new URL(urlFile);
        JarURLConnection juc = (JarURLConnection)rootUrl.openConnection();
        JarFile rootJar = juc.getJarFile();
        JarFile.Release root = rootJar.getVersion();

        URL runtimeUrl = new URL(urlFile + "#runtime");
        juc = (JarURLConnection)runtimeUrl.openConnection();
        JarFile runtimeJar = juc.getJarFile();
        JarFile.Release runtime = runtimeJar.getVersion();
        Assert.assertNotEquals(root, runtime);

        juc = (JarURLConnection)rootUrl.openConnection();
        JarFile jar = juc.getJarFile();
        Assert.assertEquals(jar.getVersion(), root);
        Assert.assertEquals(jar, rootJar);

        juc = (JarURLConnection)runtimeUrl.openConnection();
        jar = juc.getJarFile();
        Assert.assertEquals(jar.getVersion(), runtime);
        Assert.assertEquals(jar, runtimeJar);

        rootJar.close();
        runtimeJar.close();
        jar.close(); // probably not needed
    }

    private boolean readAndCompare(URL url, String match) throws Exception {
        boolean result;
        // necessary to do it this way, instead of openStream(), so we can
        // close underlying JarFile, otherwise windows can't delete the file
        URLConnection conn = url.openConnection();
        try (InputStream is = conn.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            result = (new String(bytes)).contains(match);
        }
        if (conn instanceof JarURLConnection) {
            ((JarURLConnection)conn).getJarFile().close();
        }
        return result;
    }
}
