/*
 * Copyright (c) 2026, Google LLC. All rights reserved.
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
 * @bug 8382871
 * @summary Completion Failure during diagnostic formatting
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.main jdk.compiler/com.sun.tools.javac.api
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main DiagnosticFormatterCompletionFailureTest
 */
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DiagnosticFormatterCompletionFailureTest extends TestRunner {
    ToolBox tb;

    public DiagnosticFormatterCompletionFailureTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        new DiagnosticFormatterCompletionFailureTest()
                .runTests(m -> new Object[] {Paths.get(m.getName())});
    }

    @Test
    public void test(Path base) throws Exception {
        Path libClasses = base.resolve("libclasses");
        Files.createDirectories(libClasses);
        new JavacTask(tb)
                .outdir(libClasses)
                .sources(
                        """
                        package lib;

                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                        @interface A {}
                        """,
                        """
                        package lib;

                        public class I {
                          public final void g(@A Integer other) {}
                        }
                        """,
                        """
                        package lib;

                        class NoSuch<T> {}
                        """,
                        """
                        package lib;

                        public class Lib {
                          public static I f(@A Integer actual)   { return null; }
                          public static I f(@A NoSuch<?> actual) { return null; }
                        }
                        """)
                .run()
                .writeAll();
        Path lib = libClasses.resolve("lib");
        Files.delete(lib.resolve("NoSuch.class"));
        Files.delete(lib.resolve("A.class"));
        String code =
                """
                import static lib.Lib.f;
                class T {
                  void f(L l) {
                    f(2).g(2);
                  }
                }
                """;
        List<String> lines =
                new JavacTask(tb)
                        .classpath(libClasses)
                        .sources(code)
                        .run(Task.Expect.FAIL)
                        .getOutputLines(Task.OutputKind.DIRECT);
        String output = String.join("\n", lines);
        if (!output.contains("Cannot attach type annotations @A to Lib.f")
                && !output.contains("class file for lib.NoSuch not found")) {
            throw new AssertionError(output);
        }
    }
}
