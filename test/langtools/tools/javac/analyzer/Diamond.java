/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8349132
 * @summary Check behavior of the diamond analyzer
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main Diamond
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class Diamond extends TestRunner {

    private final ToolBox tb;

    public static void main(String... args) throws Exception {
        new Diamond().runTests();
    }

    Diamond() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test //JDK-8349132:
    public void testMissingClassfileForConstructorParamType(Path base) throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");
        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");
        tb.writeJavaFiles(libSrc,
                          """
                          package test;
                          public class Utils {
                              public static void run(Task<Param> uat) {
                              }
                          }
                          """,
                          """
                          package test;
                          public interface Task<T> {
                              public void run(T t) throws Exception;
                          }
                          """,
                          """
                          package test;
                          public class Param {
                          }
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        Files.delete(libClasses.resolve("test").resolve("Param.class"));

        Path src = current.resolve("src");
        Path classes = current.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package test;
                          public class Test {
                              private static void test() {
                                  Utils.run(new Task<Param>() {
                                      @Override
                                      public void run(Param parameter) throws Exception {
                                      }
                                  });
                              }
                          }
                          """);

        Files.createDirectories(classes);

        var out = new JavacTask(tb)
            .options("-XDfind=diamond",
                     "-XDshould-stop.at=FLOW",
                     "-XDrawDiagnostics")
            .classpath(libClasses)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOut = List.of(
            "Test.java:4:28: compiler.err.cant.resolve.location: kindname.class, Param, , , (compiler.misc.location: kindname.class, test.Test, null)",
            "Test.java:6:29: compiler.err.cant.resolve: kindname.class, Param, , ",
            "2 errors"
        );

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);

        }
    }

}
