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

/**
 * @test
 * @summary simple tests of module uses
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ModuleBuilder ModuleTestBase
 * @run main UsesTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import toolbox.JavacTask;
import toolbox.ModuleBuilder;
import toolbox.Task;
import toolbox.ToolBox;

public class UsesTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        UsesTest t = new UsesTest();
        t.runTests();
    }

    @Test
    public void testSimple(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses p.C; }",
                "package p; public class C { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testSimpleInner(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses p.C.Inner; }",
                "package p; public class C { public class Inner { } }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testSimpleAnnotation(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses p.C; }",
                "package p; public @interface C { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb)
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testPrivateService(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses p.C.A; uses p.C; }",
                "package p; public class C { protected class A { } }");

        List<String> output = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("module-info.java:1:20: compiler.err.report.access: p.C.A, protected, p.C",
                "1 error");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testMulti(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { exports p; }",
                "package p; public class C { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; uses p.C; }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        new JavacTask(tb)
                .options("-modulesourcepath", src.toString())
                .outdir(modules)
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testMultiOnModulePath(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder(tb, "m1")
                .exports("p")
                .classes("package p; public class C { }")
                .build(modules);
        new ModuleBuilder(tb, "m2")
                .requires("m1")
                .uses("p.C")
                .write(modules);

        new JavacTask(tb)
                .options("-mp", modules.toString())
                .outdir(modules)
                .files(findJavaFiles(modules.resolve("m2")))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testMultiOnModulePathInner(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder(tb, "m1")
                .exports("p")
                .classes("package p; public class C { public class Inner { } }")
                .build(modules);
        new ModuleBuilder(tb, "m2")
                .requires("m1")
                .uses("p.C.Inner")
                .write(modules);

        new JavacTask(tb)
                .options("-mp", modules.toString())
                .outdir(modules)
                .files(findJavaFiles(modules.resolve("m2")))
                .run(Task.Expect.SUCCESS)
                .writeAll();
    }

    @Test
    public void testDuplicateUses(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m"),
                "module m { uses p.C; uses p.C; }",
                "package p; public class C { }");

        List<String> output = new JavacTask(tb)
                .options("-XDrawDiagnostics")
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        if (!output.containsAll(Arrays.asList(
                "module-info.java:1:22: compiler.err.duplicate.uses: p.C"))) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testServiceNotExist(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { uses p.NotExist; }",
                "package p; public class C { }");

        List<String> output = new JavacTask(tb)
                .outdir(Files.createDirectories(base.resolve("classes")))
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        Collection<?> expected = Arrays.asList("module-info.java:1:18: compiler.err.cant.resolve.location: kindname.class, NotExist, , , (compiler.misc.location: kindname.package, p, null)",
                "1 error");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testUsesUnexportedService(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { }",
                "package p; public class C { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; uses p.C; }");

        List<String> output = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(Files.createDirectories(base.resolve("modules")))
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("module-info.java:1:32: compiler.err.not.def.access.package.cant.access: p.C, p",
                "1 error");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testUsesUnexportedButProvidedService(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"),
                "module m1 { provides p.C with p.C; }",
                "package p; public class C { }");
        tb.writeJavaFiles(src.resolve("m2"),
                "module m2 { requires m1; uses p.C; }");

        List<String> output = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-modulesourcepath", src.toString())
                .outdir(Files.createDirectories(base.resolve("modules")))
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList("module-info.java:1:32: compiler.err.not.def.access.package.cant.access: p.C, p",
                "1 error");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }
}
