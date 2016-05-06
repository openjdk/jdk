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
 * @summary tests for "requires public"
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main RequiresPublicTest
 */

import java.nio.file.Files;
import java.nio.file.Path;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class RequiresPublicTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        RequiresPublicTest t = new RequiresPublicTest();
        t.runTests();
    }

    @Test
    public void testJavaSE_OK(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires java.se; }",
                "import java.awt.Frame;\n"  // in java.se
                + "class Test {\n"
                + "    Frame f;\n"
                + "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .files(findJavaFiles(src))
                .outdir(classes)
                .run()
                .writeAll();
    }

    @Test
    public void testJavaSE_Fail(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m { requires java.se; }",
                "import com.sun.source.tree.Tree;\n" // not in java.se (in jdk.compiler)
                + "class Test {\n"
                + "    Tree t;\n"
                + "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .outdir(classes.toString()) // should allow Path here
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        if (!log.contains("Test.java:1:27: compiler.err.doesnt.exist: com.sun.source.tree"))
            throw new Exception("expected output not found");
    }

    @Test
    public void testComplex_OK(Path base) throws Exception {
        Path src = getComplexSrc(base, "", "");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .outdir(classes)
                .run()
                .writeAll();
    }

    @Test
    public void testComplex_Fail(Path base) throws Exception {
        Path src = getComplexSrc(base,
                "import p5.C5; import p6.C6; import p7.C7;\n",
                "C5 c5; C6 c6; C7 c7;\n");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        String log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options("-XDrawDiagnostics",
                        "-modulesourcepath", src.toString())
                .files(findJavaFiles(src))
                .outdir(classes)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutput(Task.OutputKind.DIRECT);

        String[] expect = {
            "C1.java:5:10: compiler.err.not.def.access.package.cant.access: p5.C5, p5",
            "C1.java:5:24: compiler.err.not.def.access.package.cant.access: p6.C6, p6",
            "C1.java:5:38: compiler.err.not.def.access.package.cant.access: p7.C7, p7",
            "C1.java:8:1: compiler.err.cant.resolve.location: kindname.class, C5, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)",
            "C1.java:8:8: compiler.err.cant.resolve.location: kindname.class, C6, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)",
            "C1.java:8:15: compiler.err.cant.resolve.location: kindname.class, C7, , , "
                + "(compiler.misc.location: kindname.class, p1.C1, null)"
        };

        for (String e: expect) {
            if (!log.contains(e))
                throw new Exception("expected output not found: " + e);
        }
    }

    /*
     * Set up the following module graph
     *     m1 -> m2 => m3 => m4 -> m5
     *              -> m6 => m7
     * where -> is requires, => is requires public
     */
    Path getComplexSrc(Path base, String m1_extraImports, String m1_extraUses) throws Exception {
        Path src = base.resolve("src");

        Path src_m1 = src.resolve("m1");
        tb.writeJavaFiles(src_m1,
                "module m1 { requires m2; }",
                "package p1;\n"
                + "import p2.C2;\n"
                + "import p3.C3;\n"
                + "import p4.C4;\n"
                + m1_extraImports
                + "class C1 {\n"
                + "  C2 c2; C3 c3; C4 c4;\n"
                + m1_extraUses
                + "}\n");

        Path src_m2 = src.resolve("m2");
        tb.writeJavaFiles(src_m2,
                "module m2 {\n"
                + "  requires public m3;\n"
                + "  requires        m6;\n"
                + "  exports p2;\n"
                + "}",
                "package p2;\n"
                + "public class C2 { }\n");

        Path src_m3 = src.resolve("m3");
        tb.writeJavaFiles(src_m3,
                "module m3 { requires public m4; exports p3; }",
                "package p3;\n"
                + "public class C3 { }\n");

        Path src_m4 = src.resolve("m4");
        tb.writeJavaFiles(src_m4,
                "module m4 { requires m5; exports p4; }",
                "package p4;\n"
                + "public class C4 { }\n");

        Path src_m5 = src.resolve("m5");
        tb.writeJavaFiles(src_m5,
                "module m5 { exports p5; }",
                "package p5;\n"
                + "public class C5 { }\n");

        Path src_m6 = src.resolve("m6");
        tb.writeJavaFiles(src_m6,
                "module m6 { requires public m7; exports p6; }",
                "package p6;\n"
                + "public class C6 { }\n");

        Path src_m7 = src.resolve("m7");
        tb.writeJavaFiles(src_m7,
                "module m7 { exports p7; }",
                "package p7;\n"
                + "public class C7 { }\n");

        return src;
    }
}
