/*
 * Copyright (c) 2024, Alphabet LLC. All rights reserved.
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
 * @bug 8331081 8349058
 * @summary Verify 'internal proprietary API' diagnostics if --system is configured
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api jdk.compiler/com.sun.tools.javac.file
 *     jdk.compiler/com.sun.tools.javac.jvm jdk.compiler/com.sun.tools.javac.main
 *     jdk.compiler/com.sun.tools.javac.util jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask toolbox.JavapTask toolbox.TestRunner
 * @run main SystemSunProprietary
 */
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Expect;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JrtFsClassLoaderClosed extends TestRunner {

    private final ToolBox tb = new ToolBox();

    private Path src;
    private Path classes;

    public JrtFsClassLoaderClosed() {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        new JrtFsClassLoaderClosed().runTests();
    }

    @Test
    public void testClosed(Path base) throws IOException {
        src = base.resolve("src");
        tb.writeJavaFiles(src, "module m {}");

        classes = base.resolve("classes");
        tb.createDirectories(classes);

        // Create a valid argument to system that isn't the current java.home
        Path originalSystem = Path.of(System.getProperty("java.home"));
        Path system = base.resolve("system");
        List<String> systemFiles = List.of(
                "release",
                "lib/modules",
                "lib/jrt-fs.jar");
        for (String path : systemFiles) {
            Path to = system.resolve(path);
            Files.createDirectories(to.getParent());
            Files.copy(originalSystem.resolve(path), to);
        }

        compile(system);
        for (String path : systemFiles) {
            Path to = system.resolve(path);
            Files.delete(to);
        }
    }

    private void compile(Path system) throws IOException {
        new JavacTask(tb)
                .fileManager(new JavacFileManager(new Context(), false, null))
                .options(List.of("--system", system.toString()))
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] {Paths.get(m.getName())});
    }
}
