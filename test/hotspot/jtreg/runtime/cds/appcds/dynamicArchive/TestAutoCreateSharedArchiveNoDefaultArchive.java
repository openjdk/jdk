/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test -XX:+AutoCreateSharedArchive on a copied JDK without default shared archive
 * @bug 8261455
 * @requires vm.cds
 * @requires vm.cds.default.archive.available
 * @requires vm.flagless
 * @comment This test doesn't work on Windows because it depends on symlinks
 * @requires os.family != "windows"
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @compile ../test-classes/Hello.java
 * @run driver TestAutoCreateSharedArchiveNoDefaultArchive
 */

import java.io.File;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class TestAutoCreateSharedArchiveNoDefaultArchive {
    public static void main(String[] args) throws Exception {
        String mainClass = "Hello";
        String java_home_src = System.getProperty("java.home");
        String java_home_dst = CDSTestUtils.getOutputDir() + File.separator + "moved_jdk";
        CDSTestUtils.clone(new File(java_home_src), new File(java_home_dst));
        System.out.println("======== Cloned JDK at " + java_home_dst);

        String homeJava = java_home_src + File.separator + "bin" + File.separator + "java";
        String dstJava  = java_home_dst + File.separator + "bin" + File.separator + "java";

        TestCommon.startNewArchiveName();
        String jsaFileName = TestCommon.getCurrentArchiveName();
        File jsaFile = new File(jsaFileName);
        if (jsaFile.exists()) {
            jsaFile.delete();
        }

        String jsaOpt = "-XX:SharedArchiveFile=" + jsaFileName;
        String autoCreateArchive = "-XX:+AutoCreateSharedArchive";
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(homeJava,
                                                         "-Xshare:dump",
                                                         jsaOpt);
            TestCommon.executeAndLog(pb, "dump")
                      .shouldHaveExitValue(0);
        }
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(homeJava,
                                                         "-Xshare:auto",
                                                         jsaOpt,
                                                         "-Xlog:class+path=info",
                                                         "-version");
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-src");
            out.shouldHaveExitValue(0);
            out.shouldNotContain("shared class paths mismatch");
            out.shouldNotContain("BOOT classpath mismatch");
        }

        String helloJar = JarBuilder.getOrCreateHelloJar();

        if (jsaFile.exists()) {
            jsaFile.delete();
        }
        // Test runtime with cloned JDK
        System.out.println("======== run with cloned jdk to created dynamic shared archive at exit");
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xshare:auto",
                                                         autoCreateArchive,
                                                         jsaOpt,
                                                         "-Xlog:cds",
                                                         "-Xlog:class+path=info",
                                                         "-cp", helloJar,
                                                         mainClass);
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-dst");
            out.shouldHaveExitValue(0);
            out.shouldContain("Dumping shared data to file");
            if (!jsaFile.exists()) {
                throw new RuntimeException("Shared archive " + jsaFileName + " should be created at exit");
            }
        }

        // Remove all possible default archives
        removeDefaultArchives(java_home_dst, "zero");
        removeDefaultArchives(java_home_dst, "server");
        removeDefaultArchives(java_home_dst, "client");
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xlog:cds",
                                                         "-version");
            TestCommon.executeAndLog(pb, "show-version")
                      .shouldHaveExitValue(0)
                      .shouldContain("Loading static archive failed")
                      .shouldContain("Unable to map shared spaces")
                      .shouldNotContain("sharing");
        }
        // delete existing jsa file
        if (jsaFile.exists()) {
            jsaFile.delete();
        }
        System.out.println("======= run with no default shared archive should not create shared archive at exit");
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xshare:auto",
                                                         autoCreateArchive,
                                                         jsaOpt,
                                                         "-Xlog:cds",
                                                         "-Xlog:class+path=info",
                                                         "-cp", helloJar,
                                                         mainClass);
            TestCommon.executeAndLog(pb, "no-default-archive")
                      .shouldHaveExitValue(0)
                      .shouldContain("Loading static archive failed")
                      .shouldContain("Unable to map shared spaces")
                      .shouldNotContain("Dumping shared data to file");
            if (jsaFile.exists()) {
                throw new RuntimeException("Archive file " + jsaFileName + " should not be created at exit");
            }
        }
    }

    private static void removeDefaultArchives(String java_home_dst, String variant) {
        removeDefaultArchive(java_home_dst, variant, "");
        removeDefaultArchive(java_home_dst, variant, "_nocoops");
        removeDefaultArchive(java_home_dst, variant, "_coh");
    }

    private static void removeDefaultArchive(String java_home_dst, String variant, String suffix) {
        String fileName = java_home_dst + File.separator + "lib" + File.separator + variant +
                          File.separator +  "classes" + suffix + ".jsa";
        File f = new File(fileName);
        if (f.delete()) {
            System.out.println("======= removed " + fileName);
        }
    }
}
