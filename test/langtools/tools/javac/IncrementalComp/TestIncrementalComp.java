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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.spi.ToolProvider;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestIncrementalComp {

    static final Path TEST_SRC_DIR = Path.of(System.getProperty("test.src"));
    static final Path TEST_MODULES_DIR = TEST_SRC_DIR.resolve("test_modules");
    static final Path ALTS_DIR = TEST_SRC_DIR.resolve("alts");

    static final ToolProvider JAVAC = ToolProvider.findFirst("javac")
            .orElseThrow();

    @Test
    public void testIncrementalComp() throws Throwable {
        // set up test sources
        Path localTestModules = Path.of("test_modules");
        Files.createDirectories(localTestModules);
        FileUtils.copyDirectory(TEST_MODULES_DIR, localTestModules);

        Files.copy(ALTS_DIR.resolve("Lib_int.java"),
                localTestModules.resolve("org.moda", "org", "moda", "lib", "Lib.java"));

        // compile both modules
        compile(
            "-d", "mods",
            "--module-source-path=" + localTestModules,
            "--module=org.moda,org.modb");
        Path mods = Path.of("mods");

        invokeMainMethod(mods, "org.modb", "org.modb.app.Main");

        // modify sources
        Files.copy(ALTS_DIR.resolve("Lib_long.java"),
                localTestModules.resolve("org.moda", "org", "moda", "lib", "Lib.java"),
                REPLACE_EXISTING);

        // compile only moda, modb should be compiled automatically
        compile(
            "-d", "mods",
            "--module-source-path=" + localTestModules,
            "--module=org.moda");

        // should work
        invokeMainMethod(mods, "org.modb", "org.modb.app.Main");
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
