/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for output directory
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main OutputDirTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OutputDirTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        new OutputDirTest().run();
    }

    Path src;

    void run() throws Exception {
        tb = new ToolBox();

        src = Paths.get("src");
        tb.writeJavaFiles(src.resolve("m"),
                "module m { }",
                "package p; class C { }");

        runTests();
    }

    @Test
    void testError(Path base) throws Exception {
        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("- compiler.err.no.output.dir"))
            throw new Exception("expected output not found");
    }

    @Test
    void testProcOnly(Path base) throws IOException {
        tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-proc:only",
                        "-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    void testClassOutDir(Path base) throws IOException {
        Path classes = base.resolve("classes");
        tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-d", classes.toString(),
                        "-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    void testExplodedOutDir(Path base) throws Exception {
        Path modSrc = base.resolve("modSrc");
        tb.writeJavaFiles(modSrc,
                "module m1 { exports p; }",
                "package p; public class CC { }");
        Path modClasses = base.resolve("modClasses");
        Files.createDirectories(modClasses);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .outdir(modClasses)
                .files(findJavaFiles(modSrc))
                .run()
                .writeAll();

        Path src = base.resolve("src");
        Path src_m = src.resolve("m");
        tb.writeJavaFiles(src_m,
                "module m { requires m1 ; }",
                "class C { }");

        String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .outdir(modClasses) // an exploded module
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("- compiler.err.multi-module.outdir.cannot.be.exploded.module: " + modClasses.toString()))
            throw new Exception("expected output not found");
    }

    @Test
    void testInExplodedOutDir(Path base) throws Exception {
        Path modSrc = base.resolve("modSrc");
        tb.writeJavaFiles(modSrc,
                "module m1 { exports p; }",
                "package p; public class CC { }");
        Path modClasses = base.resolve("modClasses");
        Files.createDirectories(modClasses);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .outdir(modClasses)
                .files(findJavaFiles(modSrc))
                .run()
                .writeAll();

        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires m1 ; }",
                "class C { }");

        Path classes = modClasses.resolve("m");
        Files.createDirectories(classes);

        String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .outdir(classes) // within an exploded module
                .options("-XDrawDiagnostics",
                        "-Xlint", "-Werror",
                        "-modulepath", modClasses.toString())
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("- compiler.warn.outdir.is.in.exploded.module: " + classes.toString()))
            throw new Exception("expected output not found");
    }
}
