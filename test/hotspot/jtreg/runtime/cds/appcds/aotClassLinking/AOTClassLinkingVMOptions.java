/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class AOTClassLinkingVMOptions {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");

    static int testCaseNum = 0;
    static void testCase(String s) {
        testCaseNum++;
        System.out.println("Test case " + testCaseNum + ": " + s);
    }

    public static void main(String[] args) throws Exception {
        TestCommon.testDump(appJar, TestCommon.list("Hello"),
                            "-XX:+AOTClassLinking");

        testCase("Archived full module graph must be enabled at runtime");
        TestCommon.run("-cp", appJar, "-Djdk.module.validation=1", "Hello")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");

        testCase("Cannot use -Djava.system.class.loader");
        TestCommon.run("-cp", appJar, "-Djava.system.class.loader=dummy", "Hello")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used when the java.system.class.loader property is specified.");

        testCase("Cannot use a different main module");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-m", "jdk.compiler/com.sun.tools.javac.Main")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used.");
        testCase("Cannot use security manager");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-Djava.security.manager=allow")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=allow.");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-Djava.security.manager=default")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=default.");

        // Dumping with AOTInvokeDynamicLinking disabled
        TestCommon.testDump(appJar, TestCommon.list("Hello"),
                            "-XX:+UnlockDiagnosticVMOptions", "-XX:+AOTClassLinking", "-XX:-AOTInvokeDynamicLinking");

        testCase("Use the archive that was created with -XX:-AOTInvokeDynamicLinking.");
        TestCommon.run("-cp", appJar, "Hello")
            .assertNormalExit("Hello");

        testCase("Archived full module graph must be enabled at runtime (with -XX:-AOTInvokeDynamicLinking)");
        TestCommon.run("-cp", appJar, "-Djdk.module.validation=1", "Hello")
            .assertAbnormalExit("shared archive file has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");

        // NOTE: tests for ClassFileLoadHook + AOTClassLinking is in
        // ../jvmti/ClassFileLoadHookTest.java

        boolean dynamicMode = Boolean.getBoolean("test.dynamic.cds.archive");
        if (!dynamicMode) {
            // These tests need to dump the full module graph, which is not possible with
            // dynamic dump.
            modulePathTests();
        }
    }

    static void modulePathTests() throws Exception {
        String TEST_ROOT = System.getProperty("test.root");
        Path SRC_DIR = Paths.get(TEST_ROOT, "runtime/cds/appcds/jigsaw/modulepath/src");

        String MAIN_MODULE = "com.foos";
        String MAIN_CLASS = "com.foos.Test";

        String[] appClasses = {MAIN_CLASS};

        CDSModulePackager modulePackager = new CDSModulePackager(SRC_DIR);
        modulePackager.createModularJarWithMainClass(MAIN_MODULE, MAIN_CLASS);

        String modulePath = modulePackager.getOutputDir().toString();

        testCase("Cannot use mis-matched module path");
        TestCommon.testDump(null, appClasses,
                            "--module-path", modulePath,
                            "-XX:+AOTClassLinking",
                            "-m", MAIN_MODULE);
        TestCommon.run("-Xlog:aot",
                       "-Xlog:cds",
                       "--module-path", modulePath,
                       "-m", MAIN_MODULE)
            .assertNormalExit("Using AOT-linked classes: true");

        TestCommon.run("-Xlog:aot",
                       "-Xlog:cds",
                       "--module-path", modulePath + "/bad",
                       "-m", MAIN_MODULE)
            .assertAbnormalExit("shared archive file has aot-linked classes. It cannot be used when archived full module graph is not used.");

        testCase("Cannot use mis-matched --add-modules");
        TestCommon.testDump(null, appClasses,
                            "--module-path", modulePath,
                            "-XX:+AOTClassLinking",
                            "--add-modules", MAIN_MODULE);
        TestCommon.run("-Xlog:aot",
                       "-Xlog:cds",
                       "--module-path", modulePath,
                       "--add-modules", MAIN_MODULE,
                       MAIN_CLASS)
            .assertNormalExit("Using AOT-linked classes: true");

        TestCommon.run("-Xlog:cds",
                       "--module-path", modulePath,
                       "--add-modules", "java.base",
                       MAIN_CLASS)
            .assertAbnormalExit("Mismatched values for property jdk.module.addmods",
                                "shared archive file has aot-linked classes. It cannot be used when archived full module graph is not used.");
    }
}
