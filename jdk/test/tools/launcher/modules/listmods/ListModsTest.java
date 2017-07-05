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
 * @modules java.se
 * @build ListModsTest CompilerUtils jdk.testlibrary.*
 * @run testng ListModsTest
 * @summary Basic test for java -listmods
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import static jdk.testlibrary.ProcessTools.*;
import jdk.testlibrary.OutputAnalyzer;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Basic tests for java -listmods
 */

public class ListModsTest {

    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path UPGRADEMODS_DIR = Paths.get("upgrademods");

    @BeforeTest
    public void setup() throws Exception {
        boolean compiled;

        // javac -d mods/m1 -mp mods src/m1/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve("m1"),
                MODS_DIR.resolve("m1"));
        assertTrue(compiled);

        // javac -d upgrademods/java.transaction -mp mods src/java.transaction/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve("java.transaction"),
                UPGRADEMODS_DIR.resolve("java.transaction"));
        assertTrue(compiled);

    }


    @Test
    public void testListAll() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-listmods")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldContain("java.xml");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListOneModule() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-listmods:java.base")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldContain("exports java.lang");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListTwoModules() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-listmods:java.base,java.xml")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldContain("exports java.lang");
        output.shouldContain("java.xml");
        output.shouldContain("exports javax.xml");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListUnknownModule() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-listmods:java.rhubarb")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldNotContain("java.base");
        output.shouldNotContain("java.rhubarb");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListWithModulePath() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-mp", MODS_DIR.toString(), "-listmods")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldContain("m1");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListWithUpgradeModulePath() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-upgrademodulepath", UPGRADEMODS_DIR.toString(),
                              "-listmods:java.transaction")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("exports javax.transaction.atomic");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListWithLimitMods1() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-limitmods", "java.compact1", "-listmods")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.compact1");
        output.shouldContain("java.base");
        output.shouldNotContain("java.xml");
        assertTrue(output.getExitValue() == 0);
    }


    @Test
    public void testListWithLimitMods2() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-mp", MODS_DIR.toString(),
                              "-limitmods", "java.compact1",
                              "-listmods")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldNotContain("m1");
        assertTrue(output.getExitValue() == 0);
    }


    /**
     * java -version -listmods => should print version and exit
     */
    @Test
    public void testListWithPrintVersion1() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-version", "-listmods")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldNotContain("java.base");
        output.shouldContain("Runtime Environment");
        assertTrue(output.getExitValue() == 0);
    }


    /**
     * java -listmods -version => should list modules and exit
     */
    @Test
    public void testListWithPrintVersion2() throws Exception {
        OutputAnalyzer output
            = executeTestJava("-listmods", "-version")
                .outputTo(System.out)
                .errorTo(System.out);
        output.shouldContain("java.base");
        output.shouldNotContain("Runtime Environment");
        assertTrue(output.getExitValue() == 0);
    }

}
