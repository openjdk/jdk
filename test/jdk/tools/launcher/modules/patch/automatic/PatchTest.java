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
 * @build PatchTest
 *        jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.util.JarUtils
 *        jdk.test.lib.process.ProcessTools
 * @run testng PatchTest
 * @bug 8259395
 * @summary Runs tests that make use of automatic modules
 */

import java.io.File;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;
import static jdk.test.lib.process.ProcessTools.*;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeClass;
import static org.testng.Assert.*;

public class PatchTest {

    private static final String APP_NAME = "myapp";

    private static final String MODULE_NAME = "somelib";

    private static final String PATCH1_NAME = "patch1";
    private static final String PATCH2_NAME = "patch2";

    private static final String APP_MAIN = "myapp.Main";
    private static final String PATCH1_MAIN = "somelib.test.TestMain";
    private static final String PATCH2_MAIN = "somelib.Dummy";

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path APP_SRC = Path.of(TEST_SRC, APP_NAME);
    private static final Path APP_CLASSES = Path.of("classes", APP_NAME);
    private static final Path SOMELIB_SRC = Path.of(TEST_SRC, MODULE_NAME);
    private static final Path SOMELIB_PATCH1_SRC = Path.of(TEST_SRC, PATCH1_NAME);
    private static final Path SOMELIB_PATCH2_SRC = Path.of(TEST_SRC, PATCH2_NAME);
    private static final Path SOMELIB_CLASSES = Path.of("classes", MODULE_NAME);
    private static final Path SOMELIB_PATCH1_CLASSES = Path.of("classes", PATCH1_NAME);
    private static final Path SOMELIB_PATCH2_CLASSES = Path.of("classes", PATCH2_NAME);
    private static final Path SOMELIB_JAR = Path.of("mods", MODULE_NAME + "-0.19.jar");

    private static final String MODULE_PATH = String.join(File.pathSeparator, SOMELIB_JAR.toString(), APP_CLASSES.toString());

    /**
     * The test consists of 2 modules:
     *
     * somelib - dummy automatic module.
     * myapp - explicit module, uses somelib
     *
     * And two patches:
     *
     * patch1 - adds an additional package.
     * patch2 - only replaces existing classes.
     *
     */
    @BeforeClass
    public void compile() throws Exception {
        boolean compiled;

        // create mods/somelib-0.19.jar

        compiled = CompilerUtils.compile(SOMELIB_SRC, SOMELIB_CLASSES);
        assertTrue(compiled);

        JarUtils.createJarFile(SOMELIB_JAR, SOMELIB_CLASSES);


        // compile patch 1
        compiled = CompilerUtils.compile(SOMELIB_PATCH1_SRC, SOMELIB_PATCH1_CLASSES,
                        "--module-path", SOMELIB_JAR.toString(),
                        "--add-modules", MODULE_NAME,
                        "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH1_SRC);
        assertTrue(compiled);

        // compile patch 2
        compiled = CompilerUtils.compile(SOMELIB_PATCH2_SRC, SOMELIB_PATCH2_CLASSES,
                        "--module-path", SOMELIB_JAR.toString(),
                        "--add-modules", MODULE_NAME,
                        "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH2_SRC);
        assertTrue(compiled);

        // compile app
        compiled = CompilerUtils.compile(APP_SRC, APP_CLASSES,
                        "--module-path", SOMELIB_JAR.toString());
        assertTrue(compiled);
    }

    @Test
    public void modulePathExtend() throws Exception {
        int exitValue
            = executeTestJava("--module-path", MODULE_PATH,
                              "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH1_CLASSES,
                              "-m", APP_NAME + "/" + APP_MAIN, "patch1")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

    @Test
    public void modulePathAugment() throws Exception {
        int exitValue
            = executeTestJava("--module-path", MODULE_PATH,
                              "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH2_CLASSES,
                              "-m", APP_NAME + "/" + APP_MAIN, "patch2")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

    @Test
    public void rootModuleExtend() throws Exception {
        int exitValue
            = executeTestJava("--module-path", SOMELIB_JAR.toString(),
                              "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH1_CLASSES,
                              "-m", MODULE_NAME + "/" + PATCH1_MAIN)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

    @Test
    public void rootModuleAugment() throws Exception {
        int exitValue
            = executeTestJava("--module-path", SOMELIB_JAR.toString(),
                              "--patch-module", MODULE_NAME + "=" + SOMELIB_PATCH2_CLASSES,
                              "-m", MODULE_NAME + "/" + PATCH2_MAIN)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();

        assertTrue(exitValue == 0);
    }

}
