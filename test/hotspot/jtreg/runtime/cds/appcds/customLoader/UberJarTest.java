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
 *
 */
/*
 * @test
 * @summary Test handling of uber jar.
 * @requires vm.cds
 * @requires vm.cds.custom.loaders
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/C2.java
 * @run driver UberJarTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class UberJarTest {
    static String subDir = null; // set in buildUberJar
    static final String subDirName = "subDir";
    static final String embeddedJarName = "x2.jar";

    public static void main(String[] args) throws Exception {
        JarBuilder.build("x2", "C2");
        String embeddedJar = TestCommon.getTestJar(embeddedJarName);
        buildUberJar("uber_jar_test", "MainClass.mf", embeddedJarName, "C1", "SimpleHello");
        String uberJar = ClassFileInstaller.getJarPath("uber_jar_test.jar");

        // test various source format with jar protocol
        String[] classlist = new String[] {
            "java/lang/Object id: 1",
            "C1 id: 2 super: 1 source: jar:file:" + uberJar + "!/",
            "C2 id: 3 super: 1 source: jar:file:" + uberJar + "!/" + embeddedJarName + "!/",
            "SimpleHello id: 4 super: 1 source: jar:file:" + uberJar + "!/" + subDirName + "!/"
        };

        testPositive(uberJar, classlist);

        // test directory path in "source:"
        classlist[3] = "SimpleHello id: 4 super: 1 source: " + subDir;

        testPositive(uberJar, classlist);

        // test various unsupported source format
        String[] badSource = new String[] {
            "C1 id: 2 super: 1 source: file:" + uberJar,
            "C1 id: 2 super: 1 source: jar:file:" + uberJar,
            "C1 id: 2 super: 1 source: jar:file:" + uberJar + "/",
            "C1 id: 2 super: 1 source: jar:file:" + uberJar + "!/" + subDirName + "/",
            "C1 id: 2 super: 1 source: jar:file:" + uberJar + "!/C1.class/"
        };

        testNegative(uberJar, classlist, badSource);
    }

    private static void testPositive(String uberJar, String[] classlist) throws Exception {

        String addExports = "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED";

        OutputAnalyzer output;
        output = TestCommon.testDump(null, classlist, "-Xlog:class+load", "-jar", uberJar);

        output = TestCommon.exec(null, "-Xlog:class+load", addExports, "-jar", uberJar, uberJar);
        TestCommon.checkExecReturn(output, 0, true /* should contain */,
                                   "C2 source: shared objects file",
                                   "C2: here I am",
                                   "SimpleHello source: shared objects file");
    }

    private static void testNegative(String uberJar, String[] classlist, String[] badSource) throws Exception {
        for (String entry : badSource) {
            classlist[1] = entry;
            OutputAnalyzer output = TestCommon.testDump(null, classlist, "-Xlog:class+load", "-jar", uberJar);
            String source = entry.substring(entry.indexOf("source:") + 8);
            TestCommon.checkExecReturn(output, 0, true /* should contain */,
                                       "java.lang.IllegalArgumentException: unsupported source: " + source,
                                       "Preload Warning: Cannot find C1");
        }
    }

    private static void buildUberJar(String jarName, String manifest, String embeddedJar,
                                     String mainClassName, String className) throws Exception {
        String jarClassesDir = CDSTestUtils.getOutputDir() + File.separator + jarName + "_classes";
        try { Files.createDirectory(Paths.get(jarClassesDir)); } catch (FileAlreadyExistsException e) { }

        JarBuilder.compile(jarClassesDir, System.getProperty("test.src") + File.separator +
        "test-classes" + File.separator + mainClassName + ".java");

        String dirName = subDirName;
        subDir = jarClassesDir + File.separator + dirName;
        try { Files.createDirectory(Paths.get(subDir)); } catch (FileAlreadyExistsException e) { }

        JarBuilder.compile(subDir, System.getProperty("test.src") + File.separator +
        "test-classes" + File.separator + className + ".java");

        String[] testClassNames = {mainClassName, dirName + File.separator + className};

        JarBuilder.buildWithManifest(jarName, manifest, embeddedJar, jarClassesDir, testClassNames);
    }
}
