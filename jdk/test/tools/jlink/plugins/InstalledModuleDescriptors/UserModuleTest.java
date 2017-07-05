/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Collectors;

import jdk.testlibrary.FileUtils;
import static jdk.testlibrary.ProcessTools.*;


import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @library /lib/testlibrary
 * @modules jdk.compiler
 * @build UserModuleTest CompilerUtils jdk.testlibrary.FileUtils jdk.testlibrary.ProcessTools
 * @run testng UserModuleTest
 */

public class UserModuleTest {
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path IMAGE = Paths.get("image");
    private static final Path JMODS = Paths.get(JAVA_HOME, "jmods");

    // the names of the modules in this test
    private static String[] modules = new String[] {"m1", "m2", "m3"};

    private static boolean hasJmods() {
        if (!Files.exists(JMODS)) {
            System.err.println("Test skipped. NO jmods directory");
            return false;
        }
        return true;
    }

    /*
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Throwable {
        if (!hasJmods()) return;

        for (String mn : modules) {
            Path msrc = SRC_DIR.resolve(mn);
            assertTrue(CompilerUtils.compile(msrc, MODS_DIR, "-modulesourcepath", SRC_DIR.toString()));
        }

        if (Files.exists(IMAGE)) {
            FileUtils.deleteFileTreeUnchecked(IMAGE);
        }

        createImage(IMAGE, "java.base", "m1");
    }

    private void createImage(Path outputDir, String... modules) throws Throwable {
        Path jlink = Paths.get(JAVA_HOME, "bin", "jlink");
        String mp = JMODS.toString() + File.pathSeparator + MODS_DIR.toString();
        assertTrue(executeProcess(jlink.toString(), "--output", outputDir.toString(),
                        "--addmods", Arrays.stream(modules).collect(Collectors.joining(",")),
                        "--modulepath", mp)
                        .outputTo(System.out)
                        .errorTo(System.out)
                        .getExitValue() == 0);
    }

    /*
     * Test the image created when linking with a module with
     * no ConcealedPackages attribute
     */
    @Test
    public void test() throws Throwable {
        if (!hasJmods()) return;

        Path java = IMAGE.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(), "-m", "m1/p1.Main")
                        .outputTo(System.out)
                        .errorTo(System.out)
                        .getExitValue() == 0);
    }

    /*
     * Disable the fast loading of installed modules.
     * Parsing module-info.class
     */
    @Test
    public void disableInstalledModules() throws Throwable {
        if (!hasJmods()) return;

        Path java = IMAGE.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(),
                                  "-Djdk.installed.modules.disable",
                                  "-m", "m1/p1.Main")
                        .outputTo(System.out)
                        .errorTo(System.out)
                        .getExitValue() == 0);
    }

    /*
     * Test the optimization that deduplicates Set<String> on targets of exports,
     * uses, provides.
     */
    @Test
    public void testDedupSet() throws Throwable {
        if (!hasJmods()) return;

        Path dir = Paths.get("newImage");
        createImage(dir, "java.base", "m1", "m2", "m3");
        Path java = dir.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(), "-m", "m1/p1.Main")
                        .outputTo(System.out)
                        .errorTo(System.out)
                        .getExitValue() == 0);
    }
}
