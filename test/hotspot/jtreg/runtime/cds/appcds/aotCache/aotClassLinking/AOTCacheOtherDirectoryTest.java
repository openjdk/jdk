/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @summary Use AOT cache from different directory between training and production
 * @bug 8376576
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build AOTCacheOtherDirectoryTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCacheOtherDirectoryApp
 * @run driver AOTCacheOtherDirectoryTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSAppTester.RunMode;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AOTCacheOtherDirectoryTest {
    static final String jarName = "app.jar";
    static final String appJar = ClassFileInstaller.getJarPath(jarName);
    static final String targetDirName = "target";
    static final String mainClass = "AOTCacheOtherDirectoryApp";
    static final String aotCacheName = "otherDirectoryCache";

    public static void main(String[] args) throws Exception {
        // Move the jar file to directory test_root/target
        Path srcPath = Paths.get(appJar);
        Path destDir = Files.createDirectory(Paths.get(targetDirName));
        Path destPath = destDir.resolve(jarName);
        Files.move(srcPath, destPath, REPLACE_EXISTING);

        // Dump archive in root directory first
        SimpleCDSAppTester.of(targetDirName + File.separator + aotCacheName)
            .addVmArgs("-Xlog:aot,cds", "-XX:-AOTClassLinking")
            .classpath(destPath.toString())
            .appCommandLine(mainClass)
            .runAOTTrainingAndAssemblyWorkflow();

        // At runtime, run from /target so the classpath is different but use the same JAR
        String[] cmdLine = new String[] { "-XX:-AOTClassLinking", "-XX:AOTMode=on",
                                          "-XX:AOTCache=" + aotCacheName + ".aot",
                                          "-Xlog:cds,aot=trace,aot+map+oops=trace:file=production.map:none:filesize=0",
                                          "-cp", jarName, mainClass};
        ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(cmdLine);
        pb.directory(destDir.toFile());

        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb.start(), RunMode.PRODUCTION.toString());
        output.shouldHaveExitValue(0);
    }
}

class AOTCacheOtherDirectoryApp {
    public static void main(String[] args) {
        String path = AOTCacheOtherDirectoryApp.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        System.out.println(path);
    }
}
