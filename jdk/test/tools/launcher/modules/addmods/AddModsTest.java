/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 */

/**
 * @test
 * @library /lib/testlibrary
 * @modules jdk.jlink/jdk.tools.jmod
 *          jdk.compiler
 * @build AddModsTest CompilerUtils jdk.testlibrary.*
 * @run testng AddModsTest
 * @summary Basic test for java -addmods
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import static jdk.testlibrary.ProcessTools.*;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


@Test
public class AddModsTest {

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS1_DIR = Paths.get("mods1");
    private static final Path MODS2_DIR = Paths.get("mods2");

    // test module / main class
    private static final String TEST_MODULE = "test";
    private static final String TEST_MAIN_CLASS = "test.Main";
    private static final String TEST_MID = TEST_MODULE + "/" + TEST_MAIN_CLASS;

    // logger module
    private static final String LOGGER_MODULE = "logger";


    @BeforeTest
    public void compile() throws Exception {
        // javac -d mods1/test src/test/**
        boolean compiled = CompilerUtils.compile(
            SRC_DIR.resolve(TEST_MODULE),
            MODS1_DIR.resolve(TEST_MODULE)
        );
        assertTrue(compiled, "test did not compile");

        // javac -d mods1/logger src/logger/**
        compiled= CompilerUtils.compile(
            SRC_DIR.resolve(LOGGER_MODULE),
            MODS2_DIR.resolve(LOGGER_MODULE)
        );
        assertTrue(compiled, "test did not compile");
    }


    /**
     * Basic test of -addmods ALL-DEFAULT. Module java.sql should be
     * resolved and the types in that module should be visible.
     */
    public void testAddDefaultModules1() throws Exception {

        // java -addmods ALL-DEFAULT -mp mods1 -m test ...
        int exitValue
            = executeTestJava("-mp", MODS1_DIR.toString(),
                              "-addmods", "ALL-DEFAULT",
                              "-m", TEST_MID,
                              "java.sql.Connection")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

    /**
     * Basic test of -addmods ALL-DEFAULT. Module java.annotations.common
     * should not resolved and so the types in that module should not be
     * visible.
     */
    public void testAddDefaultModules2() throws Exception {

        // java -addmods ALL-DEFAULT -mp mods1 -m test ...
        int exitValue
            = executeTestJava("-mp", MODS1_DIR.toString(),
                              "-addmods", "ALL-DEFAULT",
                              "-m", TEST_MID,
                              "javax.annotation.Generated")
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldContain("ClassNotFoundException")
                .getExitValue();

        assertTrue(exitValue != 0);
    }

    /**
     * Basic test of -addmods ALL-SYSTEM. All system modules should be resolved
     * and thus all types in those modules should be visible.
     */
    public void testAddSystemModules() throws Exception {

        // java -addmods ALL-SYSTEM -mp mods1 -m test ...
        int exitValue
            = executeTestJava("-mp", MODS1_DIR.toString(),
                              "-addmods", "ALL-SYSTEM",
                              "-m", TEST_MID,
                              "java.sql.Connection",
                              "javax.annotation.Generated")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Run test on class path to load a type in a module on the application
     * module path, uses {@code -addmods logger}.
     */
    public void testRunWithAddMods() throws Exception {

        // java -mp mods -addmods logger -cp classes test.Main
        String classpath = MODS1_DIR.resolve(TEST_MODULE).toString();
        String modulepath = MODS2_DIR.toString();
        int exitValue
            = executeTestJava("-mp", modulepath,
                              "-addmods", LOGGER_MODULE,
                              "-cp", classpath,
                              TEST_MAIN_CLASS,
                              "logger.Logger")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

     /**
      * Run application on class path that makes use of module on the
      * application module path. Does not use -addmods and so should
      * fail at run-time.
      */
     public void testRunMissingAddMods() throws Exception {

         // java -mp mods -cp classes test.Main
         String classpath = MODS1_DIR.resolve(TEST_MODULE).toString();
         String modulepath = MODS1_DIR.toString();
         int exitValue
             = executeTestJava("-mp", modulepath,
                               "-cp", classpath,
                               TEST_MAIN_CLASS,
                               "logger.Logger")
                 .outputTo(System.out)
                 .errorTo(System.out)
                 .shouldContain("ClassNotFoundException")
                 .getExitValue();

         assertTrue(exitValue != 0);
     }


    /**
     * Run test on class path to load a type in a module on the application
     * module path, uses {@code -addmods ALL-MODULE-PATH}.
     */
    public void testAddAllModulePath() throws Exception {

        // java -mp mods -addmods ALL-MODULE-PATH -cp classes test.Main
        String classpath = MODS1_DIR.resolve(TEST_MODULE).toString();
        String modulepath = MODS1_DIR.toString();
        int exitValue
            = executeTestJava("-mp", modulepath,
                              "-addmods", "ALL-MODULE-PATH",
                              "-cp", classpath,
                              TEST_MAIN_CLASS)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Test {@code -addmods ALL-MODULE-PATH} without {@code -modulepath}.
     */
    public void testAddAllModulePathWithNoModulePath() throws Exception {

        // java -addmods ALL-MODULE-PATH -version
        int exitValue
            = executeTestJava("-addmods", "ALL-MODULE-PATH",
                              "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Attempt to run with a bad module name specified to -addmods
     */
    public void testRunWithBadAddMods() throws Exception {

        // java -mp mods -addmods DoesNotExist -m test ...
        int exitValue
            = executeTestJava("-mp", MODS1_DIR.toString(),
                              "-addmods", "DoesNotExist",
                              "-m", TEST_MID)
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldContain("DoesNotExist")
                .getExitValue();

        assertTrue(exitValue != 0);
    }

}
