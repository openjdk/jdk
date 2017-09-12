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

/*
 * @test
 * @summary Tests jdeps --generate-open-module option
 * @library ../lib
 * @build CompilerUtils JdepsUtil JdepsRunner
 * @modules jdk.jdeps/com.sun.tools.jdeps
 * @run testng GenOpenModule
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.stream.Stream;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GenOpenModule extends GenModuleInfo {
    private static final String MODULE_INFO = "module-info.class";

    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path LIBS_DIR = Paths.get("libs");
    private static final Path DEST_DIR = Paths.get("moduleinfosrc");
    private static final Path NEW_MODS_DIR = Paths.get("new_mods");

    /**
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Exception {

        compileModules(MODS_DIR);

        createJARFiles(MODS_DIR, LIBS_DIR);
    }

    @Test
    public void test() throws IOException {
        Stream<String> files = MODULES.stream()
                .map(mn -> LIBS_DIR.resolve(mn + ".jar"))
                .map(Path::toString);

        Stream<String> options = Stream.concat(
            Stream.of("--generate-open-module", DEST_DIR.toString()), files);
        JdepsRunner.run(options.toArray(String[]::new));

        // check file exists
        MODULES.stream()
             .map(mn -> DEST_DIR.resolve(mn).resolve("module-info.java"))
             .forEach(f -> assertTrue(Files.exists(f)));

        // copy classes to a temporary directory
        // and then compile new module-info.java
        copyClasses(MODS_DIR, NEW_MODS_DIR);
        compileNewGenModuleInfo(DEST_DIR, NEW_MODS_DIR);

        for (String mn : MODULES) {
            Path p1 = NEW_MODS_DIR.resolve(mn).resolve(MODULE_INFO);
            Path p2 = MODS_DIR.resolve(mn).resolve(MODULE_INFO);

            try (InputStream in1 = Files.newInputStream(p1);
                 InputStream in2 = Files.newInputStream(p2)) {
                verify(ModuleDescriptor.read(in1),
                       ModuleDescriptor.read(in2));
            }
        }
    }

    /*
     * Verify the dependences
     */
    private void verify(ModuleDescriptor openModule, ModuleDescriptor md) {
        System.out.println("verifying: " + openModule.name());
        assertTrue(openModule.isOpen());
        assertTrue(!md.isOpen());
        assertEquals(openModule.name(), md.name());
        assertEquals(openModule.requires(), md.requires());
        assertTrue(openModule.exports().isEmpty());
        assertEquals(openModule.provides(), md.provides());
    }
}
