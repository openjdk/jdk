/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test that CDS archive can be loaded if the archive is in a non-JVM variant directory.
 * @bug 8353504
 * @requires !jdk.static
 * @requires vm.cds
 * @requires vm.flagless
 * @requires vm.flavor == "server"
 * @comment This test doesn't work on Windows because it depends on symlinks
 * @requires os.family != "windows"
 * @library /test/lib appcds
 * @run driver NonJVMVariantLocation
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class NonJVMVariantLocation {
    public static void main(String[] args) throws Exception {
        String java_home_src = System.getProperty("java.home");
        String java_home_dst = CDSTestUtils.getOutputDir() + File.separator + "moved_jdk";
        String homeJava = java_home_src + File.separator + "bin" + File.separator + "java";
        String dstJava  = java_home_dst + File.separator + "bin" + File.separator + "java";

        CDSTestUtils.clone(new File(java_home_src), new File(java_home_dst));
        System.out.println("============== Cloned JDK at " + java_home_dst);

        // Replace "server" with "release" in jvm.cfg.
        // The jvm.cfg is parsed by the java launcher in java.c.
        String locDir = java_home_dst + File.separator + "lib";
        String jvmCfg = locDir + File.separator + "jvm.cfg";
        String serverDir = "server";
        String releaseDir = "release";
        replaceTextInFile(jvmCfg, serverDir, releaseDir);

        // Rename "server" dir to "release" dir.
        CDSTestUtils.rename(new File(locDir + File.separator + serverDir),
                            new File(locDir + File.separator + releaseDir));

        // Test runtime with cloned JDK
        {
            ProcessBuilder pb = CDSTestUtils.makeBuilder(dstJava,
                                                         "-Xshare:on",
                                                         "-Xlog:cds",
                                                         "-version");
            OutputAnalyzer out = TestCommon.executeAndLog(pb, "exec-dst");
            out.shouldHaveExitValue(0)
               .shouldMatch(".info..cds. Opened shared archive file.*classes.*\\.jsa");
        }
    }

    private static void replaceTextInFile(String filePath, String oldText, String newText) {
        try {
            List<String> lines = Files.readAllLines(Paths.get(filePath));
            lines.replaceAll(line -> line.replace(oldText, newText));
            Files.write(Paths.get(filePath), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
