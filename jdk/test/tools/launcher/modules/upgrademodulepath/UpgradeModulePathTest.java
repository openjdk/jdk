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
 * @build UpgradeModulePathTest CompilerUtils jdk.testlibrary.*
 * @run testng UpgradeModulePathTest
 * @summary Basic test for java -upgrademodulepath
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

import static jdk.testlibrary.ProcessTools.executeTestJava;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * This test upgrades module java.transaction. The upgraded module has a
 * dependency on module java.enterprise that is deployed on the application
 * modue path.
 */


@Test
public class UpgradeModulePathTest {

    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path UPGRADEDMODS_DIR = Paths.get("upgradedmods");


    @BeforeTest
    public void setup() throws Exception {

        // javac -d mods/java.enterprise src/java.enterprise/**
        boolean compiled = CompilerUtils.compile(
                SRC_DIR.resolve("java.enterprise"),
                MODS_DIR.resolve("java.enterprise"));
        assertTrue(compiled);

        // javac -d upgrademods/java.transaction -mp mods src/java.transaction/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve("java.transaction"),
                UPGRADEDMODS_DIR.resolve("java.transaction"),
                "-mp", MODS_DIR.toString());
        assertTrue(compiled);

        // javac -d mods -upgrademodulepath upgrademods -mp mods src/test/**
        compiled = CompilerUtils.compile(
                SRC_DIR.resolve("test"),
                MODS_DIR.resolve("test"),
                "-upgrademodulepath", UPGRADEDMODS_DIR.toString(),
                "-mp", MODS_DIR.toString());
        assertTrue(compiled);

    }


    /**
     * Run the test with an upgraded java.transaction module.
     */
    public void testWithUpgradedModule() throws Exception {

        String mid = "test/jdk.test.Main";

        int exitValue
            = executeTestJava(
                "-upgrademodulepath", UPGRADEDMODS_DIR.toString(),
                "-mp", MODS_DIR.toString(),
                "-m", mid)
            .outputTo(System.out)
            .errorTo(System.out)
            .getExitValue();

        assertTrue(exitValue == 0);

    }


    /**
     * Run the test with a non-existent file on the upgrade module path.
     * It should be silently ignored.
     */
    public void testRunWithNonExistentEntry() throws Exception {

        String upgrademodulepath
            = "DoesNotExit" + File.pathSeparator + UPGRADEDMODS_DIR.toString();
        String mid = "test/jdk.test.Main";

        int exitValue
            = executeTestJava(
                "-upgrademodulepath", upgrademodulepath,
                "-mp", MODS_DIR.toString(),
                "-m", mid)
            .outputTo(System.out)
            .errorTo(System.out)
            .getExitValue();

        assertTrue(exitValue == 0);

    }

}
