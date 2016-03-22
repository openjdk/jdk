/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for module resolution
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main ResolveTest
 */

import java.nio.file.*;

public class ResolveTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        ResolveTest t = new ResolveTest();
        t.runTests();
    }

    @Test
    void testMissingSimpleTypeUnnamedModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "class C { D d; }");

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C.java:1:11: compiler.err.cant.resolve.location: "
                + "kindname.class, D, , , (compiler.misc.location: kindname.class, C, null)"))
            throw new Exception("expected output not found");
    }

    @Test
    void testMissingSimpleTypeNamedModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { }",
                "class C { D d; }");

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C.java:1:11: compiler.err.cant.resolve.location: "
                + "kindname.class, D, , , (compiler.misc.location: kindname.class, C, null)"))
            throw new Exception("expected output not found");
    }

    @Test
    void testUnexportedTypeUnreadableModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { }",
                "package p2; public class C2 { p1.C1 c; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C2.java:1:33: compiler.err.not.def.access.package.cant.access: p1.C1, p1"))
            throw new Exception("expected output not found");
    }

    @Test
    void testUnexportedTypeReadableModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; }",
                "package p2; public class C2 { p1.C1 c; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C2.java:1:33: compiler.err.not.def.access.package.cant.access: p1.C1, p1"))
            throw new Exception("expected output not found");
    }

    @Test
    void testQualifiedExportedTypeReadableModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { exports p1 to m3; }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; }",
                "package p2; public class C2 { p1.C1 c; }");
        tb.writeJavaFiles(src.resolve("m3"),
                "module m3 { requires m1; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C2.java:1:33: compiler.err.not.def.access.package.cant.access: p1.C1, p1"))
            throw new Exception("expected output not found");
    }

    @Test
    void testExportedTypeUnreadableModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { exports p1; }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { }",
                "package p2; public class C2 { p1.C1 c; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("C2.java:1:33: compiler.err.not.def.access.package.cant.access: p1.C1, p1"))
            throw new Exception("expected output not found");
    }

    @Test
    void testExportedTypeReadableModule(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { exports p1; }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; }",
                "package p2; public class C2 { p1.C1 c; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    void testExportedTypeReadableModule2(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { exports p1 to m2; }",
                "package p1; public class C1 { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; }",
                "package p2; public class C2 { p1.C1 c; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }
}
