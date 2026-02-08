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
 * @summary Check behavior of var
 * @library /tools/lib
 * @modules java.logging
 *          java.sql
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit LVTI
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import toolbox.JavacTask;
import toolbox.JavaTask;
import toolbox.Task;
import toolbox.ToolBox;

public class LVTI {

    private static final ToolBox tb = new ToolBox();

    @Test
    public void testInaccessibleInferredType1() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package p1;
                          public class API {
                              public static PackagePrivate get() {
                                  return null;
                              }
                          }
                          """,
                          """
                          package p1;
                          class PackagePrivate {
                          }
                          """,
                          """
                          package p2;
                          import p1.API;
                          public class Test {
                              public static void main(String... args) {
                                  var v = API.get();
                                  System.out.println("pass");
                              }
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.SUCCESS)
            .writeAll();

        var out = new JavaTask(tb)
                .classpath(classes.toString())
                .className("p2.Test")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.STDOUT);

        var expectedOut = List.of("pass");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testInaccessibleInferredType2() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package p1;
                          public class API {
                              public static PackagePrivate get() {
                                  return null;
                              }
                          }
                          """,
                          """
                          package p1;
                          class PackagePrivate {
                              public String toString() {
                                  return "pass";
                              }
                          }
                          """,
                          """
                          package p2;
                          import p1.API;
                          public class Test {
                              public static void main(String... args) {
                                  var v = API.get();
                                  System.out.println(v.toString());
                              }
                          }
                          """);

        Files.createDirectories(classes);

        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(Task.Expect.FAIL)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOut = List.of(
            "Test.java:6:29: compiler.err.not.def.access.class.intf.cant.access: toString(), p1.PackagePrivate",
            "1 error"
        );

        if (!Objects.equals(expectedOut, log)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + log);

        }
    }
}
