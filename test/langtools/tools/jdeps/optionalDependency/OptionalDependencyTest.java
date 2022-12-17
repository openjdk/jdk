/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8293701
 * @library ../lib
 * @build CompilerUtils
 * @run testng OptionalDependencyTest
 * @summary Tests optional dependency handling
 */

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.spi.ToolProvider;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertTrue;

public class OptionalDependencyTest {
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");

    private static final Path MODS_DIR = Paths.get("mods");

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar").orElseThrow();
    private static final Set<String> modules = Set.of("m1", "m2", "m3");

    @BeforeTest
    public void compileAll() throws Exception {
        CompilerUtils.cleanDir(MODS_DIR);
        modules.forEach(mn ->
                assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, mn)));

        Path m1 = MODS_DIR.resolve("m1");
        Path m2 = MODS_DIR.resolve("m2");
        Path m3 = MODS_DIR.resolve("m3");
        jar("cf", "m1.jar", "-C", m1.toString(), "p1/P.class",
                "-C", m1.toString(), "module-info.class");
        jar("cf", "m2.jar", "-C", m2.toString(), "p2/Q.class",
                "-C", m2.toString(), "module-info.class");
        jar("cf", "m3.jar", "-C", m3.toString(), "p3/R.class",
                "-C", m3.toString(), "module-info.class");
    }

    /*
     * Test if a requires static dependence is not resolved in the configuration.
     */
    @Test
    public void optionalDependenceNotResolved() {
        JdepsRunner jdepsRunner = new JdepsRunner("--module-path", "m2.jar" + File.pathSeparator + "m3.jar",
                                                  "--inverse",
                                                  "--package", "p2", "m1.jar");
        int rc = jdepsRunner.run(true);
        assertTrue(rc == 0);
    }

    /*
     * Test if a requires static dependence is resolved in the configuration.
     */
    @Test
    public void optionalDependenceResolved() {
        JdepsRunner jdepsRunner = new JdepsRunner("--module-path", "m2.jar" + File.pathSeparator + "m3.jar",
                                                  "--inverse", "--add-modules", "m3",
                                                  "--package", "p2", "m1.jar");
        int rc = jdepsRunner.run(true);
        assertTrue(rc == 0);
    }

    private static void jar(String... options) {
        int rc = JAR_TOOL.run(System.out, System.err, options);
        assertTrue(rc == 0);
    }
}
