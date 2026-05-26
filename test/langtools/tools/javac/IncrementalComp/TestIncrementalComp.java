/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test javac incremental compilation with modules
 * @library /test/lib
 * @run junit TestIncrementalComp
 */

import jdk.test.lib.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestIncrementalComp {

    static final Path TEST_SRC_DIR = Path.of(System.getProperty("test.src"));
    static final Path TEST_MODULES_DIR = TEST_SRC_DIR.resolve("test_modules");
    static final Path ALTS_DIR = TEST_SRC_DIR.resolve("alts");

    static final ToolProvider JAVAC = ToolProvider.findFirst("javac")
            .orElseThrow();

    record TestCase(String srcDir, Set<String> modules, String mainModule) {}

    @ParameterizedTest
    @MethodSource("cases")
    public void test(TestCase testCase) throws Throwable {
        Path workDir = Path.of(testCase.srcDir());
        // set up test sources
        Path localTestModules = workDir.resolve("test_modules");
        Path outDir = workDir.resolve("mods");
        Files.createDirectories(localTestModules);
        FileUtils.copyDirectory(TEST_MODULES_DIR.resolve(testCase.srcDir()), localTestModules);

        Path libPath = localTestModules.resolve("org.moda", "org", "moda", "lib", "Lib.java");
        Files.createDirectories(libPath.getParent());
        Files.copy(ALTS_DIR.resolve("Lib_int.java"), libPath);

        String modulesArg = String.join(",", testCase.modules());
        // compile both modules
        compile(
                "-d", outDir.toString(),
                "--module-source-path=" + localTestModules,
                "--module", modulesArg);

        String mainClass = testCase.mainModule() + ".app.Main";
        invokeMainMethod(outDir, testCase.mainModule(), mainClass);

        // modify sources. Dep is not modified
        Files.copy(ALTS_DIR.resolve("Lib_long.java"), libPath, REPLACE_EXISTING);

        // recompile moda. Dep should be recompiled as well
        compile(
                "-d", outDir.toString(),
                "--module-source-path=" + localTestModules,
                "--module", modulesArg);

        // should work
        invokeMainMethod(outDir, testCase.mainModule(), mainClass);
    }

    static Stream<TestCase> cases() {
        return Stream.of(
            new TestCase("single", Set.of("org.moda"), "org.moda"),
            new TestCase("multi", Set.of("org.moda", "org.modb"), "org.modb"),
            new TestCase("transitive", Set.of("org.moda", "org.modb", "org.modc"), "org.modc")
        );
    }

    private static void invokeMainMethod(Path modulePath, String moduleName, String mainClassName, String... args)
            throws ReflectiveOperationException {
        ModuleLayer boot = ModuleLayer.boot();
        ModuleLayer layer = boot.defineModulesWithOneLoader(
                boot.configuration()
                        .resolve(ModuleFinder.of(modulePath), ModuleFinder.of(), Set.of(moduleName)),
                ClassLoader.getSystemClassLoader());
        Class<?> main1 = layer.findLoader(moduleName).loadClass(mainClassName);
        Method m = main1.getMethod("main", String[].class);
        m.invoke(null, new Object[]{ args });
    }

    private static void compile(String... args) {
        System.err.println("compile: " + Arrays.asList(args));
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = JAVAC.run(pw, pw, args);
        pw.close();
        System.err.println(sw);
        assertEquals(0, rc);
    }
}
