/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib
 * @modules jdk.compiler
 *          java.scripting
 *          jdk.zipfs
 * @build PatchTest
 *        jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.util.JarUtils
 *        jdk.test.lib.process.ProcessTools
 * @run testng PatchTest
 * @bug 8259395
 * @summary Runs tests that make use of automatic modules
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;
import static jdk.test.lib.process.ProcessTools.*;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class PatchTest {

    private static final String MODULE_NAME = "somelib";
    private static final String PATCH_NAME = "somelibTest";
    private static final String MAIN_CLASS = "somelib.test.TestMain";

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SOMELIB_SRC = Paths.get(TEST_SRC, MODULE_NAME);
    private static final Path SOMELIB_TEST_SRC = Paths.get(TEST_SRC, PATCH_NAME);
    private static final Path SOMELIB_CLASSES = Paths.get("classes", MODULE_NAME);
    private static final Path SOMELIB_TEST_CLASSES = Paths.get("classes", PATCH_NAME);
    private static final Path SOMELIB_JAR = Paths.get("mods", MODULE_NAME + "-0.19.jar");

    /**
     * Test using --patch-module with main class in a new package in the patch
     *
     * The consists of 1 module:
     *
     * somelib - dummy automatic module.
     *
     * And one patch:
     *
     * somelibTest - contains the test logic to test somelib
     *
     * The test patches somelib with somelibTest.
     */

    public void testPatchModule() throws Exception {
        boolean compiled;

        // create mods/somelib-0.19.jar

        compiled = CompilerUtils.compile(SOMELIB_SRC, SOMELIB_CLASSES);
        assertTrue(compiled);

        JarUtils.createJarFile(SOMELIB_JAR, SOMELIB_CLASSES);


        // compile patch

        compiled = CompilerUtils.compile(SOMELIB_TEST_SRC, SOMELIB_TEST_CLASSES,
                        "--module-path", SOMELIB_JAR.toString(),
                        "--add-modules", MODULE_NAME,
                        "--patch-module", MODULE_NAME + "=" + SOMELIB_TEST_CLASSES);

        assertTrue(compiled);


        // launch the test

        int exitValue
            = executeTestJava("--module-path", SOMELIB_JAR.toString(),
                              "--patch-module", MODULE_NAME + "=" + SOMELIB_TEST_CLASSES,
                              "-m", MODULE_NAME + "/" + MAIN_CLASS)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);

    }

}
