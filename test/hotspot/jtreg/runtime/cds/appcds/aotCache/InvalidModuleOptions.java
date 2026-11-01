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

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.StringArrayUtils;

public class InvalidModuleOptions {
    static final Path modulesSrc = Paths.get(System.getProperty("test.src")).resolve("modules");
    static final Path modulesSrc2 = Paths.get(System.getProperty("test.src")).resolve("modules2");
    static final Path modulesSrc3 = Paths.get(System.getProperty("test.src")).resolve("modules3");
    static String appJar = ClassFileInstaller.getJarPath("hello.jar");
    static String aotConfigFile = "hello.aotconfig";
    static String aotCacheFile = "hello.aot";
    static String helloClass = "Hello";
    static String modulePath1;
    static String modulePath2;
    static String modulePath3;

    static String[][] testCases = {
        // --module-path
        {
            "<mp1>", null,
            "module path has fewer elements (0) than expected (1)",
        },
        {
            "<mp1>", "<mp2>",
            "module path at [2] is different",
        },
        {
            "<mp12>", "<mp13>",
            "module path at [3] is different",
        },
        {
            null, "<mp1>",
            "module path has more elements (1) than expected (0)",
        },

        // --add-modules
        {
            null, "--add-modules=java.instrument",
            "Mismatched values for property jdk.module.addmods: java.instrument specified for current JVM but not in AOTConfiguration"
        },
        {
            "--add-modules=java.instrument", null,
            "Mismatched values for property jdk.module.addmods: java.instrument specified in AOTConfiguration but not for current JVM"},
        {
            "--add-modules=java.instrument", "--add-modules=java.base",
            "Mismatched values for property jdk.module.addmods: current = java.base, AOTConfiguration = java.instrument"
        },

        // --enable-native-access
        // Just test one variation, as HotSpot handles this the same way as --add-modules.
        {
            null, "--enable-native-access=ALL_UNNAMED",
            "Mismatched values for property jdk.module.enable.native.access: ALL_UNNAMED specified for current JVM but not in AOTConfiguration"
        },

        // -Djdk.module.showModuleResolution=true also disabled full module graph:
        {
            null, "-Djdk.module.showModuleResolution=true",
            "full module graph: disabled due to incompatible property: jdk.module.showModuleResolution=true",
            "AOT class linking was enabled in training run but has been disabled due to incompatible module options",
        },
    };

    public static void main(String[] args) throws Exception {
        CDSModulePackager modulePackager1 = new CDSModulePackager(modulesSrc, Paths.get("test-modules1"));
        modulePackager1.createModularJar("com.test");
        modulePath1 = modulePackager1.getOutputDir().toString();

        CDSModulePackager modulePackager2 = new CDSModulePackager(modulesSrc2, Paths.get("test-modules2"));
        modulePackager2.createModularJar("com.moretest");
        modulePath2 = modulePackager2.getOutputDir().toString();

        CDSModulePackager modulePackager3 = new CDSModulePackager(modulesSrc3, Paths.get("test-modules3"));
        modulePackager3.createModularJar("com.evenmoretest");
        modulePath3 = modulePackager3.getOutputDir().toString();

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
                trainCmds = concat(trainCmds, trainOpt);
            }
            trainCmds = StringArrayUtils.concat(trainCmds, helloClass);

            String[] assemblyCmds = new String[] {
                "-XX:AOTMode=create",
                "-XX:AOTConfiguration=" + aotConfigFile,
                "-XX:AOTCache=" + aotCacheFile,
                "-Xlog:cds,aot,class+path",
                "-cp", appJar
            };
            if (assemblyOpt != null) {
                assemblyCmds = concat(assemblyCmds, assemblyOpt);
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

    static String[] concat(String[] opts, String extra) {
        if (extra.equals("<mp1>")) {
            return StringArrayUtils.concat(opts, "--module-path", modulePath1);
        } else if (extra.equals("<mp2>")) {
            return StringArrayUtils.concat(opts, "--module-path", modulePath2);
        } else if (extra.equals("<mp12>")) {
            return StringArrayUtils.concat(opts, "--module-path", modulePath1 + File.pathSeparator + modulePath2);
        } else if (extra.equals("<mp13>")) {
            return StringArrayUtils.concat(opts, "--module-path", modulePath1 + File.pathSeparator + modulePath3);
        } else {
            return StringArrayUtils.concat(opts, extra);
        }
    }
}
