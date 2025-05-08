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
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AddReads
 * @summary sanity test the --add-reads option
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;
import jdk.test.whitebox.gc.GC;

public class AddReads {

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");

    // the module name of the test module
    private static final String MAIN_MODULE = "com.norequires";
    private static final String SUB_MODULE = "org.astro";

    // the module main class
    private static final String MAIN_CLASS = "com.norequires.Main";
    private static final String APP_CLASS = "org.astro.World";

    private static final String sharedClassA = 
        "[class,load] com.norequires.Main source: shared objects file";
    private static final String sharedClassB =
        "[class,load] org.astro.World source: shared objects file";
    private static final String fmgEnabled = "full module graph: enabled";
    private static final String fmgDisabled = "full module graph: disabled";
    private static final String cannotAccess =
        "class com.norequires.Main (in module com.norequires) cannot access class org.astro.World (in module org.astro)";

    private static Path moduleDir = null;
    private static Path subJar = null;
    private static Path mainJar = null;

    public static void buildTestModule() throws Exception {

        // javac -d mods/$TESTMODULE src/$TESTMODULE/**
        JarBuilder.compileModule(SRC_DIR.resolve(SUB_MODULE),
                                       MODS_DIR.resolve(SUB_MODULE),
                                       null);

        Asserts.assertTrue(CompilerUtils
            .compile(SRC_DIR.resolve(MAIN_MODULE),
                     MODS_DIR.resolve(MAIN_MODULE),
                     "-cp", MODS_DIR.resolve(SUB_MODULE).toString(),
                     "--add-reads", "com.norequires=ALL-UNNAMED"));

        moduleDir = Files.createTempDirectory(USER_DIR, "mlib");
        subJar = moduleDir.resolve(SUB_MODULE + ".jar");
        String classes = MODS_DIR.resolve(SUB_MODULE).toString();
        JarBuilder.createModularJar(subJar.toString(), classes, null);

        mainJar = moduleDir.resolve(MAIN_MODULE + ".jar");
        classes = MODS_DIR.resolve(MAIN_MODULE).toString();
        JarBuilder.createModularJar(mainJar.toString(), classes, MAIN_CLASS);
    }

    public static void main(String... args) throws Exception {
        // compile the modules and create the modular jar files
        buildTestModule();
        String appClasses[] = {MAIN_CLASS, APP_CLASS};
        String addReadsTarget[] = {SUB_MODULE, "ALL-UNNAMED"};
        final boolean useZGC = GC.Z.isSelected();
        final boolean dynamicMode = CDSTestUtils.DYNAMIC_DUMP;

        // create an archive with the classes in the modules built in the
        // previous step
        OutputAnalyzer output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path", moduleDir.toString(),
                                        "--add-modules", SUB_MODULE,
                                        "--add-reads", "com.norequires=" + SUB_MODULE,
                                        "-m", MAIN_MODULE);
        TestCommon.checkDump(output);
        String prefix[] = {"-Djava.class.path=", "-Xlog:class+load,cds,class+path=info",
                           "--add-modules", SUB_MODULE,
                           "--add-reads", "com.norequires=" + SUB_MODULE};

        // run the com.norequires module with the archive with the same args
        // used during dump time.
        // The classes should be loaded from the archive.
        TestCommon.runWithModules(prefix,
                                  null, // --upgrade-module-path
                                  moduleDir.toString(), // --module-path
                                  MAIN_MODULE) // -m
            .assertNormalExit(out -> {
                out.shouldContain(sharedClassA)
                   .shouldContain(sharedClassB);
                if (useZGC || dynamicMode) {
                   out.shouldContain(fmgDisabled);
                } else {
                   out.shouldContain(fmgEnabled);
                }
            });

        // Run without --add-reads
        String prefixNoAddReads[] = {"-Djava.class.path=", "-Xlog:class+load,cds,class+path=info",
                                     "--add-modules", SUB_MODULE};
        TestCommon.runWithModules(prefixNoAddReads,
                                  null, // --upgrade-module-path
                                  moduleDir.toString(), // --module-path
                                  MAIN_MODULE) // -m
            .assertAbnormalExit(out -> {
                if (CDSTestUtils.isAOTClassLinkingEnabled()) {
                   out.shouldContain("shared archive file has aot-linked classes. It cannot be used when archived full module graph is not used.");
                } else {
                    out.shouldContain(sharedClassA)
                       .shouldContain(sharedClassB)
                       .shouldContain(fmgDisabled)
                       .shouldContain("java.lang.IllegalAccessError")
                       .shouldContain(cannotAccess);
                    if (dynamicMode) {
                       out.shouldContain("Mismatched values for property jdk.module.addmods:")
                          .shouldContain("org.astro specified during runtime but not during dump time");
                    } else {
                       out.shouldContain("Mismatched values for property jdk.module.addreads:")
                          .shouldContain("com.norequires=org.astro specified during dump time but not during runtime");
                    }
                }
            });

        if (dynamicMode) {
            // Skip the rest of the test if running in CDS dynamic mode.
            return;
        }

        for (int i = 0; i <= 1; i++) {
            // create an archive with -cp pointing to the jar file containing the
            // org.astro module and --module-path pointing to the main module
            output = TestCommon.createArchive(
                                            subJar.toString(), appClasses,
                                            "--module-path", moduleDir.toString(),
                                            "--add-reads", "com.norequires=" + addReadsTarget[i],
                                            "-m", MAIN_MODULE);
            TestCommon.checkDump(output);
            // run the com.norequires module with the archive with the sub-module
            // in the -cp and with -add-reads=com.norequires=ALL-UNNAMED
            // Both the main class and org.astro.World should be loaded from the archive.
            // In the case where there is a mismatch in the target module in the --add-reads option
            // between dump and run time. The full module graph should be disabled.
            String prefix2[] = {"-cp", subJar.toString(), "-Xlog:class+load,cds,class+path=info",
                               "--add-reads", "com.norequires=ALL-UNNAMED"};
            final int caseNum = i;
            TestCommon.runWithModules(prefix2,
                                      null, // --upgrade-module-path
                                      moduleDir.toString(), // --module-path
                                      MAIN_MODULE) // -m
                .ifAbnormalExit(out -> {
                    if (caseNum == 0) {
                        out.shouldContain(fmgDisabled)
                           .shouldContain("shared archive file has aot-linked classes. It cannot be used when archived full module graph is not used.");
                    } else {
                        throw new RuntimeException("Expecting normal exit");
                    }
                })
                .ifNormalExit(out -> {
                    out.shouldContain(sharedClassA)
                       .shouldContain(sharedClassB);
                    if (caseNum == 0 || useZGC) {
                        out.shouldContain(fmgDisabled);
                    } else {
                        out.shouldContain(fmgEnabled);
                    }
                });
        }
    }
}
