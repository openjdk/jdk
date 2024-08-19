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

public class ModulePathAndFMG {

    private static final Path USER_DIR = Paths.get(CDSTestUtils.getOutputDir());

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mody");

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
    private static Path mainJar = null;
    private static Path testJar = null;
    private static Path dupJar = null;

    private static String CLASS_FOUND_MESSAGE = "com.foos.Test found";
    private static String CLASS_NOT_FOUND_MESSAGE = "java.lang.ClassNotFoundException: com.foos.Test";
    private static String OPTIMIZE_ENABLED = "] optimized module handling: enabled";
    private static String OPTIMIZE_DISABLED = "] optimized module handling: disabled";
    private static String FMG_ENABLED = "] full module graph: enabled";
    private static String FMG_DISABLED = "] full module graph: disabled";
    private static String MAIN_FROM_JAR = "class,load.*com.bars.Main.*[.]jar";
    private static String MAIN_FROM_CDS = "class,load.*com.bars.Main.*shared objects file";
    private static String TEST_FROM_JAR = "class,load.*com.foos.Test.*[.]jar";
    private static String TEST_FROM_CDS = "class,load.*com.foos.Test.*shared objects file";
    private static String MAP_FAILED  = "Unable to use shared archive";
    private static String PATH_SEPARATOR = File.pathSeparator;

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
    }

    public static void main(String... args) throws Exception {
        runWithModulePath();
    }

    private static void tty(String... args) {
        for (String s : args) {
            System.out.print(s + " ");
        }
        System.out.print("\n");
    }

    public static void runWithModulePath(String... extraRuntimeArgs) throws Exception {
        // compile the modules and create the modular jar files
        buildTestModule();
        String appClasses[] = {MAIN_CLASS, TEST_CLASS};
        // create an archive with the classes in the modules built in the
        // previous step
        OutputAnalyzer output = TestCommon.createArchive(
                                        null, appClasses,
                                        "--module-path",
                                        libsDir.toString(),
                                        "-m", MAIN_MODULE);
        TestCommon.checkDump(output);

        tty("1. run with CDS on, with module path same as dump time");
        String prefix[] = {"-Djava.class.path=", "-Xlog:cds,class+load,class+path=info"};
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

        tty("5. run with CDS on, without the extra module specified in dump time, should pass");
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
        tty("6. run with CDS on, with the extra module specified in dump time");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraModulePath,    // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldContain(FMG_DISABLED)
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
        tty("7. run with CDS on, without the extra module specified in dump time, should pass");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 modularJarPath,     // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_DISABLED)
                   .shouldContain(OPTIMIZE_ENABLED)
                   .shouldNotContain(FMG_DISABLED)
                   .shouldContain(FMG_ENABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });

        tty("8. run with CDS on, with the extra module specified in dump time");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraJarPath,       // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });
        tty("9. same as test case 8 but with paths instead of modular jars in the --module-path");
        TestCommon.runWithModules(prefix,
                                 null,               // --upgrade-module-path
                                 extraModulePath,    // --module-path
                                 MAIN_MODULE)        // -m
            .assertNormalExit(out -> {
                out.shouldNotContain(OPTIMIZE_ENABLED)
                   .shouldContain(OPTIMIZE_DISABLED)
                   .shouldNotContain(FMG_ENABLED)
                   .shouldContain(FMG_DISABLED)
                   .shouldMatch(MAIN_FROM_CDS)       // archived Main class is for module only
                   .shouldContain(CLASS_FOUND_MESSAGE);
            });
    }

}
