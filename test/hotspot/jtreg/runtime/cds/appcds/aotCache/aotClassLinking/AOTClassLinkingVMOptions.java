/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @summary Disable CDS when incompatible options related to AOTClassLinking are used
 * @library /test/jdk/lib/testlibrary
 *          /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar Hello
 * @run driver AOTClassLinkingVMOptions
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTClassLinkingVMOptions {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "Hello";

    static int testCaseNum = 0;
    static void testCase(String s) {
        testCaseNum++;
        System.out.println("Test case " + testCaseNum + ": " + s);
    }

    public static void main(String[] args) throws Exception {
        SimpleCDSAppTester t = SimpleCDSAppTester.of("AOTClassLinking")
            .classpath(appJar)
            .addVmArgs("-XX:+AOTClassLinking")
            .appCommandLine(mainClass)
            .runAOTTrainingAndAssemblyWorkflow();

        testCase("Archived full module graph must be enabled at runtime");
        t.setVmArgs("-Djdk.module.validation=1")
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");
            })
            .productionRun();

        testCase("Cannot use -Djava.system.class.loader");
        t.setVmArgs("-Djava.system.class.loader=dummy")
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                    " It cannot be used when the java.system.class.loader property is specified.");
            })
            .productionRun();

        testCase("Cannot use a different main module");
        t.setVmArgs()
            .appCommandLine("-m", "jdk.compiler/com.sun.tools.javac.Main")
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used.");
            })
            .productionRun();

        testCase("Cannot use security manager");
        t.setVmArgs("-Djava.security.manager=allow")
            .appCommandLine(mainClass)
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=allow.");
            })
            .productionRun();

        t.setVmArgs("-Djava.security.manager=default")
            .appCommandLine(mainClass)
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=default.");
            })
            .productionRun();

        // Dumping with AOTInvokeDynamicLinking disabled
        t.setVmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+AOTClassLinking", "-XX:-AOTInvokeDynamicLinking")
            .appCommandLine(mainClass)
            .setCheckExitValue(true)
            .runAOTTrainingAndAssemblyWorkflow();

        testCase("Use the archive that was created with -XX:-AOTInvokeDynamicLinking.");
        t.setVmArgs()
            .appCommandLine(mainClass)
            .setCheckExitValue(true)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(0);
                out.shouldMatch("Hello");
            })
            .productionRun();

        testCase("Archived full module graph must be enabled at runtime (with -XX:-AOTInvokeDynamicLinking)");
        t.setVmArgs("-Djdk.module.validation=1")
            .appCommandLine(mainClass)
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldMatch("AOT cache has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");
            })
            .productionRun();

        // NOTE: tests for ClassFileLoadHook + AOTClassLinking is in
        // ../jvmti/ClassFileLoadHookTest.java

        modulePathTests();
    }

    static void modulePathTests() throws Exception {
        String TEST_ROOT = System.getProperty("test.root");
        Path SRC_DIR = Paths.get(TEST_ROOT, "runtime/cds/appcds/jigsaw/modulepath/src");

        String MAIN_MODULE = "com.foos";
        String MAIN_CLASS = "com.foos.Test";

        CDSModulePackager modulePackager = new CDSModulePackager(SRC_DIR);
        modulePackager.createModularJarWithMainClass(MAIN_MODULE, MAIN_CLASS);

        String modulePath = modulePackager.getOutputDir().toString();

        testCase("Cannot use mis-matched module path");
        SimpleCDSAppTester t = SimpleCDSAppTester.of("ModuleTests")
            .modulepath(modulePath)
            .addVmArgs("-XX:+AOTClassLinking")
            .appCommandLine("-m", MAIN_MODULE)
            .runAOTTrainingAndAssemblyWorkflow();

        t.setVmArgs("-Xlog:aot", "-Xlog:cds")
            .modulepath(modulePath)
            .appCommandLine("-m", MAIN_MODULE)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldContain("Using AOT-linked classes: true");
            })
            .productionRun();

        t.setVmArgs("-Xlog:aot=debug", "-Xlog:cds")
            .modulepath(modulePath + "/bad")
            .appCommandLine( "-m", MAIN_MODULE)
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldContain("shared class paths mismatch (hint: enable -Xlog:class+path=info to diagnose the failure)");
            })
            .productionRun();

        testCase("Cannot use mis-matched --add-modules");
        t.setVmArgs("-XX:+AOTClassLinking", "--add-modules", MAIN_MODULE)
            .modulepath(modulePath)
            .appCommandLine(MAIN_CLASS)
            .setCheckExitValue(true)
            .runAOTTrainingAndAssemblyWorkflow();

        t.setVmArgs("-Xlog:aot", "-Xlog:cds", "--add-modules", MAIN_MODULE)
            .modulepath(modulePath)
            .appCommandLine(MAIN_CLASS)
            .setCheckExitValue(true)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldContain("Using AOT-linked classes: true");
            })
            .productionRun();

        t.setVmArgs("-Xlog:cds", "--add-modules", "java.base")
            .modulepath(modulePath)
            .appCommandLine(MAIN_CLASS)
            .setCheckExitValue(false)
            .setProductionChecker((OutputAnalyzer out) -> {
                out.shouldHaveExitValue(1);
                out.shouldContain("Mismatched values for property jdk.module.addmods");
                out.shouldContain("AOT cache has aot-linked classes. It cannot be used when archived full module graph is not used.");
            })
            .productionRun();
    }
}
