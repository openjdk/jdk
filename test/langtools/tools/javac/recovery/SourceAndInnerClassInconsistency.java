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

/*
 * @test
 * @bug 8340840
 * @summary Ensure InnerClasses attribute from a classfile won't overwrite properties
 *          of a source-based class
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit SourceAndInnerClassInconsistency
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

public class SourceAndInnerClassInconsistency {

    ToolBox tb = new ToolBox();

    @Test
    public void testNonStaticToStatic() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public class Nested {}
                          }
                          """,
                          """
                          public class Other {
                              Complex.Nested n;
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .files(tb.findJavaFiles(src))
            .outdir(classes)
            .run()
            .writeAll();

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public static class Nested {}
                              private void t() {
                                  Other o;
                              }
                          }
                          """);
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics", "-Xlint:classfile")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = List.of(
            "- compiler.warn.inconsistent.inner.classes: Complex.Nested, Other.class",
            "1 warning"
        );
        if (!Objects.equals(expected, log)) {
            throw new AssertionError("Wrong output, expected: " + expected +
                                     ", got: " + log);
        }
    }

    @Test
    public void testStaticToNonStatic() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public static class Nested {}
                          }
                          """,
                          """
                          public class Other {
                              Complex.Nested n;
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .files(tb.findJavaFiles(src))
            .outdir(classes)
            .run()
            .writeAll();

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public class Nested {}
                              private void t() {
                                  Other o;
                              }
                          }
                          """);
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
        List<String> log = new JavacTask(tb)
            .options("-XDrawDiagnostics", "-Xlint:classfile")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = List.of(
            "- compiler.warn.inconsistent.inner.classes: Complex.Nested, Other.class",
            "1 warning"
        );
        if (!Objects.equals(expected, log)) {
            throw new AssertionError("Wrong output, expected: " + expected +
                                     ", got: " + log);
        }
    }

    @Test
    public void testNoWarning() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public static class Nested {}
                          }
                          """,
                          """
                          public class Other {
                              Complex.Nested n;
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .files(tb.findJavaFiles(src))
            .outdir(classes)
            .run()
            .writeAll();

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public static class Nested {}
                              private void t() {
                                  Other o;
                              }
                          }
                          """);
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-Xlint:classfile", "-Werror")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
    }

    @Test
    public void testSuppress() throws Exception {
        Path base = Paths.get(".");
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public static class Nested {}
                          }
                          """,
                          """
                          public class Other {
                              Complex.Nested n;
                          }
                          """);

        Files.createDirectories(classes);

        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .files(tb.findJavaFiles(src))
            .outdir(classes)
            .run()
            .writeAll();

        tb.writeJavaFiles(src,
                          """
                          public class Complex {
                              public class Nested {}
                              private void t() {
                                  Other o;
                              }
                          }
                          """);
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-XDdev")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
        new JavacTask(tb)
            .options("-XDrawDiagnostics", "-Xlint:-classfile", "-Werror")
            .classpath(classes)
            .files(src.resolve("Complex.java"))
            .outdir(classes)
            .run()
            .writeAll();
    }

}
