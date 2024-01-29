/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316470
 * @summary Verify correct source file is set while reporting errors for sealing from Attr
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main SealedErrorPositions
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

public class SealedErrorPositions extends TestRunner {

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new SealedErrorPositions().runTests();
    }

    SealedErrorPositions() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testDoesNotExtendErrorPosition(Path base) throws IOException {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                           """
                           package test;
                           sealed class C permits A, B { }
                           """,
                           """
                           package test;
                           final class A extends C { }
                           """,
                           """
                           package test;
                           final class B { }
                           """);
        Path test = src.resolve("test");
        Path classes = current.resolve("classes");

        Files.createDirectories(classes);

        var log =
                new JavacTask(tb)
                    .options("-XDrawDiagnostics",
                             "-implicit:none",
                             "-sourcepath", src.toString())
                    .outdir(classes)
                    .files(test.resolve("A.java"))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedErrors = List.of(
               "C.java:2:27: compiler.err.invalid.permits.clause: (compiler.misc.doesnt.extend.sealed: test.B)",
               "1 error");

        if (!expectedErrors.equals(log)) {
            throw new AssertionError("Incorrect errors, expected: " + expectedErrors +
                                      ", actual: " + log);
        }
    }

    @Test
    public void testEmptyImplicitPermitsErrorPosition(Path base) throws IOException {
        Path current = base.resolve(".");
        Path src = current.resolve("src");
        tb.writeJavaFiles(src,
                           """
                           package test;
                           sealed class C { }
                           """,
                           """
                           package test;
                           final class A extends C { }
                           """);
        Path test = src.resolve("test");
        Path classes = current.resolve("classes");

        Files.createDirectories(classes);

        var log =
                new JavacTask(tb)
                    .options("-XDrawDiagnostics",
                             "-implicit:none",
                             "-sourcepath", src.toString())
                    .outdir(classes)
                    .files(test.resolve("A.java"))
                    .run(Task.Expect.FAIL)
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expectedErrors = List.of(
               "C.java:2:8: compiler.err.sealed.class.must.have.subclasses",
               "A.java:2:7: compiler.err.cant.inherit.from.sealed: test.C",
               "2 errors");

        if (!expectedErrors.equals(log)) {
            throw new AssertionError("Incorrect errors, expected: " + expectedErrors +
                                      ", actual: " + log);
        }
    }

}
