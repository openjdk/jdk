/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for module graph resolution issues
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.jdeps/com.sun.tools.javap
 * @build ToolBox ModuleTestBase
 * @run main GraphsTest
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class GraphsTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        GraphsTest t = new GraphsTest();
        t.runTests();
    }

    /**
     * Tests diamond graph with an automatic module added in.
     * +-------------+          +--------------------+         +------------------+
     * | module M    |          | module N           |         | module O         |
     * |             | ----->   |                    | --->    |                  |  --> J.jar
     * | require N   |          | requires public  O |         |                  |
     * | require L   |          |                    |         +------------------+
     * +-------------+          +--------------------+                  ^
     *       |                                                          |
     *       |                  +--------------------+                  |
     *       ------------------>| module L           |                  |
     *                          |                    |------------------
     *                          | requires public O  |
     *                          |                    |
     *                          +--------------------+
     *
     */
    @Test
    void diamond(Path base) throws Exception {

        Path modules = Files.createDirectories(base.resolve("modules"));

        new ModuleBuilder("J")
                .exports("openJ")
                .classes("package openJ; public class J { }")
                .classes("package closedJ; public class J { }")
                .build(base.resolve("jar"));

        Path jarModules = Files.createDirectories(base.resolve("jarModules"));
        Path jar = jarModules.resolve("J.jar");
        tb.new JarTask(jar)
                .baseDir(base.resolve("jar/J"))
                .files(".")
                .run()
                .writeAll();

        new ModuleBuilder("O")
                .exports("openO")
                .requiresPublic("J", jarModules)
                .classes("package openO; public class O { openJ.J j; }")
                .classes("package closedO; public class O { }")
                .build(modules);
        new ModuleBuilder("N")
                .requiresPublic("O", modules, jarModules)
                .exports("openN")
                .classes("package openN; public class N { }")
                .classes("package closedN; public class N { }")
                .build(modules);
        new ModuleBuilder("L")
                .requiresPublic("O", modules, jarModules)
                .exports("openL")
                .classes("package openL; public class L { }")
                .classes("package closedL; public class L { }")
                .build(modules);
        ModuleBuilder m = new ModuleBuilder("M");
        //positive case
        Path positiveSrc = m
                .requires("N", modules)
                .requires("L", modules)
                .classes("package p; public class Positive { openO.O o; openN.N n; openL.L l; }")
                .write(base.resolve("positiveSrc"));

        tb.new JavacTask()
                .options("-XDrawDiagnostics", "-mp", modules + File.pathSeparator + jarModules)
                .outdir(Files.createDirectories(base.resolve("positive")))
                .files(findJavaFiles(positiveSrc))
                .run()
                .writeAll();
        //negative case
        Path negativeSrc = m.classes("package p; public class Negative { closedO.O o; closedN.N n; closedL.L l; }")
                .write(base.resolve("negativeSrc"));
        List<String> log = tb.new JavacTask()
                .options("-XDrawDiagnostics", "-mp", modules + File.pathSeparator + jarModules)
                .outdir(Files.createDirectories(base.resolve("negative")))
                .files(findJavaFiles(negativeSrc))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutputLines(ToolBox.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
                "Negative.java:1:43: compiler.err.doesnt.exist: closedO",
                "Negative.java:1:56: compiler.err.doesnt.exist: closedN",
                "Negative.java:1:69: compiler.err.doesnt.exist: closedL");
        if (!log.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
        //multi module mode
        m.write(modules);
        List<String> out = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", modules + "/*/src",
                        "-mp", jarModules.toString()
                )
                .outdir(Files.createDirectories(base.resolve("negative")))
                .files(findJavaFiles(modules))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutputLines(ToolBox.OutputKind.DIRECT);
        expected = Arrays.asList(
                "Negative.java:1:43: compiler.err.not.def.access.package.cant.access: closedO.O, closedO",
                "Negative.java:1:56: compiler.err.not.def.access.package.cant.access: closedN.N, closedN",
                "Negative.java:1:69: compiler.err.not.def.access.package.cant.access: closedL.L, closedL");
        if (!out.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
        //checks if the output does not contain messages about exported packages.
        Pattern regex = Pattern.compile("compiler\\.err.*(openO\\.O|openN\\.N|openL\\.L)");
        for (String s : out) {
            if (regex.matcher(s).find()) {
                throw new Exception("Unexpected output: " + s);
            }
        }
    }

    /**
     * Tests graph where module M reexport package of N, but N export the package only to M.
     *
    +-------------+        +--------------------+        +---------------+
    | module L    |        | module M           |        | module N      |
    |             | -----> |                    | -----> |               |
    |  requires M |        |  requires public N |        | exports P to M|
    +-------------+        |                    |        +---------------+
                           +--------------------+
    */
    @Test
    public void reexportOfQualifiedExport(Path base) throws Exception {
        Path modules = base.resolve("modules");
        new ModuleBuilder("M")
                .requiresPublic("N")
                .write(modules);
        new ModuleBuilder("N")
                .exportsTo("pack", "M")
                .classes("package pack; public class Clazz { }")
                .write(modules);
        new ModuleBuilder("L")
                .requires("M")
                .classes("package p; public class A { A(pack.Clazz cl){} } ")
                .write(modules);
        String log = tb.new JavacTask()
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", modules + "/*/src")
                .outdir(Files.createDirectories(base.resolve("negative")))
                .files(findJavaFiles(modules))
                .run(ToolBox.Expect.FAIL)
                .writeAll()
                .getOutput(ToolBox.OutputKind.DIRECT);

        String expected = "A.java:1:35: compiler.err.not.def.access.package.cant.access: pack.Clazz, pack";
        if (!log.contains(expected)) {
            throw new Exception("Expected output not found");
        }
    }
}
