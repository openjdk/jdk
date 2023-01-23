/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8284283
 * @summary Verify javac's error recovery can handle multiple missing transitive supertypes
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.api
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main MissingTransitiveSuperTypes
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Arrays;

import toolbox.ToolBox;
import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;

public class MissingTransitiveSuperTypes extends TestRunner {
    ToolBox tb;

    public MissingTransitiveSuperTypes() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        new MissingTransitiveSuperTypes().runTests(m -> new Object[] {Paths.get(m.getName())});
    }

    @Test
    public void testMultipleTransitiveSuperTypesMissing(Path base) throws Exception {
        Path libClasses = base.resolve("libclasses");
        Files.createDirectories(libClasses);
        new JavacTask(tb)
            .outdir(libClasses)
            .sources("""
                     package lib;
                     public class Lib implements A, B {}
                     """,
                     """
                     package lib;
                     public interface A {}
                     """,
                     """
                     package lib;
                     public interface B {}
                     """)
            .options()
            .run()
            .writeAll();
        Files.delete(libClasses.resolve("lib").resolve("A.class"));
        Files.delete(libClasses.resolve("lib").resolve("B.class"));
        String code = """
                      public class Test<E> extends lib.Lib {}
                      """;
        List<String> output = new JavacTask(tb)
                .classpath(libClasses)
                .sources(code)
                .options("-XDrawDiagnostics", "-XDdev")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList(
                "Test.java:1:33: compiler.err.cant.access: lib.A, (compiler.misc.class.file.not.found: lib.A)",
                "Test.java:1:8: compiler.err.cant.access: lib.B, (compiler.misc.class.file.not.found: lib.B)",
                "2 errors");
        tb.checkEqual(expected, output);
    }

}
