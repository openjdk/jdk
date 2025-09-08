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
 * @bug 8356441
 * @summary Recursive formatting in RichDiagnosticFormatter
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.main jdk.compiler/com.sun.tools.javac.api
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main RichFormatterWithTypeAnnotationsReentrantTest
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

public class RichFormatterWithTypeAnnotationsReentrantTest extends TestRunner {
    ToolBox tb;

    public RichFormatterWithTypeAnnotationsReentrantTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        new RichFormatterWithTypeAnnotationsReentrantTest()
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
                        @Target({ElementType.TYPE_USE, ElementType.PARAMETER})
                        @Foo(Bar.BAZ)
                        @interface A {}
                        """,
                        """
                        package lib;
                        public interface M {
                          String f(@A String k, @A String v);
                        }
                        """)
                .run()
                .writeAll();
        Files.delete(libClasses.resolve("lib").resolve("Bar.class"));
        String code =
                """
                import lib.M;
                class T implements M {
                }
                """;
        // verify that the compilation fails wtih an error, and does not crash
        new JavacTask(tb)
                .classpath(libClasses)
                .sources(code)
                .run(Task.Expect.FAIL);
    }
}
