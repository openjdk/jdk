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

/**
 * @test
 * @bug 8322657
 * @summary This test defines an application module using the DefineModuleApp.
 *          When performing dynamic dump, the modular jar containing the module
 *          is in the -cp. The jar listed in the "Class-Path" attribute of the modular
 *          jar doesn't exist. VM should not crash during dynamic dump under this scenario.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build jdk.test.whitebox.WhiteBox DefineModuleApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar define_module_app.jar DefineModuleApp
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. ModularJarWithNonExistentJar
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ModularJarWithNonExistentJar extends DynamicArchiveTestBase {
    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "../jigsaw/modulepath/src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path MANIFEST_PATH = Paths.get(TEST_SRC, "test-classes/manifest-with-non-existent-jar.txt");

    // the module name of the test module
    private static final String TEST_MODULE = "com.simple";

    // the module main class
    private static final String MAIN_CLASS = "com.simple.Main";

    private static Path moduleDir = null;
    private static Path modularJar = null;

    public static void buildTestModule() throws Exception {

        // javac -d mods/$TESTMODULE --module-path MOD_DIR src/$TESTMODULE/**
        JarBuilder.compileModule(SRC_DIR.resolve(TEST_MODULE),
                                 MODS_DIR.resolve(TEST_MODULE),
                                 MODS_DIR.toString());


        moduleDir = Files.createTempDirectory(USER_DIR, "mlib");

        modularJar = moduleDir.resolve(TEST_MODULE + ".jar");
        String classes = MODS_DIR.resolve(TEST_MODULE).toString();
        JarBuilder.createModularJarWithManifest(modularJar.toString(), classes,
                                                MAIN_CLASS, MANIFEST_PATH.toString());
    }

    public static void main(String... args) throws Exception {
        runTest(ModularJarWithNonExistentJar::testDefaultBase);
    }

    static void testDefaultBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTest(topArchiveName);
    }

    private static void doTest(String topArchiveName) throws Exception {
        // compile the module and create the modular jar file
        buildTestModule();
        String appJar = ClassFileInstaller.getJarPath("define_module_app.jar");
        dump(topArchiveName,
             "-Xlog:cds,class+path=info",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar + File.pathSeparator + modularJar.toString(),
             "DefineModuleApp", moduleDir.toString(), TEST_MODULE)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });
    }
}
