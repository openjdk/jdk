/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that CDS still works when the JDK is moved to a new directory
 * @bug 8272345
 * @requires vm.cds
 * @requires vm.flagless
 * @comment This test doesn't work on Windows because it depends on symlinks
 * @requires os.family != "windows"
 * @library /test/lib
 * @compile test-classes/Hello.java
 * @run driver MoveJDKTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class MoveJDKTest {
    public static void main(String[] args) throws Exception {
        String java_home_src = System.getProperty("java.home");
        String java_home_dst = CDSTestUtils.getOutputDir() + File.separator + "moved_jdk";
        String homeJava = java_home_src + File.separator + "bin" + File.separator + "java";
        String dstJava  = java_home_dst + File.separator + "bin" + File.separator + "java";

        TestCommon.startNewArchiveName();
        String jsaFile = TestCommon.getCurrentArchiveName();
        String jsaOpt = "-XX:SharedArchiveFile=" + jsaFile;
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(homeJava, "-Xshare:dump", jsaOpt);
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

        CDSTestUtils.clone(new File(java_home_src), new File(java_home_dst));
        System.out.println("============== Cloned JDK at " + java_home_dst);

        // Test runtime with cloned JDK
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xshare:auto",
                                                         jsaOpt,
                                                         "-Xlog:class+path=info",
                                                         "-version");
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-dst");
            out.shouldHaveExitValue(0);
            out.shouldNotContain("shared class paths mismatch");
            out.shouldNotContain("BOOT classpath mismatch");
        }

        // Test with bad JAR file name, hello.modules
        String helloJar = JarBuilder.getOrCreateHelloJar();
        String fake_modules = copyFakeModulesFromHelloJar();
        String dumptimeBootAppendOpt = "-Xbootclasspath/a:" + fake_modules;
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(homeJava,
                                                         "-Xshare:dump",
                                                         dumptimeBootAppendOpt,
                                                         jsaOpt);
            TestCommon.executeAndLog(pb, "dump")
                      .shouldHaveExitValue(0);
        }
        {
            String runtimeBootAppendOpt = dumptimeBootAppendOpt + System.getProperty("path.separator") + helloJar;
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xshare:auto",
                                                         runtimeBootAppendOpt,
                                                         jsaOpt,
                                                         "-Xlog:class+path=info",
                                                         "-version");
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-dst");
            out.shouldHaveExitValue(0);
            out.shouldNotContain("shared class paths mismatch");
            out.shouldNotContain("BOOT classpath mismatch");
        }

        // Test with no modules image in the <java home>/lib directory
        String locDir = java_home_dst + File.separator + "lib";
        CDSTestUtils.rename(new File(locDir + File.separator + "modules"),
                            new File(locDir + File.separator + "orig-modules"));
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava, "-version");
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-missing-modules");
            out.shouldHaveExitValue(1);
            out.shouldContain("Failed setting boot class path.");
        }
    }

    private static String copyFakeModulesFromHelloJar() throws Exception {
        String outDir = CDSTestUtils.getOutputDir();
        String newFile = "hello.modules";
        String path = outDir + File.separator + newFile;

        Files.copy(Paths.get(outDir, "hello.jar"),
            Paths.get(outDir, newFile),
            StandardCopyOption.REPLACE_EXISTING);
        return path;
    }
}
