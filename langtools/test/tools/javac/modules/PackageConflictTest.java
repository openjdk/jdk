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
 * @summary test module/package conflicts
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main PackageConflictTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public class PackageConflictTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        PackageConflictTest t = new PackageConflictTest();
        t.runTests();
    }

    @Test
    void testSimple(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package java.util; public class MyList { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("MyList.java:1:1: compiler.err.package.in.other.module: java.base"))
            throw new Exception("expected output not found");
    }

    @Test
    void testDisjoint(Path base) throws Exception {
        Path m1 = base.resolve("m1");
        Path m2 = base.resolve("m2");
        tb.writeJavaFiles(m1,
                          "module m1 { }",
                          "package test; public class A { }");
        tb.writeJavaFiles(m2,
                          "module m2 { }",
                          "package test; public class B { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        tb.new JavacTask()
          .options("-Werror", "-modulesourcepath", base.toString())
          .outdir(classes)
          .files(findJavaFiles(base))
          .run()
          .writeAll();
    }

    @Test
    void testConflictInDependencies(Path base) throws Exception {
        Path m1 = base.resolve("m1");
        Path m2 = base.resolve("m2");
        Path m3 = base.resolve("m3");
        tb.writeJavaFiles(m1,
                          "module m1 { exports test; }",
                          "package test; public class A { }");
        tb.writeJavaFiles(m2,
                          "module m2 { exports test; }",
                          "package test; public class B { }");
        tb.writeJavaFiles(m3,
                          "module m3 { requires m1; requires m2; }",
                          "package impl; public class Impl { }");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        List<String> log = tb.new JavacTask()
                       .options("-XDrawDiagnostics", "-modulesourcepath", base.toString())
                       .outdir(classes)
                       .files(findJavaFiles(base))
                       .run(ToolBox.Expect.FAIL)
                       .writeAll()
                       .getOutputLines(ToolBox.OutputKind.DIRECT);

        List<String> expected =
                Arrays.asList("module-info.java:1:1: compiler.err.package.clash.from.requires: m3, test, m1, m2",
                              "1 error");

        if (!expected.equals(log)) {
            throw new AssertionError("Unexpected output: " + log);
        }
    }

    @Test
    void testSimple2(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("N")
                .exports("pack")
                .classes("package pack; public class A { }")
                .build(modules);
        new ModuleBuilder("M")
                .requires("N")
                .classes("package pack; public class B { pack.A f; }")
                .write(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-mp", modules.toString())
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules.resolve("M")))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("B.java:1:1: compiler.err.package.in.other.module: N"))
            throw new Exception("expected output not found");
    }

    @Test
    void testPrivateConflict(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("N")
                .exports("publ")
                .classes("package pack; public class A { }")
                .classes("package publ; public class B { }")
                .write(modules);
        new ModuleBuilder("M")
                .requires("N")
                .classes("package pack; public class C { publ.B b; }")
                .write(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-modulesourcepath", modules + "/*/src")
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains(""))
            throw new Exception("unexpected output not found");

    }

    @Test
    void testPrivateConflictOnModulePath(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("N")
                .exports("publ")
                .classes("package pack; public class A { }")
                .classes("package publ; public class B { }")
                .build(modules);
        new ModuleBuilder("M")
                .requires("N")
                .classes("package pack; public class C { publ.B b; }")
                .write(modules);

        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-mp", modules.toString())
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules.resolve("M")))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains(""))
            throw new Exception("expected output not found");

    }

    @Test
    void testRequiresConflictExports(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("M")
                .exports("pack")
                .classes("package pack; public class A { }")
                .build(modules);
        new ModuleBuilder("N")
                .exports("pack")
                .classes("package pack; public class B { }")
                .build(modules);
        new ModuleBuilder("K")
                .requires("M")
                .requires("N")
                .classes("package pkg; public class C { pack.A a; pack.B b; }")
                .write(modules);

        List<String> log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-mp", modules.toString())
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules.resolve("K")))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutputLines(ToolBox.OutputKind.DIRECT);

        List<String> expected =
                Arrays.asList("module-info.java:1:1: compiler.err.package.clash.from.requires: K, pack, M, N",
                        "1 error");
        if (!log.containsAll(expected))
            throw new Exception("expected output not found");
    }

    @Test
    void testQulifiedExportsToDifferentModules(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("U").write(modules);
        new ModuleBuilder("M")
                .exports("pkg to U")
                .classes("package pkg; public class A { public static boolean flagM; }")
                .write(modules);
        new ModuleBuilder("N")
                .exports("pkg to K")
                .classes("package pkg; public class A { public static boolean flagN; }")
                .write(modules);
        ModuleBuilder moduleK = new ModuleBuilder("K");
        moduleK.requires("M")
                .requires("N")
                .classes("package p; public class DependsOnN { boolean f = pkg.A.flagN; } ")
                .write(modules);
        tb.new JavacTask()
                .options("-modulesourcepath", modules + "/*/src")
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules.resolve("K")))
                .run(ToolBox.Expect.SUCCESS)
                .writeAll();

        //negative case
        moduleK.classes("package pkg; public class DuplicatePackage { } ")
                .classes("package p; public class DependsOnM { boolean f = pkg.A.flagM; } ")
                .write(modules);

        List<String> output = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", modules + "/*/src")
                .outdir(Files.createDirectories(base.resolve("classes")))
                .files(findJavaFiles(modules.resolve("K")))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutputLines(ToolBox.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "DependsOnM.java:1:55: compiler.err.cant.resolve.location: kindname.variable, flagM, , , (compiler.misc.location: kindname.class, pkg.A, null)");
        if (!output.containsAll(expected)) {
            throw new Exception("expected output not found");
        }
    }
}
