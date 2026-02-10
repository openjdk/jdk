/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Check behavior of warnings related to var
 * @library /tools/lib
 * @modules java.logging
 *          java.sql
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit VarWarnings
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class VarWarnings {

    private static final ToolBox tb = new ToolBox();

    @Test
    public void testDeprecationWarning() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package p1;
                          import java.util.List;
                          import java.util.function.Consumer;
                          @SuppressWarnings("deprecation")
                          public class API {
                              public static DeprOutter<DeprInner> get() {
                                  return new DeprOutter<>();
                              }
                              public static Iterable<DeprOutter<DeprInner>> getIterable() {
                                  return null;
                              }
                              public static void run(Consumer<DeprOutter<DeprInner>> task) {
                              }
                          }
                          """,
                          """
                          package p1;
                          @Deprecated
                          public class DeprInner {
                          }
                          """,
                          """
                          package p1;
                          @Deprecated
                          public class DeprOutter<T> {
                              public T get() {
                                  return null;
                              }
                          }
                          """,
                          """
                          package p2;
                          import p1.API;
                          public class Test {
                              public static void main(String... args) {
                                  var v1 = API.get();
                                  API.run(v -> v.get().toString());
                                  API.run((var v) -> v.get().toString());
                                  for (var v2 : API.getIterable()) {}
                              }
                          }
                          """);

        Files.createDirectories(classes);

        var out = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "-Xlint:deprecation")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOut = List.of("");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);

        }
    }

    @Test
    public void testRawTypeWarning() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          package p1;
                          import java.util.List;
                          import java.util.function.Consumer;
                          @SuppressWarnings("rawtypes")
                          public class API {
                              public static RawOutter<RawInner> get() {
                                  return new RawOutter<>();
                              }
                              public static Iterable<RawOutter<RawInner>> getIterable() {
                                  return null;
                              }
                              public static void run(Consumer<RawOutter<RawInner>> task) {
                              }
                          }
                          """,
                          """
                          package p1;
                          public class RawInner<T> {
                          }
                          """,
                          """
                          package p1;
                          public class RawOutter<T> {
                              public T get() {
                                  return null;
                              }
                          }
                          """,
                          """
                          package p2;
                          import p1.API;
                          public class Test {
                              public static void main(String... args) {
                                  var v1 = API.get();
                                  API.run(v -> v.get().toString());
                                  API.run((var v) -> v.get().toString());
                                  for (var v2 : API.getIterable()) {}
                              }
                          }
                          """);

        Files.createDirectories(classes);

        var out = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "-Xlint:rawtypes")
                .outdir(classes)
                .files(tb.findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        var expectedOut = List.of("");

        if (!Objects.equals(expectedOut, out)) {
            throw new AssertionError("Incorrect Output, expected: " + expectedOut +
                                      ", actual: " + out);

        }
    }

}
