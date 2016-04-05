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
 * @summary tests for -upgrademodulepath
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main UpgradeModulePathTest
 */

import java.io.File;
import java.nio.file.Path;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class UpgradeModulePathTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        UpgradeModulePathTest t = new UpgradeModulePathTest();
        t.runTests();
    }

    @Test
    void simpleUsage(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        final Path upgradeModule = base.resolve("upgradeModule");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class E { }")
                .build(upgradeModule);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.E.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulepath", module.toString(),
                        "-upgrademodulepath", upgradeModule.toString())
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    void onlyUpgradeModulePath(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        final Path upgradeModule = base.resolve("upgradeModule");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class E { }")
                .build(upgradeModule);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.E.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-upgrademodulepath", upgradeModule + File.pathSeparator + module)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    void withModuleSourcePath(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        final Path upgradeModule = base.resolve("upgradeModule");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class E { }")
                .build(upgradeModule);

        final Path s = base.resolve("source");
        tb.writeJavaFiles(s.resolve("m3"), "module m3 { }");

        final Path upgradeModule3 = base.resolve("upgradeModule");
        new ModuleBuilder("m3")
                .exports("pkg3")
                .classes("package pkg3; public class E { }")
                .build(upgradeModule);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m2"), "module m2 { requires m1; requires m3; }",
                "package p; class A { void main() { pkg2.E.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulepath", module.toString(),
                        "-modulesourcepath", src + File.pathSeparator + s,
                        "-upgrademodulepath", upgradeModule + File.pathSeparator + upgradeModule3)
                .outdir(module)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    void sameUpgradeAndModulePath(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        final Path upgradeModule = base.resolve("upgradeModule");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class E { }")
                .build(upgradeModule);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.E.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulepath", upgradeModule + File.pathSeparator + module,
                        "-upgrademodulepath", upgradeModule.toString())
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    @Test
    void dummyFileInUpgradeModulePath(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        Path dummy = base.resolve("dummy.txt");
        tb.writeFile(dummy, "");

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.E.class.getName(); } }");

        String output = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulepath", module.toString(),
                        "-upgrademodulepath", dummy.toString())
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);
        if (!output.contains("compiler.err.illegal.argument.for.option: -upgrademodulepath, " + dummy)) {
            throw new Exception("Expected output was not found");
        }
    }

    @Test
    void severalUpgradeModules(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class A { }")
                .build(module);

        new ModuleBuilder("m2")
                .exports("pkg2")
                .classes("package pkg2; public class B { }")
                .build(module);

        Path upgradeModule = base.resolve("upgradeModule");
        new ModuleBuilder("m2")
                .exports("pkg2")
                .classes("package pkg2; public class BC { }")
                .build(upgradeModule);
        new ModuleBuilder("m3")
                .exports("pkg3")
                .classes("package pkg3; public class DC { }")
                .build(upgradeModule);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m4 { requires m1; requires m2; requires m3; }",
                "package p; class A { void main() { pkg1.A.class.getName(); pkg2.BC.class.getName(); pkg3.DC.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulepath", module.toString(),
                        "-upgrademodulepath", upgradeModule.toString())
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        Path src2 = base.resolve("src2");
        tb.writeJavaFiles(src2, "module m4 { requires m1; }",
                "package p; class A { void main() { pkg2.B.class.getName(); } }");

        String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulepath", module.toString(),
                        "-upgrademodulepath", upgradeModule.toString())
                .files(findJavaFiles(src2))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);
        if (!log.contains("compiler.err.doesnt.exist: pkg2")) {
            throw new Exception("Expected output was not found");
        }
    }

    @Test
    void severalUpgradeModulePathsLastWin(Path base) throws Exception {
        final Path module = base.resolve("modules");
        new ModuleBuilder("m1")
                .exports("pkg1")
                .classes("package pkg1; public class E { }")
                .build(module);

        final Path upgradeModule1 = base.resolve("upgradeModule1");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class EC1 { }")
                .build(upgradeModule1);

        final Path upgradeModule2 = base.resolve("upgradeModule2");
        new ModuleBuilder("m1")
                .exports("pkg2")
                .classes("package pkg2; public class EC2 { }")
                .build(upgradeModule2);

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.EC2.class.getName(); } }");

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulepath", module.toString(),
                        "-upgrademodulepath", upgradeModule1.toString(),
                        "-upgrademodulepath", upgradeModule2.toString())
                .files(findJavaFiles(src))
                .run()
                .writeAll();

        Path src2 = base.resolve("src2");
        tb.writeJavaFiles(src2, "module m2 { requires m1; }",
                "package p; class A { void main() { pkg2.EC1.class.getName(); } }");

        final String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulepath", module.toString(),
                        "-upgrademodulepath", upgradeModule1.toString(),
                        "-upgrademodulepath", upgradeModule2.toString())
                .files(findJavaFiles(src2))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("compiler.err.cant.resolve.location: kindname.class, EC1, , , (compiler.misc.location: kindname.package, pkg2, null)")) {
            throw new Exception("Expected output was not found");
        }
    }
}
