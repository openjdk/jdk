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
 * @bug 8356894
 * @summary Verify source level checks are performed properly
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main RequiresIdentityTest
*/

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class RequiresIdentityTest extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new RequiresIdentityTest().runTests();
    }

    RequiresIdentityTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testReleaseWorksAsCurrentVersion(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          import java.util.WeakHashMap;
                          import java.util.Optional;

                          public class Test {
                              void test() {
                                  WeakHashMap<Optional<Integer>, Object> m = null;
                                  m.put(Optional.empty(), 1);
                              }
                          }
                          """);

        Files.createDirectories(classes);

        var expectedErrors = List.of(
            "Test.java:6:20: compiler.warn.attempt.to.use.value.based.where.identity.expected",
            "Test.java:7:29: compiler.warn.attempt.to.use.value.based.where.identity.expected",
            "2 warnings"
        );

        {
            var actualErrors =
                    new JavacTask(tb)
                        .options("-XDrawDiagnostics")
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .run()
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
            if (!expectedErrors.equals(actualErrors)) {
                throw new AssertionError("Incorrect errors, expected: " + List.of(expectedErrors) +
                                          ", actual: " + actualErrors);
            }
        }

        {
            var actualErrors =
                    new JavacTask(tb)
                        .options("--release", System.getProperty("java.specification.version"),
                                 "-XDrawDiagnostics")
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .run()
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
            if (!expectedErrors.equals(actualErrors)) {
                throw new AssertionError("Incorrect errors, expected: " + List.of(expectedErrors) +
                                          ", actual: " + actualErrors);
            }
        }
    }

    @Test
    public void testModel(Path base) throws Exception {
        {
            List<String> printed =
                new JavacTask(tb)
                    .options("-Xprint")
                    .classes("java.util.WeakHashMap")
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.STDOUT);

            printed.removeIf(l -> !l.contains("put(") && !l.contains("class WeakHashMap<"));

            List<String> expected = List.of(
                "public class WeakHashMap<@jdk.internal.RequiresIdentity K, V> extends java.util.AbstractMap<K,V> implements java.util.Map<K,V> {",
                "  public V put(@jdk.internal.RequiresIdentity K key,"
            );
            if (!expected.equals(printed)) {
                throw new AssertionError("Expected: " + expected +
                                         ", but got: " + printed);
            }
        }

        {
            List<String> printed =
                new JavacTask(tb)
                    .options("--release", System.getProperty("java.specification.version"),
                             "-Xprint")
                    .classes("java.util.WeakHashMap")
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.STDOUT);

            printed.removeIf(l -> !l.contains("put(") && !l.contains("class WeakHashMap<"));

            List<String> expected = List.of(
                "public class WeakHashMap<K, V> extends java.util.AbstractMap<K,V> implements java.util.Map<K,V> {",
                "  public V put(K arg0,"
            );
            if (!expected.equals(printed)) {
                throw new AssertionError("Expected: " + expected +
                                         ", but got: " + printed);
            }
        }
    }

}
