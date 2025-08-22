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

/**
 * @test
 * @summary Identical --add-exports switches can be used across training/assembly/production
 * @bug 8352437
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver AddExports
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSJarUtils;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AddExports {
    static final String SEP = File.separator;
    static final Path SRC = Paths.get(System.getProperty("test.src")).
        resolve( ".." + SEP + "jigsaw" + SEP + "modulepath" + SEP + "src");
    static final Path nonModuleNeedsJdkAddExportDir = SRC.resolve("com.nomodule.needsjdkaddexport");
    static final String nonModuleNeedsJdkAddExportJar = "nonModuleNeedsJdkAddExport.jar";

    static String modulePath;

    private static void buildJars() throws Exception {
        // non-module needs jdk.internal.misc
        CDSJarUtils.buildFromSourceDirectory(nonModuleNeedsJdkAddExportJar,
                                             nonModuleNeedsJdkAddExportDir.toString(),
                                             "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED");

        CDSModulePackager modulePackager = new CDSModulePackager(SRC);
        modulePath = modulePackager.getOutputDir().toString();

        // module needs jdk.internal.misc
        modulePackager.createModularJar("com.needsjdkaddexport",
                                        "--add-exports", "java.base/jdk.internal.misc=com.needsjdkaddexport");

        // module needs com.foos.internal
        modulePackager.createModularJar("com.foos");
        modulePackager.createModularJar("com.needsfoosaddexport",
                                        "--add-exports", "com.foos/com.foos.internal=com.needsfoosaddexport");
    }

    static int testCount = 0;
    static void printComment(String comment) {
        testCount ++;
        System.out.println("======================================================================");
        System.out.println("TESTCASE " + testCount + ": " + comment);
        System.out.println("======================================================================");
    }

    static SimpleCDSAppTester test(String comment, SimpleCDSAppTester tester) throws Exception {
        printComment(comment);
        return tester
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Full module graph = enabled");
                    })
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("use_full_module_graph = true; java.base");
                    })
            .runStaticWorkflow()
            .runAOTWorkflow();
    }

    public static void main(String... args) throws Exception {
        buildJars();

        test("FMG should be enabled with '--add-exports java.base/jdk.internal.misc=ALL-UNNAMED'",
             SimpleCDSAppTester.of("nonModuleNeedsJdkAddExport")
                 .classpath(nonModuleNeedsJdkAddExportJar)
                 .addVmArgs("--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED", "-Xlog:aot,cds")
                 .appCommandLine("com.nomodule.needsjdkaddexport.Main"));

        test("FMG should be enabled with '--add-exports java.base/jdk.internal.misc=com.needsjdkaddexport'",
             SimpleCDSAppTester.of("moduleNeedsJdkAddExport")
                 .modulepath(modulePath)
                 .addVmArgs("--add-modules", "com.needsjdkaddexport",
                            "--add-exports", "java.base/jdk.internal.misc=com.needsjdkaddexport", "-Xlog:aot,cds")
                 .appCommandLine("-m", "com.needsjdkaddexport/com.needsjdkaddexport.Main"));

        test("FMG should be enabled with '--add-exports com.foos/com.foos.internal=com.needsfoosaddexport'",
             SimpleCDSAppTester.of("moduleNeedsFoosAddExport")
                 .modulepath(modulePath)
                 .addVmArgs("--add-modules", "com.needsfoosaddexport",
                            "--add-exports", "com.foos/com.foos.internal=com.needsfoosaddexport", "-Xlog:aot,cds")
                 .appCommandLine("-m", "com.needsfoosaddexport/com.needsfoosaddexport.Main"));

        test("FMG should be enabled with multiple --add-exports",
             SimpleCDSAppTester.of("moduleNeedsFoosAddExport")
                 .modulepath(modulePath)
                 .addVmArgs("--add-modules", "com.needsfoosaddexport",
                            "--add-exports", "com.foos/com.foos.internal=com.needsfoosaddexport",
                            "--add-exports", "com.foos/com.foos.internal=ALL-UNNAMED",
                            "--add-exports", "java.base/jdk.internal.misc=ALL-UNNAMED",
                            "--add-exports", "java.base/jdk.internal.misc=com.foos",
                            "-Xlog:aot,cds")
                 .appCommandLine("-m", "com.needsfoosaddexport/com.needsfoosaddexport.Main"));
    }
}
