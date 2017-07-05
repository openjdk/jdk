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
 * @modules jdk.compiler
 * @build AddExportsTest CompilerUtils jdk.testlibrary.*
 * @run testng AddExportsTest
 * @summary Basic tests for java -XaddExports
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import static jdk.testlibrary.ProcessTools.*;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;


@Test
public class AddExportsTest {

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path UPGRADE_MODS_DIRS = Paths.get("upgrademods");

    // test module m1 that uses Unsafe
    private static final String TEST1_MODULE = "m1";
    private static final String TEST1_MAIN_CLASS = "jdk.test1.Main";

    // test module m2 uses java.transaction internals
    private static final String TEST2_MODULE = "m2";
    private static final String TEST2_MAIN_CLASS = "jdk.test2.Main";

    // test module m3 uses m4 internals
    private static final String TEST3_MODULE = "m3";
    private static final String TEST3_MAIN_CLASS = "jdk.test3.Main";
    private static final String TEST4_MODULE = "m4";


    @BeforeTest
    public void compileTestModules() throws Exception {

        // javac -d mods/m1 src/m1/**
        boolean compiled = CompilerUtils.compile(
                SRC_DIR.resolve(TEST1_MODULE),
                MODS_DIR.resolve(TEST1_MODULE),
                "-XaddExports:java.base/jdk.internal.misc=m1");
        assertTrue(compiled, "module " + TEST1_MODULE + " did not compile");

        // javac -d upgrademods/java.transaction src/java.transaction/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve("java.transaction"),
                UPGRADE_MODS_DIRS.resolve("java.transaction"));
        assertTrue(compiled, "module java.transaction did not compile");

        // javac -upgrademodulepath upgrademods -d mods/m2 src/m2/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve(TEST2_MODULE),
                MODS_DIR.resolve(TEST2_MODULE),
                "-upgrademodulepath", UPGRADE_MODS_DIRS.toString(),
                "-XaddExports:java.transaction/javax.transaction.internal=m2");
        assertTrue(compiled, "module " + TEST2_MODULE + " did not compile");

        // javac -d mods/m3 src/m3/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve(TEST3_MODULE),
                MODS_DIR.resolve(TEST3_MODULE));
        assertTrue(compiled, "module " + TEST3_MODULE + " did not compile");

        // javac -d mods/m4 src/m4/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve(TEST4_MODULE),
                MODS_DIR.resolve(TEST4_MODULE));
        assertTrue(compiled, "module " + TEST4_MODULE + " did not compile");
    }

    /**
     * Sanity check with -version
     */
    public void testSanity() throws Exception {

        int exitValue
            =  executeTestJava("-XaddExports:java.base/sun.reflect=ALL-UNNAMED",
                               "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Run class path application that uses jdk.internal.misc.Unsafe
     */
    public void testUnnamedModule() throws Exception {

        // java -XaddExports:java.base/jdk.internal.misc=ALL-UNNAMED \
        //      -cp mods/$TESTMODULE jdk.test.UsesUnsafe

        String classpath = MODS_DIR.resolve(TEST1_MODULE).toString();
        int exitValue
            = executeTestJava("-XaddExports:java.base/jdk.internal.misc=ALL-UNNAMED",
                              "-cp", classpath,
                              TEST1_MAIN_CLASS)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Run named module that uses jdk.internal.misc.Unsafe
     */
    public void testNamedModule() throws Exception {

        //  java -XaddExports:java.base/jdk.internal.misc=test \
        //       -mp mods -m $TESTMODULE/$MAIN_CLASS

        String mid = TEST1_MODULE + "/" + TEST1_MAIN_CLASS;
        int exitValue =
            executeTestJava("-XaddExports:java.base/jdk.internal.misc=" + TEST1_MODULE,
                            "-mp", MODS_DIR.toString(),
                            "-m", mid)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Test -XaddExports with upgraded module
     */
    public void testWithUpgradedModule() throws Exception {

        // java -XaddExports:java.transaction/javax.transaction.internal=m2
        //      -upgrademodulepath upgrademods -mp mods -m ...
        String mid = TEST2_MODULE + "/" + TEST2_MAIN_CLASS;
        int exitValue = executeTestJava(
                "-XaddExports:java.transaction/javax.transaction.internal=m2",
                "-upgrademodulepath", UPGRADE_MODS_DIRS.toString(),
                "-mp", MODS_DIR.toString(),
                "-m", mid)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Test -XaddExports with module that is added to the set of root modules
     * with -addmods.
     */
    public void testWithAddMods() throws Exception {

        // java -XaddExports:m4/jdk.test4=m3 -mp mods -m ...
        String mid = TEST3_MODULE + "/" + TEST3_MAIN_CLASS;
        int exitValue = executeTestJava(
                "-XaddExports:m4/jdk.test4=m3",
                "-mp", MODS_DIR.toString(),
                "-addmods", TEST4_MODULE,
                "-m", mid)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * -XaddExports can only be specified once
     */
    public void testWithDuplicateOption() throws Exception {

        int exitValue
            =  executeTestJava("-XaddExports:java.base/sun.reflect=ALL-UNNAMED",
                               "-XaddExports:java.base/sun.reflect=ALL-UNNAMED",
                               "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .shouldContain("specified more than once")
                .getExitValue();

        assertTrue(exitValue != 0);
    }


    /**
     * Exercise -XaddExports with bad values
     */
    @Test(dataProvider = "badvalues")
    public void testWithBadValue(String value, String ignore) throws Exception {

        //  -XaddExports:$VALUE -version
        int exitValue =
            executeTestJava("-XaddExports:" + value,
                            "-version")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue != 0);
    }

    @DataProvider(name = "badvalues")
    public Object[][] badValues() {
        return new Object[][]{

            { "java.base/jdk.internal.misc",            null }, // missing target
            { "java.base/jdk.internal.misc=sun.monkey", null }, // unknown target
            { "java.monkey/sun.monkey=ALL-UNNAMED",     null }, // unknown module
            { "java.base/sun.monkey=ALL-UNNAMED",       null }, // unknown package
            { "java.monkey/sun.monkey=ALL-UNNAMED",     null }, // unknown module/package
            { "java.base=ALL-UNNAMED",                  null }, // missing package
            { "java.base/=ALL-UNNAMED",                 null }  // missing package

        };
    }
}
