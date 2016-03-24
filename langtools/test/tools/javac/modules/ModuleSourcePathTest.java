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
 * @summary tests for -modulesourcepath
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main ModuleSourcePathTest
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModuleSourcePathTest extends ModuleTestBase {

    public static final char PATH_SEP = File.pathSeparatorChar;

    public static void main(String... args) throws Exception {
        ModuleSourcePathTest t = new ModuleSourcePathTest();
        t.runTests();
    }

    @Test
    void testSourcePathConflict(Path base) throws Exception {
        Path sp = base.resolve("src");
        Path msp = base.resolve("srcmodules");

        String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-sourcepath", sp.toString().replace('/', File.separatorChar),
                        "-modulesourcepath", msp.toString().replace('/', File.separatorChar),
                        "dummyClass")
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        if (!log.contains("cannot specify both -sourcepath and -modulesourcepath"))
            throw new Exception("expected diagnostic not found");
    }

    @Test
    void testUnnormalizedPath1(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1, "module m1 { }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", src.toString())
                .outdir(modules)
                .files(prefixAll(findJavaFiles(src), Paths.get("./")))
                .run()
                .writeAll();
    }

    @Test
    void testUnnormalizedPath2(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1, "module m1 { }");
        Path modules = base.resolve("modules");
        Files.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", "./" + src)
                .outdir(modules)
                .files(findJavaFiles(src))
                .run()
                .writeAll();
    }

    private Path[] prefixAll(Path[] paths, Path prefix) {
        return Stream.of(paths)
                .map(prefix::resolve)
                .collect(Collectors.toList())
                .toArray(new Path[paths.length]);
    }

    @Test
    void regularBraces(Path base) throws Exception {
        generateModules(base, "src1", "src2/inner_dir");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{src1,src2/inner_dir}")
                .files(base.resolve("src1/m0/pkg0/A.java"), base.resolve("src2/inner_dir/m1/pkg1/A.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("m0/pkg0/A.class"),
                modules.resolve("m1/pkg1/A.class"),
                modules.resolve("m0/module-info.class"),
                modules.resolve("m1/module-info.class"));
    }

    @Test
    void mismatchedBraces(Path base) throws Exception {
        final List<String> sourcePaths = Arrays.asList(
                "{",
                "}",
                "}{",
                "./}",
                ".././{./",
                "src{}}",
                "{{}{}}{}}",
                "src/{a,b}/{",
                "src/{a,{,{}}",
                "{.,..{}/src",
                "*}{",
                "{}*}"
        );
        for (String sourcepath : sourcePaths) {
            String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                    .options("-XDrawDiagnostics",
                            "-modulesourcepath", sourcepath.replace('/', File.separatorChar))
                    .run(ToolBox.Expect.FAIL)
                    .writeAll()
                    .getOutput(ToolBox.OutputKind.DIRECT);

            if (!log.contains("- compiler.err.illegal.argument.for.option: -modulesourcepath, mismatched braces"))
                throw new Exception("expected output for path [" + sourcepath + "] not found");
        }
    }

    @Test
    void deepBraces(Path base) throws Exception {
        String[] modulePaths = {"src/src1",
                "src/src2",
                "src/src3",
                "src/srcB/src1",
                "src/srcB/src2/srcXX",
                "src/srcB/src2/srcXY",
                "src/srcC/src1",
                "src/srcC/src2/srcXX",
                "src/srcC/src2/srcXY"};
        generateModules(base, modulePaths);

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath",
                        base + "/{src/{{src1,src2,src3},{srcB,srcC}/{src1,src2/srcX{X,Y}/}},.}"
                                .replace('/', File.separatorChar))
                .files(findJavaFiles(base.resolve(modulePaths[modulePaths.length - 1])))
                .outdir(modules)
                .run()
                .writeAll();

        for (int i = 0; i < modulePaths.length; i++) {
            checkFiles(modules.resolve("m" + i + "/module-info.class"));
        }
        checkFiles(modules.resolve("m8/pkg8/A.class"));
    }

    @Test
    void fileInPath(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("kettle$"), "module kettle$ { }", "package electric; class Heater { }");
        tb.writeFile(base.resolve("dummy.txt"), "");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{dummy.txt,src}")
                .files(src.resolve("kettle$/electric/Heater.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("kettle$/electric/Heater.class"));
        checkFiles(modules.resolve("kettle$/module-info.class"));
    }

    @Test
    void noAlternative(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("kettle$"), "module kettle$ { }", "package electric; class Heater { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{src}")
                .files(src.resolve("kettle$/electric/Heater.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("kettle$/electric/Heater.class"));
        checkFiles(modules.resolve("kettle$/module-info.class"));
    }

    @Test
    void noChoice(Path base) throws Exception {
        tb.writeJavaFiles(base.resolve("kettle$"), "module kettle$ { }", "package electric; class Heater { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{}")
                .files(base.resolve("kettle$/electric/Heater.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("kettle$/electric/Heater.class"));
        checkFiles(modules.resolve("kettle$/module-info.class"));
    }

    @Test
    void nestedModules(Path src) throws Exception {
        Path carModule = src.resolve("car");
        tb.writeJavaFiles(carModule, "module car { }", "package light; class Headlight { }");
        tb.writeJavaFiles(carModule.resolve("engine"), "module engine { }", "package flat; class Piston { }");

        final Path modules = src.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", "{" + src + "," + src + "/car}")
                .files(findJavaFiles(src))
                .outdir(modules)
                .run()
                .writeAll();
        checkFiles(modules.resolve("car/light/Headlight.class"));
        checkFiles(modules.resolve("engine/flat/Piston.class"));
    }

    @Test
    void relativePaths(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("kettle"), "module kettle { }", "package electric; class Heater { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/src/./../src")
                .files(src.resolve("kettle/electric/Heater.java"))
                .outdir(modules)
                .run()
                .writeAll();
        checkFiles(modules.resolve("kettle/electric/Heater.class"));
        checkFiles(modules.resolve("kettle/module-info.class"));
    }

    @Test
    void duplicatePaths(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"), "module m1 { }", "package a; class A { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{src,src,src}")
                .files(src.resolve("m1/a/A.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("m1/module-info.class"));
    }

    @Test
    void notExistentPaths(Path base) throws Exception {
        tb.writeJavaFiles(base.resolve("m1"), "module m1 { requires m0; }", "package a; class A { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/not_exist" + PATH_SEP + base + "/{not_exist,}")
                .files(base.resolve("m1/a/A.java"))
                .outdir(modules)
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);
        if (!log.contains("compiler.err.module.not.found: m0"))
            throw new Exception("expected output for not existent module source path not found");
    }

    @Test
    void notExistentPathShouldBeSkipped(Path base) throws Exception {
        tb.writeJavaFiles(base.resolve("m1"), "module m1 { }", "package a; class A { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "{/not_exist,/}")
                .files(base.resolve("m1/a/A.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("m1/module-info.class"));
    }

    @Test
    void commas(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src.resolve("m1"), "module m1 { }", "package a; class A { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/{,{,,,,src,,,}}")
                .files(src.resolve("m1/a/A.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("m1/module-info.class"));
    }

    @Test
    void asterisk(Path base) throws Exception {
        tb.writeJavaFiles(base.resolve("kettle").resolve("classes"), "module kettle { }",
                "package electric; class Heater { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", base + "/*/classes/")
                .files(base.resolve("kettle/classes/electric/Heater.java"))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("kettle/electric/Heater.class"));
        checkFiles(modules.resolve("kettle/module-info.class"));
    }

    @Test
    void asteriskInDifferentSets(Path base) throws Exception {
        Path src = base.resolve("src");
        final Path module = src.resolve("kettle");
        tb.writeJavaFiles(module.resolve("classes"), "module kettle { }", "package electric; class Heater { }");
        tb.writeJavaFiles(module.resolve("gensrc"), "package model; class Java { }");
        tb.writeJavaFiles(module.resolve("special/classes"), "package gas; class Heater { }");

        final Path modules = base.resolve("modules");
        tb.createDirectories(modules);

        tb.new JavacTask(ToolBox.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", src + "{/*/gensrc/,/*/classes/}" + PATH_SEP
                                + src + "/*/special/classes")
                .files(findJavaFiles(src))
                .outdir(modules)
                .run()
                .writeAll();

        checkFiles(modules.resolve("kettle/electric/Heater.class"));
        checkFiles(modules.resolve("kettle/gas/Heater.class"));
        checkFiles(modules.resolve("kettle/model/Java.class"));
        checkFiles(modules.resolve("kettle/module-info.class"));
    }

    @Test
    void asteriskIllegalUse(Path base) throws Exception {
        final List<String> sourcePaths = Arrays.asList(
                "*",
                "**",
                "***",
                "*.*",
                ".*",
                "*.",
                "src/*/*/",
                "{*,*}",
                "src/module*/"
        );
        for (String sourcepath : sourcePaths) {
            String log = tb.new JavacTask(ToolBox.Mode.CMDLINE)
                    .options("-XDrawDiagnostics",
                            "-modulesourcepath", sourcepath.replace('/', File.separatorChar))
                    .run(ToolBox.Expect.FAIL)
                    .writeAll()
                    .getOutput(ToolBox.OutputKind.DIRECT);

            if (!log.contains("- compiler.err.illegal.argument.for.option: -modulesourcepath, illegal use of *"))
                throw new Exception("expected output for path [" + sourcepath + "] not found");
        }
    }

    private void generateModules(Path base, String... paths) throws IOException {
        for (int i = 0; i < paths.length; i++) {
            String moduleName = "m" + i;
            String dependency = i > 0 ? "requires m" + (i - 1) + ";" : "";
            tb.writeJavaFiles(base.resolve(paths[i]).resolve(moduleName),
                    "module " + moduleName + " { " + dependency + " }",
                    "package pkg" + i + "; class A { }");
        }
    }

    private void checkFiles(Path... files) throws Exception {
        for (Path file : files) {
            if (!Files.exists(file)) {
                throw new Exception("File not exists: " + file);
            }
        }
    }
}
