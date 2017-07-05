/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8178012
 * @summary tests for multi-module mode compilation
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ModuleBuilder ModuleTestBase
 * @run main LegacyXModuleTest
 */

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task;

public class LegacyXModuleTest extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        new LegacyXModuleTest().runTests();
    }

    @Test
    public void testLegacyXModule(Path base) throws Exception {
        //note: avoiding use of java.base, as that gets special handling on some places:
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, "package com.sun.tools.javac.comp; public class Extra { Modules modules; }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        new JavacTask(tb)
            .options("-XD-Xmodule:jdk.compiler")
            .outdir(classes)
            .files(findJavaFiles(src))
            .run()
            .writeAll()
            .getOutput(Task.OutputKind.DIRECT);

        List<String> log = new JavacTask(tb)
                .options("-XD-Xmodule:java.compiler",
                         "-XD-Xmodule:jdk.compiler",
                         "-XDrawDiagnostics")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> actual =
                Arrays.asList("Extra.java:1:56: compiler.err.cant.resolve.location: kindname.class, Modules, , , " +
                                                "(compiler.misc.location: kindname.class, com.sun.tools.javac.comp.Extra, null)",
                              "1 error");

        if (!Objects.equals(actual, log))
            throw new Exception("expected output not found: " + log);
    }

}
