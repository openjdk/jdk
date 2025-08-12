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
 * @bug 8348410
 * @summary Ensure --enable-preview is required for primitive switch on a boxed expression
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main PrimitivePatternsSwitchRequirePreview
 */

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class PrimitivePatternsSwitchRequirePreview extends TestRunner {

    ToolBox tb;

    public PrimitivePatternsSwitchRequirePreview() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        PrimitivePatternsSwitchRequirePreview t = new PrimitivePatternsSwitchRequirePreview();
        t.runTests();
    }

    @Test
    public void testBoolean() throws Exception {
        String code = """
                      class C {
                          public static void testBoolean(Boolean value) {
                            switch (value) {
                                case true   -> System.out.println("true");
                                default     -> System.out.println("false");
                            }
                          }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:4:16: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.primitive.patterns)",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testLong() throws Exception {
        String code = """
                      class C {
                          public static void testLong(Long value) {
                             switch (value) {
                                 case 0L      -> System.out.println("zero");
                                 default      -> System.out.println("non-zero");
                             }
                         }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:4:17: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.primitive.patterns)",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testFloat() throws Exception {
        String code = """
                      class C {
                         public static void testFloat(Float value) {
                           switch (value) {
                               case 0f      -> System.out.println("zero");
                               default      -> System.out.println("non-zero");
                           }
                         }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:4:15: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.primitive.patterns)",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testDouble() throws Exception {
        String code = """
                      class C {
                        public static void testDouble(Long value) {
                            switch (value) {
                                case 0L      -> System.out.println("zero");
                                default      -> System.out.println("non-zero");
                            }
                        }
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev", "-XDshould-stop.at=FLOW")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "C.java:4:16: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.primitive.patterns)",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }
}