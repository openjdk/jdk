/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8353267
 * @summary Test jlink with a module containing a class file in its META-INF directory
 * @library /test/lib
 * @modules java.base/jdk.internal.module
 *          jdk.jlink
 *          jdk.jartool
 * @run junit/othervm ClassFileInMetaInfo
 */

import java.lang.module.ModuleDescriptor;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.spi.ToolProvider;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.ModuleInfoWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;

class ClassFileInMetaInfo {
    private static PrintStream out;
    private static String moduleName;
    private static String classesDir;

    @BeforeAll
    static void setup() throws Exception {
        out = System.err; // inline with Junit

        // Create module foo containing
        //     module-info.class
        //     p/C.class
        //     META-INF/extra/q/C.class
        moduleName = "foo";
        ModuleDescriptor descriptor = ModuleDescriptor.newModule(moduleName).build();
        byte[] moduleInfo = ModuleInfoWriter.toBytes(descriptor);
        Path dir = Files.createTempDirectory(Path.of("."), moduleName);
        Files.write(dir.resolve("module-info.class"), moduleInfo);
        Files.createFile(Files.createDirectory(dir.resolve("p")).resolve("C.class"));
        Path extraClasses = dir.resolve("META-INF/extra/");
        Files.createFile(Files.createDirectories(extraClasses.resolve("q")).resolve("C.class"));
        classesDir = dir.toString();
    }

    @Test
    void testExplodedModule() throws Exception {
        test(classesDir);
    }

    @Test
    void testModularJar() throws Exception {
        String jarFile = "foo.jar";
        ToolProvider jarTool = ToolProvider.findFirst("jar").orElseThrow();
        int res = jarTool.run(out, out, "cf", jarFile, "-C", classesDir, ".");
        assertEquals(0, res);
        test(jarFile);
    }

    @Test
    void testJmod() throws Exception {
        String jmodFile = "foo.jmod";
        ToolProvider jmodTool = ToolProvider.findFirst("jmod").orElseThrow();
        int res = jmodTool.run(out, out, "create", "--class-path", classesDir, jmodFile);
        assertEquals(0, res);
        test(jmodFile);
    }

    /**
     * jlink --module-path .. --add-modules foo --ouptut image
     * image/bin/java --describe-module foo
     */
    private void test(String modulePath) throws Exception {
        Path dir = Files.createTempDirectory(Path.of("."), "image");
        Files.delete(dir);
        String image = dir.toString();

        ToolProvider jlinkTool = ToolProvider.findFirst("jlink").orElseThrow();
        int res = jlinkTool.run(out, out,
                "--module-path", modulePath,
                "--add-modules", moduleName,
                "--output", image);
        assertEquals(0, res);

        var pb = new ProcessBuilder(image + "/bin/java", "--describe-module", moduleName);
        ProcessTools.executeProcess(pb)
                .outputTo(out)
                .errorTo(out)
                .shouldHaveExitValue(0)
                .shouldContain(moduleName)
                .shouldContain("contains p")
                .shouldNotContain("META-INF");
    }
}
