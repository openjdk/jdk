/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8277165
 * @library ../lib
 * @build CompilerUtils
 * @run testng MultiVersionError
 * @summary Tests multiple versions of the same class file
 */

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class MultiVersionError {
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");

    private static final Path MODS_DIR = Paths.get("mods");

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar").orElseThrow();
    private static final Set<String> modules = Set.of("m1", "m2");

    /**
     * Compiles classes used by the test
     */
    @BeforeTest
    public void compileAll() throws Exception {
        CompilerUtils.cleanDir(MODS_DIR);
        modules.forEach(mn ->
                assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, mn)));

        // create a modular multi-release m1.jar
        Path m1 = MODS_DIR.resolve("m1");
        Path m2 = MODS_DIR.resolve("m2");
        jar("cf", "m1.jar", "-C", m1.toString(), "p/Test.class",
                "--release", "9", "-C", m1.toString(), "module-info.class",
                "--release", "11", "-C", m1.toString(), "p/internal/P.class");
        jar("cf", "m2.jar", "-C", m2.toString(), "q/Q.class",
                "--release", "10", "-C", m2.toString(), "module-info.class");

        // package private p/internal/P.class in m1 instead
        jar("cf", "m3.jar", "-C", m2.toString(), "q/Q.class",
                "--release", "12", "-C", m2.toString(), "module-info.class",
                "-C", m1.toString(), "p/internal/P.class");
    }

    /*
     * multiple module-info.class from different versions should be excluded
     * from multiple version check.
     */
    @Test
    public void noMultiVersionClass() {
        // skip parsing p.internal.P to workaround JDK-8277681
        JdepsRunner jdepsRunner = new JdepsRunner("--print-module-deps", "--multi-release", "10",
                                                  "--ignore-missing-deps",
                                                  "--module-path", "m1.jar", "m2.jar");
        int rc = jdepsRunner.run(true);
        assertTrue(rc == 0);
        assertTrue(jdepsRunner.outputContains("java.base,m1"));
    }

    /*
     * Detect multiple versions of p.internal.P class
     */
    @Test
    public void classInMultiVersions() {
        JdepsRunner jdepsRunner = new JdepsRunner("--print-module-deps", "--multi-release", "13",
                                                  "--module-path", "m1.jar", "m3.jar");
        int rc = jdepsRunner.run(true);
        assertTrue(rc != 0);
        assertTrue(jdepsRunner.outputContains("class p.internal.P already associated with version"));
    }

    private static void jar(String... options) {
        int rc = JAR_TOOL.run(System.out, System.err, options);
        assertTrue(rc == 0);
    }
}
