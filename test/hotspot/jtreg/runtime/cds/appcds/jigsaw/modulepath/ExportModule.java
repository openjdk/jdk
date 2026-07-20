/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver ExportModule
 * @summary Tests involve exporting a module from the module path to a jar in the -cp.
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSJarUtils;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class ExportModule {

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");

    // the module name of the test module
    private static final String TEST_MODULE1 = "com.greetings";
    private static final String TEST_MODULE2 = "org.astro";

    // unnamed module package name
    private static final String PKG_NAME = "com.nomodule";

    // A "library" class used by both MODULAR_MAIN_CLASS and NON_MODULAR_MAIN_CLASS
    private static final String LIB_CLASS = "org.astro.World";

    // the module main class
    private static final String MODULAR_MAIN_CLASS = "com.greetings.Main";

    // unnamed module main class
    private static final String NON_MODULAR_MAIN_CLASS = "com.nomodule.Main";

    private static Path moduleDir1 = null;
    private static Path moduleDir2 = null;
    private static Path modularAppJar = null; // a modular jar file that contains MODULAR_MAIN_CLASS
    private static Path nonModularAppJar = null; // a non-modular jar file that contains NON_MODULAR_MAIN_CLASS

    private static void buildJars() throws Exception {
        moduleDir2 = Paths.get("module-path2");
        CDSModulePackager modulePackager2 = new CDSModulePackager(SRC_DIR, moduleDir2);
        modulePackager2.createModularJar(TEST_MODULE2);

        // build a *modular* jar containing the MODULAR_MAIN_CLASS which
        // requires the org.astro package
        moduleDir1 = Paths.get("module-path1");
        CDSModulePackager modulePackager1 = new CDSModulePackager(SRC_DIR, moduleDir1);
        modulePackager1.addExtraModulePath("module-path2");
        modularAppJar = modulePackager1.createModularJar(TEST_MODULE1);

        // build a *non-modular* jar containing the NON_MODULAR_MAIN_CLASS which
        // requires the org.astro package
        nonModularAppJar = USER_DIR.resolve("non-modular.jar");

        CDSJarUtils.buildFromSourceDirectory(nonModularAppJar.toString(), SRC_DIR.resolve(PKG_NAME).toString(),
                                             "--module-path", moduleDir2.toString(),
                                             "--add-modules", TEST_MODULE2,
                                             "--add-exports", "org.astro/org.astro=ALL-UNNAMED");
    }

    public static void main(String... args) throws Exception {
        buildJars();

        // (1) Modular JAR

        String[] appClasses = {MODULAR_MAIN_CLASS, LIB_CLASS};
        // create an archive with the class in the org.astro module built in the
        // previous step and the main class from the modular jar in the -cp
        // note: the main class is in the modular jar in the -cp which requires
        // the dependent module, org.astro, in the --module-path
        OutputAnalyzer output = TestCommon.createArchive(
                                        modularAppJar.toString(), appClasses,
                                        "--module-path", moduleDir2.toString(),
                                        "--add-modules", TEST_MODULE2, MODULAR_MAIN_CLASS);
        TestCommon.checkDump(output);

        // run it using the archive
        // both the main class and the class from the org.astro module should
        // be loaded from the archive
        TestCommon.run("-Xlog:class+load=trace",
                              "-cp", modularAppJar.toString(),
                              "--module-path", moduleDir2.toString(),
                              "--add-modules", TEST_MODULE2, MODULAR_MAIN_CLASS)
            .assertNormalExit(
                "[class,load] org.astro.World source: shared objects file",
                "[class,load] com.greetings.Main source: shared objects file");

        // (2) Non-modular JAR

        String[] appClasses2 = {NON_MODULAR_MAIN_CLASS, LIB_CLASS};
        // create an archive with the main class from a non-modular jar in the
        // -cp and the class from the org.astro module
        // note: the org.astro package needs to be exported to "ALL-UNNAMED"
        // module since the jar in the -cp is a non-modular jar and thus it is
        // unnmaed.
        output = TestCommon.createArchive(
                                        nonModularAppJar.toString(), appClasses2,
                                        "--module-path", moduleDir2.toString(),
                                        "--add-modules", TEST_MODULE2,
                                        "--add-exports", "org.astro/org.astro=ALL-UNNAMED",
                                        NON_MODULAR_MAIN_CLASS);
        TestCommon.checkDump(output);

        // both the main class and the class from the org.astro module should
        // be loaded from the archive
        TestCommon.run("-Xlog:class+load=trace",
                       "-cp", nonModularAppJar.toString(),
                       "--module-path", moduleDir2.toString(),
                       "--add-modules", TEST_MODULE2,
                       "--add-exports", "org.astro/org.astro=ALL-UNNAMED",
                       NON_MODULAR_MAIN_CLASS)
            .assertNormalExit(
                "[class,load] org.astro.World source: shared objects file",
                "[class,load] com.nomodule.Main source: shared objects file");
    }
}
