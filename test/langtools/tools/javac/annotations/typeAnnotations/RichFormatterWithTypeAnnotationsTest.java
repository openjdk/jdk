/*
 * Copyright (c) 2025, Google LLC. All rights reserved.
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
 * @bug 8355065
 * @summary ConcurrentModificationException in RichDiagnosticFormatter
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.main jdk.compiler/com.sun.tools.javac.api
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main RichFormatterWithTypeAnnotationsTest
 */
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public class RichFormatterWithTypeAnnotationsTest extends TestRunner {
    ToolBox tb;

    public RichFormatterWithTypeAnnotationsTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        new RichFormatterWithTypeAnnotationsTest()
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
                        enum Bar {
                          BAZ
                        }
                        """,
                        """
                        package lib;
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @interface Foo {
                          Bar value();
                        }
                        """,
                        """
                        package lib;
                        import java.lang.annotation.ElementType;
                        import java.lang.annotation.Retention;
                        import java.lang.annotation.RetentionPolicy;
                        import java.lang.annotation.Target;

                        @Retention(RetentionPolicy.RUNTIME)
                        @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
                        @Foo(Bar.BAZ)
                        @interface A {}
                        """,
                        """
                        package lib;
                        public interface M<K, V> {
                          @A
                          V f(K k, V v);
                        }
                        """)
                .options()
                .run()
                .writeAll();
        Files.delete(libClasses.resolve("lib").resolve("Bar.class"));
        String code =
                """
                import lib.M;
                class T {
                  protected M m;

                  public void f() {
                    m.f(null, 0);
                  }
                }
                """;
        List<String> output =
                new JavacTask(tb)
                        .classpath(libClasses)
                        .sources(code)
                        .options("-Xlint:all", "-Werror", "-XDrawDiagnostics")
                        .run(Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected =
                Arrays.asList(
                        "T.java:3:13: compiler.warn.raw.class.use: lib.M, lib.M<K,V>",
                        "T.java:6:8: compiler.warn.unchecked.call.mbr.of.raw.type: f(K,V), lib.M",
                        "- compiler.err.warnings.and.werror",
                        "1 error",
                        "2 warnings");
        tb.checkEqual(expected, output);
    }
}
