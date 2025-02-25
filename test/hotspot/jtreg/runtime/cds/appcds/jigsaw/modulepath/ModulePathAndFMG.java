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

/**
 * @test
 * @bug 8328313
 * @requires vm.cds & !vm.graal.enabled & vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @run driver ModulePathAndFMG
 * @summary test module path changes for full module graph handling.
 *
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class ModulePathAndFMG {
    private static final String JAVA_HOME = System.getProperty("java.home");

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mody");
    private static final Path JMOD_DIR = Paths.get("jmod_dir");

    // the module name of the test module
    private static final String MAIN_MODULE = "com.bars";
    private static final String TEST_MODULE = "com.foos";
    private static final String DUP_MODULE = "com.foos3";

    // the module main class
    private static final String MAIN_CLASS = "com.bars.Main";
    private static final String TEST_CLASS = "com.foos.Test";

    private static String PATH_LIBS = "modylibs";
    private static String DUP_LIBS = "duplibs";
    private static Path libsDir = null;
    private static Path dupDir = null;
    private static Path jmodDir = null;
    private static Path mainJar = null;
    private static Path testJar = null;
    private static Path dupJar = null;
    private static Path badJar = null;

    private static String CLASS_FOUND_MESSAGE = "com.foos.Test found";
    private static String CLASS_NOT_FOUND_MESSAGE = "java.lang.ClassNotFoundException: com.foos.Test";
    private static String FIND_EXCEPTION_MESSAGE = "java.lang.module.FindException: Module com.foos not found, required by com.bars";
    private static String MODULE_NOT_RECOGNIZED = "Module format not recognized:.*modylibs.*com.bars.JAR";
    private static String OPTIMIZE_ENABLED = "] optimized module handling: enabled";
    private static String OPTIMIZE_DISABLED = "] optimized module handling: disabled";
    private static String FMG_ENABLED = "] full module graph: enabled";
    private static String FMG_DISABLED = "] full module graph: disabled";
    private static String MAIN_FROM_JAR = "class,load.*com.bars.Main.*[.]jar";
    private static String MAIN_FROM_CDS = "class,load.*com.bars.Main.*shared objects file";
    private static String MAIN_FROM_MODULE = "class,load.*com.bars.Main.*mody/com.bars";
    private static String TEST_FROM_JAR = "class,load.*com.foos.Test.*[.]jar";
    private static String TEST_FROM_CDS = "class,load.*com.foos.Test.*shared objects file";
    private static String MAP_FAILED  = "Unable to use shared archive";
    private static String PATH_SEPARATOR = File.pathSeparator;
    private static String appClasses[] = {MAIN_CLASS, TEST_CLASS};
    private static String prefix[] = {"-Djava.class.path=", "-Xlog:cds,class+load,class+path=info"};

    public static void buildTestModule() throws Exception {

        // javac -d mods/$TESTMODULE src/$TESTMODULE/**
        JarBuilder.compileModule(SRC_DIR.resolve(TEST_MODULE),
                                 MODS_DIR.resolve(TEST_MODULE),
                                 null);

        // javac -d mods/$TESTMODULE --module-path MOD_DIR src/$TESTMODULE/**
        JarBuilder.compileModule(SRC_DIR.resolve(MAIN_MODULE),
                                 MODS_DIR.resolve(MAIN_MODULE),
                                 MODS_DIR.toString());

        libsDir = Files.createTempDirectory(USER_DIR, PATH_LIBS);
        mainJar = libsDir.resolve(MAIN_MODULE + ".jar");
        testJar = libsDir.resolve(TEST_MODULE + ".jar");

        // modylibs contains both modules com.foos.jar, com.bars.jar
        // build com.foos.jar
        String classes = MODS_DIR.resolve(TEST_MODULE).toString();
        JarBuilder.createModularJar(testJar.toString(), classes, TEST_CLASS);

        // build com.bars.jar
        classes = MODS_DIR.resolve(MAIN_MODULE).toString();
        JarBuilder.createModularJar(mainJar.toString(), classes, MAIN_CLASS);

        dupDir = Files.createTempDirectory(USER_DIR, DUP_LIBS);
        dupJar = dupDir.resolve(DUP_MODULE + ".jar");
        Files.copy(testJar, dupJar, StandardCopyOption.REPLACE_EXISTING);

        badJar = libsDir.resolve(MAIN_MODULE + ".JAR");
        Files.copy(mainJar, badJar, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void buildJmod() throws Exception {
        Path jmod = Paths.get(JAVA_HOME, "bin", "jmod");
        jmodDir = Files.createDirectory(Paths.get(USER_DIR.toString() + File.separator + JMOD_DIR.toString()));
        OutputAnalyzer output = ProcessTools.executeProcess(jmod.toString(),
                       "create",
                       "--class-path", Paths.get(USER_DIR.toString(), MODS_DIR.toString(), TEST_MODULE).toString(),
                       "--module-version", "1.0",
                       "--main-class", TEST_CLASS,
                       jmodDir.toString() + File.separator + TEST_MODULE + ".jmod");
        output.shouldHaveExitValue(0);
    }

    public static void main(String... args) throws Exception {
        runWithModulePath();
        runWithExplodedModule();
        runWithJmodAndBadJar();
    }

    private static void tty(String... args) {
        for (String s : args) {
            System.out.print(s + " ");
        }
        System.out.print("\n");
    }

    public static void runWithModulePath() throws Exception {
        // compile the modules and create the modular jar files
        buildTestModule();
        // create an archive with the classes in the modules built in the
        // previous step
        OutputAnalyzer output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path",
                                        libsDir.toString(),
                                        "-m", MAIN_MODULE);
        TestCommon.checkDump(output);

        tty("1. run with CDS on, with module path same as dump time");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 libsDir.toString(), // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        tty("2. run with CDS on, with jar on path");
        TestCommon.run("-Xlog:cds",
                       "-Xlog:class+load",
                       "-cp", mainJar.toString() + PATH_SEPARATOR + testJar.toString(),
                       MAIN_CLASS)
            .assertNormalExit(out -> {
                out.shouldContain(CLASS_FOUND_MESSAGE)
                   .shouldMatch(MAIN_FROM_JAR)
                   .shouldMatch(TEST_FROM_JAR)
                   .shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldNotContain(FMG_ENABLED);
            });

        tty("3. run with CDS on, with --module-path, with jar should fail");
        TestCommon.run("-Xlog:cds",
                       "-Xlog:class+load",
                       "-p", libsDir.toString(),
                       "-cp", mainJar.toString(),
                       MAIN_CLASS)
            .assertNormalExit(out -> {
                out.shouldContain(CLASS_NOT_FOUND_MESSAGE)
                   .shouldMatch(MAIN_FROM_JAR)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED);
            });

        final String modularJarPath = mainJar.toString() + PATH_SEPARATOR + testJar.toString();

        tty("4. run with CDS on, with modular jars specified --module-path, should pass");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 modularJarPath,     // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldMatch(MAIN_FROM_CDS);       // archived Main class is for module only
            });

        final String extraModulePath = libsDir.toString() + PATH_SEPARATOR + dupDir.toString();
        // create an archive with an extra module which is not referenced
        output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path",
                                        extraModulePath,
                                        "-m", MAIN_MODULE);
        TestCommon.checkDump(output);

        tty("5. run with CDS on, without the extra module specified in dump time, should fail");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 libsDir.toString(), // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        tty("6. run with CDS on, with the extra module specified in dump time");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraModulePath,    // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        final String extraJarPath = modularJarPath + PATH_SEPARATOR + dupJar.toString();

        // create an archive by specifying modular jars in the --module-path with an extra module which is not referenced
        output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path",
                                        extraJarPath,
                                        "-m", MAIN_MODULE);
        TestCommon.checkDump(output);
        tty("7. run with CDS on, without the extra module specified in dump time, should fail");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 modularJarPath,     // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        tty("8. run with CDS on, with the extra module specified in dump time");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraJarPath,       // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });
        tty("9. same as test case 8 but with paths instead of modular jars in the --module-path");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraModulePath,    // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });
    }

    public static void runWithExplodedModule() throws Exception {
        // create an archive with an exploded module in the module path.
        OutputAnalyzer output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path",
                                        MODS_DIR.toString(),
                                        "-m", MAIN_MODULE + "/" + MAIN_CLASS);
        TestCommon.checkDump(output);

        tty("10. run with CDS on, with exploded module in the module path");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 MODS_DIR.toString(), // --module-path
                                 MAIN_MODULE + "/" + MAIN_CLASS)        // -m
            .assertNormalExit(out -> {
                out.shouldContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_MODULE) // Main class loaded from the exploded module
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });
    }

    public static void runWithJmodAndBadJar() throws Exception {
        buildJmod();

        final String modularJarPath = mainJar.toString() + PATH_SEPARATOR + testJar.toString();
        // create an archive with --module-path com.bars.jar:com.foos.jar
        OutputAnalyzer output = TestCommon.createArchive(
                                    null, appClasses,
                                    "--module-path",
                                    modularJarPath,
                                    "-m", MAIN_MODULE);
        TestCommon.checkDump(output);

        String runModulePath = mainJar.toString() + PATH_SEPARATOR +
            jmodDir.toString() + TEST_MODULE + ".jmod";
        tty("11. run with CDS on, with module path com.bars.jar:com.foos.jmod");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 runModulePath, // --module-path
                                 MAIN_MODULE)        // -m
            .assertAbnormalExit(out -> {
                out.shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldContain(FIND_EXCEPTION_MESSAGE);
            });

        runModulePath += PATH_SEPARATOR + testJar.toString();
        tty("12. run with CDS on, with module path com.bars.jar:com.foos.jmod:com.foos.jar");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 runModulePath, // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldMatch(TEST_FROM_CDS)
                   .shouldMatch(MAIN_FROM_CDS)
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        runModulePath = badJar.toString() + PATH_SEPARATOR + testJar.toString();
        tty("13. run with CDS on, with module path com.bars.JAR:com.foos.jar");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 runModulePath, // --module-path
                                 TEST_MODULE)        // -m
            .assertAbnormalExit(out -> {
                out.shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldMatch(MODULE_NOT_RECOGNIZED);
            });
    }
}
