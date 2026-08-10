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

/*
 * @test
 * @summary Disallow different module options between AOT training and assembly
 * @bug 8388525
 * @requires vm.cds
 * @requires vm.flagless
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver InvalidModuleOptions
 */

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.StringArrayUtils;

public class InvalidModuleOptions {
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String aotConfigFile = "hello.aotconfig";
    static String aotCacheFile = "hello.aot";
    static String helloClass = "Hello";

    static String[][] testCases = {
        {
            null, "--add-modules=java.instrument",
            "Mismatched values for property jdk.module.addmods: java.instrument specified for current JVM but not in AOTConfiguration"
        },
        {
            "--add-modules=java.instrument", null,
            "Mismatched values for property jdk.module.addmods: java.instrument specified in AOTConfiguration but not for current JVM"},
        {
            "--add-modules=java.instrument", "--add-modules=java.base",
            "Mismatched values for property jdk.module.addmods: AOTConfiguration = java.instrument, current = java.base"
        },
        {
            null, "-Djdk.module.showModuleResolution=true",
            "full module graph: disabled due to incompatible property: jdk.module.showModuleResolution=true",
            "AOT class linking was enabled in training run but has been disabled due to incompatible module options",
        }
    };

    public static void main(String[] args) throws Exception {
        for (int i = 0; i < testCases.length; i++) {
            String[] testSpec = testCases[i];
            String trainOpt    = testSpec[0];
            String assemblyOpt = testSpec[1];

            String[] trainCmds = new String[] {
                "-XX:AOTMode=record",
                "-XX:AOTConfiguration=" + aotConfigFile,
                "-Xlog:aot=debug",
                "-cp", appJar
            };
            if (trainOpt != null) {
                trainCmds = StringArrayUtils.concat(trainCmds, trainOpt);
            }
            trainCmds = StringArrayUtils.concat(trainCmds, helloClass);

            String[] assemblyCmds = new String[] {
                "-XX:AOTMode=create",
                "-XX:AOTConfiguration=" + aotConfigFile,
                "-XX:AOTCache=" + aotCacheFile,
                "-Xlog:cds,aot",
                "-cp", appJar
            };
            if (assemblyOpt != null) {
                assemblyCmds = StringArrayUtils.concat(assemblyCmds, assemblyOpt);
            }

            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(trainCmds);
            OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "train" + i);
            out.shouldContain("Hello World");
            out.shouldContain("AOTConfiguration recorded: " + aotConfigFile);
            out.shouldHaveExitValue(0);

            pb = ProcessTools.createLimitedTestJavaProcessBuilder(assemblyCmds);
            out = CDSTestUtils.executeAndLog(pb, "asm" + i);
            out.shouldContain("An error has occurred while writing the AOT cache");
            for (int j = 2; j < testSpec.length; j++) {
                out.shouldContain(testSpec[j]);
            }
            out.shouldNotContain("AOTCache creation is complete");
            out.shouldNotHaveExitValue(0);
        }
    }
}
