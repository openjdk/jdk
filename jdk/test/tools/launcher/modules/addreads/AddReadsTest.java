/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @build AddReadsTest CompilerUtils jdk.testlibrary.*
 * @run testng AddReadsTest
 * @summary Basic tests for java -XaddReads
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.testlibrary.OutputAnalyzer;
import static jdk.testlibrary.ProcessTools.*;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * The tests consists of two modules: m1 and junit.
 * Code in module m1 calls into code in module junit but the module-info.java
 * does not have a 'requires'. Instead a read edge is added via the command
 * line option -XaddReads.
 */

@Test
public class AddReadsTest {

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path CLASSES_DIR = Paths.get("classes");
    private static final Path MODS_DIR = Paths.get("mods");

    private static final String MAIN = "m1/p.Main";


    @BeforeTest
    public void setup() throws Exception {

        // javac -d classes src/junit/**
        assertTrue(CompilerUtils
            .compile(SRC_DIR.resolve("junit"), CLASSES_DIR));

        // javac -d mods/m1 -cp classes/ src/m1/**
        assertTrue(CompilerUtils
            .compile(SRC_DIR.resolve("m1"),
                    MODS_DIR.resolve("m1"),
                    "-cp", CLASSES_DIR.toString(),
                    "-XaddReads:m1=ALL-UNNAMED"));

        // jar cf mods/junit.jar -C classes .
        JarUtils.createJarFile(MODS_DIR.resolve("junit.jar"), CLASSES_DIR);

    }

    private OutputAnalyzer run(String... options) throws Exception {
        return executeTestJava(options)
            .outputTo(System.out)
            .errorTo(System.out);
    }


    /**
     * Run with junit as a module on the module path.
     */
    public void testJUnitOnModulePath() throws Exception {

        // java -mp mods -addmods junit -XaddReads:m1=junit -m ..
        int exitValue
            = run("-mp", MODS_DIR.toString(),
                  "-addmods", "junit",
                  "-XaddReads:m1=junit",
                  "-m", MAIN)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Exercise -XaddReads:m1=ALL-UNNAMED by running with junit on the
     * class path.
     */
    public void testJUnitOnClassPath() throws Exception {

        // java -mp mods -cp mods/junit.jar -XaddReads:m1=ALL-UNNAMED -m ..
        String cp = MODS_DIR.resolve("junit.jar").toString();
        int exitValue
            = run("-mp", MODS_DIR.toString(),
                  "-cp", cp,
                  "-XaddReads:m1=ALL-UNNAMED",
                  "-m", MAIN)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Run with junit as a module on the module path but without -XaddReads.
     */
    public void testJUnitOnModulePathMissingAddReads() throws Exception {
        // java -mp mods -addmods junit -m ..
        int exitValue
            = run("-mp", MODS_DIR.toString(),
                  "-addmods", "junit",
                  "-m", MAIN)
                .shouldContain("IllegalAccessError")
                .getExitValue();

        assertTrue(exitValue != 0);
    }


    /**
     * Run with junit on the class path but without -XaddReads.
     */
    public void testJUnitOnClassPathMissingAddReads() throws Exception {
        // java -mp mods -cp mods/junit.jar -m ..
        String cp = MODS_DIR.resolve("junit.jar").toString();
        int exitValue
            = run("-mp", MODS_DIR.toString(),
                  "-cp", cp,
                  "-m", MAIN)
                .shouldContain("IllegalAccessError")
                .getExitValue();

        assertTrue(exitValue != 0);
    }


    /**
     * Exercise -XaddReads with a more than one source module.
     */
    public void testJUnitWithMultiValueOption() throws Exception {

        int exitValue
            = run("-mp", MODS_DIR.toString(),
                  "-addmods", "java.xml,junit",
                  "-XaddReads:m1=java.xml,junit",
                  "-m", MAIN)
                .getExitValue();

        assertTrue(exitValue == 0);
    }


    /**
     * Exercise -XaddReads where the target module is specified more than once
     */
    public void testWithTargetSpecifiedManyTimes() throws Exception {

        int exitValue
            = run("-mp", MODS_DIR.toString(),
                "-addmods", "java.xml,junit",
                "-XaddReads:m1=java.xml",
                "-XaddReads:m1=junit",
                "-m", MAIN)
                .shouldContain("specified more than once")
                .getExitValue();

        assertTrue(exitValue != 0);
    }


    /**
     * Exercise -XaddReads with bad values
     */
    @Test(dataProvider = "badvalues")
    public void testWithBadValue(String value, String ignore) throws Exception {

        //  -XaddExports:$VALUE -version
        assertTrue(run("-XaddReads:" + value, "-version").getExitValue() != 0);
    }

    @DataProvider(name = "badvalues")
    public Object[][] badValues() {
        return new Object[][]{

            { "java.base",                  null }, // missing source
            { "java.monkey=java.base",      null }, // unknown module
            { "java.base=sun.monkey",       null }, // unknown source

        };
    }
}
