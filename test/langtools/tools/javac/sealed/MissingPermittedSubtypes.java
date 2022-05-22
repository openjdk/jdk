/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8277105
 * @summary Verify missing permitted subtype is handled properly for both casts and pattern switches.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main MissingPermittedSubtypes
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import toolbox.TestRunner;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class MissingPermittedSubtypes extends TestRunner {

    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new MissingPermittedSubtypes().runTests();
    }

    MissingPermittedSubtypes() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testInaccessiblePermitted(Path base) throws IOException {
        Path current = base.resolve(".");
        Path libSrc = current.resolve("lib-src");

        tb.writeJavaFiles(libSrc,
                           """
                           package lib;
                           public sealed interface S permits A, B1, B2 {}
                           """,
                           """
                           package lib;
                           public final class A implements S {}
                           """,
                           """
                           package lib;
                           final class B1 implements S {}
                           """,
                           """
                           package lib;
                           final class B2 implements S, Runnable {
                               public void run() {}
                           }
                           """);

        Path libClasses = current.resolve("libClasses");

        Files.createDirectories(libClasses);

        new JavacTask(tb)
                .options("--enable-preview",
                         "-source", JAVA_VERSION)
                .outdir(libClasses)
                .files(tb.findJavaFiles(libSrc))
                .run();

        Path b1Class = libClasses.resolve("lib").resolve("B1.class");

        Files.delete(b1Class);

        Path b2Class = libClasses.resolve("lib").resolve("B2.class");

        Files.delete(b2Class);

        {
            Path src1 = current.resolve("src1");
            tb.writeJavaFiles(src1,
                               """
                               package test;
                               import lib.*;
                               public class Test1 {
                                   private void test(S obj) {
                                       int i = switch (obj) {
                                           case A a -> 0;
                                       };
                                       Runnable r = () -> {obj = null;};
                                   }
                               }
                               """);

            Path classes1 = current.resolve("classes1");

            Files.createDirectories(classes1);

            var log =
                    new JavacTask(tb)
                        .options("--enable-preview",
                                 "-source", JAVA_VERSION,
                                 "-XDrawDiagnostics",
                                 "-Xlint:-preview",
                                 "--class-path", libClasses.toString())
                        .outdir(classes1)
                        .files(tb.findJavaFiles(src1))
                        .run(Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

            List<String> expectedErrors = List.of(
                   "Test1.java:5:24: compiler.err.cant.access: lib.B1, (compiler.misc.class.file.not.found: lib.B1)",
                   "Test1.java:8:29: compiler.err.cant.ref.non.effectively.final.var: obj, (compiler.misc.lambda)",
                   "- compiler.note.preview.filename: Test1.java, DEFAULT",
                   "- compiler.note.preview.recompile",
                   "2 errors");

            if (!expectedErrors.equals(log)) {
                throw new AssertionError("Incorrect errors, expected: " + expectedErrors +
                                          ", actual: " + log);
            }
        }

        {
            Path src2 = current.resolve("src2");
            tb.writeJavaFiles(src2,
                               """
                               package test;
                               import lib.*;
                               public class Test1 {
                                   private void test(S obj) {
                                    Runnable r = (Runnable) obj;
                                    String s = (String) obj;
                                   }
                               }
                               """);

            Path classes2 = current.resolve("classes2");

            Files.createDirectories(classes2);

            var log =
                    new JavacTask(tb)
                        .options("--enable-preview",
                                 "-source", JAVA_VERSION,
                                 "-XDrawDiagnostics",
                                 "-Xlint:-preview",
                                 "--class-path", libClasses.toString())
                        .outdir(classes2)
                        .files(tb.findJavaFiles(src2))
                        .run(Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

            List<String> expectedErrors = List.of(
                   "Test1.java:5:19: compiler.err.cant.access: lib.B1, (compiler.misc.class.file.not.found: lib.B1)",
                   "Test1.java:6:26: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: lib.S, java.lang.String)",
                   "2 errors");

            if (!expectedErrors.equals(log)) {
                throw new AssertionError("Incorrect errors, expected: " + expectedErrors +
                                          ", actual: " + log);
            }
        }
    }

}
