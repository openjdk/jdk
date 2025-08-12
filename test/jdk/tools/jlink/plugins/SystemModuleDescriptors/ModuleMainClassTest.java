/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.FileUtils;

import static jdk.test.lib.process.ProcessTools.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @test
 * @bug 8322809
 * @library /test/lib
 * @modules jdk.compiler jdk.jlink
 * @build jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.process.ProcessTools
 *        jdk.test.lib.util.FileUtils
 *        ModuleMainClassTest
 * @run junit ModuleMainClassTest
 */

public class ModuleMainClassTest {
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path SRC_DIR = Path.of(TEST_SRC, "src");
    private static final Path MODS_DIR = Path.of("mods");
    private static final Path JMODS_DIR = Path.of("jmods");

    private static final Path IMAGE = Path.of("image");

    // the module names are sorted by the plugin and so these names cover
    // the cases that are before and after the elements of `jdk.*` modules
    // with main classes
    private static String[] modules = new String[] {"com.foo", "net.foo"};

    private static boolean hasJmods() {
        if (!Files.exists(Paths.get(JAVA_HOME, "jmods"))) {
            System.err.println("Test skipped. NO jmods directory");
            return false;
        }
        return true;
    }

    @BeforeAll
    public static void compileAll() throws Throwable {
        if (!hasJmods()) return;

        for (String mn : modules) {
            Path msrc = SRC_DIR.resolve(mn);
            assertTrue(CompilerUtils.compile(msrc, MODS_DIR,
                    "--module-source-path", SRC_DIR.toString(),
                    "--add-exports", "java.base/jdk.internal.module=" + mn));
        }

        if (Files.exists(IMAGE)) {
            FileUtils.deleteFileTreeUnchecked(IMAGE);
        }

        // create JMOD files
        Files.createDirectories(JMODS_DIR);
        Stream.of(modules).forEach(mn ->
                assertTrue(jmod("create",
                        "--class-path", MODS_DIR.resolve(mn).toString(),
                        "--main-class", mn + ".Main",
                        JMODS_DIR.resolve(mn + ".jmod").toString()) == 0)
        );

        // the run-time image created will have 4 modules with main classes
        createImage(IMAGE, "com.foo");
    }

    @Test
    public void testComFoo() throws Exception {
        if (!hasJmods()) return;

        Path java = IMAGE.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(),
                "-m", "com.foo")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() == 0);
    }

    @Test
    public void testNetFoo() throws Exception {
        if (!hasJmods()) return;

        Path java = IMAGE.resolve("bin").resolve("java");
        assertTrue(executeProcess(java.toString(),
                "-m", "net.foo")
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue() == 0);
    }

    static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
        .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
        .orElseThrow(() -> new RuntimeException("jmod tool not found"));

    private static void createImage(Path outputDir, String... modules) throws Throwable {
        assertTrue(JLINK_TOOL.run(System.out, System.out,
                "--output", outputDir.toString(),
                "--add-modules", Arrays.stream(modules).collect(Collectors.joining(",")),
                "--module-path", JMODS_DIR.toString()) == 0);
    }

    private static int jmod(String... options) {
        System.out.println("jmod " + Arrays.asList(options));
        return JMOD_TOOL.run(System.out, System.out, options);
    }
}
