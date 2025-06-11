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
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver AddOpens
 * @summary sanity test the --add-opens option
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AddOpens {
    private static final String SEP = File.separator;

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final Path SRC_DIR = Paths.get(System.getProperty("test.src")).
        resolve( ".." + SEP + "jigsaw" + SEP + "modulepath" + SEP + "src");

    private static final Path MODS_DIR = Paths.get("mods");

    // the module name of the test module
    private static final String TEST_MODULE1 = "com.simple";

    // the module main class
    private static final String MAIN_CLASS = "com.simple.Main";

    private static Path moduleDir = null;
    private static Path moduleDir2 = null;
    private static Path destJar = null;

    private static String addOpensArg = "java.base/java.lang=" + TEST_MODULE1;
    private static String addOpensAllUnnamed = "java.base/java.lang=ALL-UNNAMED";
    private static String extraOpts[][] =
        {{"-Xlog:cds", "-Xlog:cds"},
         {"--add-opens", addOpensArg}};
    private static String expectedOutput[] =
        { "[class,load] com.simple.Main source: shared objects file",
          "method.setAccessible succeeded!"};

    public static void buildTestModule() throws Exception {

        // javac -d mods/$TESTMODULE --module-path MOD_DIR src/$TESTMODULE/**
        JarBuilder.compileModule(SRC_DIR.resolve(TEST_MODULE1),
                                 MODS_DIR.resolve(TEST_MODULE1),
                                 MODS_DIR.toString());

        moduleDir = Files.createTempDirectory(USER_DIR, "mlib");
        moduleDir2 = Files.createTempDirectory(USER_DIR, "mlib2");

        Path srcJar = moduleDir.resolve(TEST_MODULE1 + ".jar");
        destJar = moduleDir2.resolve(TEST_MODULE1 + ".jar");
        String classes = MODS_DIR.resolve(TEST_MODULE1).toString();
        JarBuilder.createModularJar(srcJar.toString(), classes, MAIN_CLASS);
        Files.copy(srcJar, destJar);

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
                    out.shouldContain(expectedOutput[0]);
                    out.shouldContain(expectedOutput[1]);
                    })
            .runStaticWorkflow()
            .runAOTWorkflow();
    }

    static class Tester1 extends CDSAppTester {
        public Tester1(String testName) {
            super(testName);
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.DUMP_STATIC) {
                return new String[] { "-Xlog:cds" };
            } else {
                return new String[] {
                    "--add-opens", addOpensArg, "-Xlog:class+load=trace",
                };
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return destJar.toString();
        }

        @Override
        public String modulepath(RunMode runMode) {
            return moduleDir.toString();
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            if (runMode == RunMode.TRAINING ||
                runMode == RunMode.ASSEMBLY ||
                runMode == RunMode.DUMP_STATIC) {
                return new String[] {
                    "-m", TEST_MODULE1,
                };
            } else {
                return new String[] {
                    "-m", TEST_MODULE1, "with_add_opens",
                };
            }
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain(expectedOutput[0]);
                out.shouldContain(expectedOutput[1]);
            } else if (runMode == RunMode.ASSEMBLY) {
                out.shouldContain("full module graph: enabled");
            }
        }
    }

    static class Tester2 extends CDSAppTester {
        public Tester2(String testName) {
            super(testName);
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "--add-opens", addOpensAllUnnamed, "-Xlog:class+load=trace",
            };
        }

        @Override
        public String classpath(RunMode runMode) {
            return destJar.toString();
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            if (runMode == RunMode.TRAINING ||
                runMode == RunMode.ASSEMBLY ||
                runMode == RunMode.DUMP_STATIC) {
                return new String[] {
                    MAIN_CLASS,
                };
            } else {
                return new String[] {
                    MAIN_CLASS, "with_add_opens",
                };
            }
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain(expectedOutput[0]);
                out.shouldContain(expectedOutput[1]);
            } else if (runMode == RunMode.ASSEMBLY) {
                out.shouldContain("full module graph: enabled");
            }
        }
    }

    public static void main(String... args) throws Exception {
        // compile the modules and create the modular jar files
        buildTestModule();
        String appClasses[] = {MAIN_CLASS};
        OutputAnalyzer output;

        test("Same --add-opens during ASSEMBLY/DUMP_STATIC and PRODUCTION RunMode",
            SimpleCDSAppTester.of("same-add-opens")
                .classpath(destJar.toString())
                .addVmArgs("--add-opens", addOpensArg, "-Xlog:class+load=trace")
                .modulepath(moduleDir.toString())
                .appCommandLine("-m", TEST_MODULE1, "with_add_opens"));

        printComment("no --add-opens during DUMP_STATIC RunMode; --add-opens during PRODUCTION RunMode");
        Tester1 t1 = new Tester1("no-add-opens-in-DUMP_STATIC");
        t1.run("AOT");
        t1.run("STATIC");

        printComment("--add-opens ALL-UNNAMED during ASSEMBLY/DUMP_STATIC and PRODUCTION RunMode");
        Tester2 t2 = new Tester2("add-opens-ALL-UNNAMED");
        t2.run("AOT");
        t2.run("STATIC");

    }
}
