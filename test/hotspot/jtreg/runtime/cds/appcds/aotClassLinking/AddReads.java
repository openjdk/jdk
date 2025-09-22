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
 * @bug 8354083
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver AddReads
 * @summary sanity test the --add-reads option
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Asserts;

public class AddReads {
    private static final String SEP = File.separator;

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final Path SRC_DIR = Paths.get(System.getProperty("test.src")).
        resolve( ".." + SEP + "jigsaw" + SEP + "modulepath" + SEP + "src");

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

    static class Tester extends CDSAppTester {
        public Tester(String testName) {
            super(testName);
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.TRAINING ||
                runMode == RunMode.ASSEMBLY ||
                runMode == RunMode.DUMP_STATIC) {
                return new String[] {
                    "--add-modules", SUB_MODULE,
                    "--add-reads", "com.norequires=" + SUB_MODULE,
                    "-Xlog:class+load,aot,cds,class+path=info",
                };
            } else {
                return new String[] {
                    "--add-modules", SUB_MODULE,
                    "--add-reads", "com.norequires=ALL-UNNAMED",
                    "-Xlog:class+load,aot,cds,class+path=info",
                };
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return subJar.toString();
        }


        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                "-m", MAIN_MODULE,
            };
        }

        @Override
        public String modulepath(RunMode runMode) {
            return moduleDir.toString();
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("full module graph: disabled");
                out.shouldContain("Mismatched values for property jdk.module.addreads: runtime com.norequires=ALL-UNNAMED dump time com.norequires=org.astro");
            } else if (runMode == RunMode.ASSEMBLY) {
                out.shouldContain("full module graph: enabled");
            } else {
                out.shouldHaveExitValue(0);
            }
        }
    }

    public static void main(String... args) throws Exception {
        // compile the modules and create the modular jar files
        buildTestModule();

        test("FMG should be enabled with '--add-reads com.norequires=org.astro'",
             SimpleCDSAppTester.of("add-reads")
                 .modulepath(moduleDir.toString())
                 .addVmArgs("--add-modules", SUB_MODULE,
                            "--add-reads", "com.norequires=" + SUB_MODULE,
                            "-Xlog:class+load,aot,cds,class+path=info")
                 .appCommandLine("-m", MAIN_MODULE));

        test("FMG should be enabled with '--add-reads com.norequires=org.astro'",
             SimpleCDSAppTester.of("add-reads-with-classpath")
                 .classpath(subJar.toString())
                 .modulepath(moduleDir.toString())
                 .addVmArgs("--add-modules", SUB_MODULE,
                            "--add-reads", "com.norequires=" + SUB_MODULE,
                            "-Xlog:class+load,aot,cds,class+path=info")
                 .appCommandLine("-m", MAIN_MODULE));

        printComment("FMG should be enabled with '--add-reads com.norequires=ALL-UNNAMED");
        Tester t = new Tester("add-reads-ALL-UNNAMED");
        t.setCheckExitValue(false);
        t.run("AOT");
        t.run("STATIC");
    }
}
