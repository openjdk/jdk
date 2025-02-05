/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used");

        testCase("Cannot use -Djava.system.class.loader");
        TestCommon.run("-cp", appJar, "-Djava.system.class.loader=dummy", "Hello")
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used when the java.system.class.loader property is specified.");

        testCase("Cannot use a different main module");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-m", "jdk.compiler/com.sun.tools.javac.Main")
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used when archived full module graph is not used.");
        testCase("Cannot use security manager");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-Djava.security.manager=allow")
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=allow.");
        TestCommon.run("-cp", appJar, "-Xlog:cds", "-Djava.security.manager=default")
            .assertAbnormalExit("CDS archive has aot-linked classes." +
                                " It cannot be used with -Djava.security.manager=default.");

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
        CDSModulePathUtils.init();

        testCase("Cannot use mis-matched module path");
        String goodModulePath = CDSModulePathUtils.getModulesDir().toString();
        TestCommon.testDump(null, CDSModulePathUtils.getAppClasses(),
                            "--module-path", goodModulePath,
                            "-XX:+AOTClassLinking",
                            "-m", CDSModulePathUtils.MAIN_MODULE);
        TestCommon.run("-Xlog:cds",
                       "--module-path", goodModulePath,
                       "-m", CDSModulePathUtils.MAIN_MODULE)
            .assertNormalExit("Using AOT-linked classes: true");

        TestCommon.run("-Xlog:cds",
                       "--module-path", goodModulePath + "/bad",
                       "-m", CDSModulePathUtils.MAIN_MODULE)
            .assertAbnormalExit("CDS archive has aot-linked classes. It cannot be used when archived full module graph is not used.");

        testCase("Cannot use mis-matched --add-modules");
        TestCommon.testDump(null, CDSModulePathUtils.getAppClasses(),
                            "--module-path", goodModulePath,
                            "-XX:+AOTClassLinking",
                            "--add-modules", CDSModulePathUtils.MAIN_MODULE);
        TestCommon.run("-Xlog:cds",
                       "--module-path", goodModulePath,
                       "--add-modules", CDSModulePathUtils.MAIN_MODULE,
                       CDSModulePathUtils.MAIN_CLASS)
            .assertNormalExit("Using AOT-linked classes: true");

        TestCommon.run("-Xlog:cds",
                       "--module-path", goodModulePath + "/bad",
                       "--add-modules", CDSModulePathUtils.TEST_MODULE,
                       CDSModulePathUtils.MAIN_CLASS)
            .assertAbnormalExit("Mismatched values for property jdk.module.addmods",
                                "CDS archive has aot-linked classes. It cannot be used when archived full module graph is not used.");
    }
}

// TODO: enhance and move this class to jdk.test.lib.cds.CDSModulePathUtils

class CDSModulePathUtils {
    private static String TEST_SRC = System.getProperty("test.root");
    private static Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());
    private static Path SRC_DIR = Paths.get(TEST_SRC, "runtime/cds/appcds/jigsaw/modulepath/src");
    private static Path MODS_DIR = Paths.get("mods");

    public static String MAIN_MODULE = "com.bars";
    public static String TEST_MODULE = "com.foos";

    public static String MAIN_CLASS = "com.bars.Main";
    public static String TEST_CLASS = "com.foos.Test";
    private static String appClasses[] = {MAIN_CLASS, TEST_CLASS};

    private static Path modulesDir;

    // This directory contains all the modular jar files
    //     $USER_DIR/modules/com.bars.jar
    //     $USER_DIR/modules/com.foos.jar
    static Path getModulesDir() {
        return modulesDir;
    }

    static String[] getAppClasses() {
        return appClasses;
    }

    static void init() throws Exception  {
        JarBuilder.compileModule(SRC_DIR.resolve(TEST_MODULE),
                                 MODS_DIR.resolve(TEST_MODULE),
                                 null);
        JarBuilder.compileModule(SRC_DIR.resolve(MAIN_MODULE),
                                 MODS_DIR.resolve(MAIN_MODULE),
                                 MODS_DIR.toString());

        String PATH_LIBS = "modules";
        modulesDir = Files.createTempDirectory(USER_DIR, PATH_LIBS);
        Path mainJar = modulesDir.resolve(MAIN_MODULE + ".jar");
        Path testJar = modulesDir.resolve(TEST_MODULE + ".jar");

        // modylibs contains both modules com.foos.jar, com.bars.jar
        // build com.foos.jar
        String classes = MODS_DIR.resolve(TEST_MODULE).toString();
        JarBuilder.createModularJar(testJar.toString(), classes, TEST_CLASS);

        // build com.bars.jar
        classes = MODS_DIR.resolve(MAIN_MODULE).toString();
        JarBuilder.createModularJar(mainJar.toString(), classes, MAIN_CLASS);
    }
}
