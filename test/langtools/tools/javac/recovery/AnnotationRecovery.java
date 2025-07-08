/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8270139 8361445
 * @summary Verify error recovery w.r.t. annotations
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main AnnotationRecovery
 */

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class AnnotationRecovery extends TestRunner {

    ToolBox tb;

    public AnnotationRecovery() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        AnnotationRecovery t = new AnnotationRecovery();
        t.runTests();
    }

    @Test
    public void testRepeatableAnnotationMissingContainer() throws Exception {
        String code = """
                      import java.lang.annotation.Repeatable;

                      @Repeatable(TestContainer.class)
                      @interface Test { int value(); }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:3:13: compiler.err.cant.resolve: kindname.class, TestContainer, , ",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test
    public void testRepeatableAnnotationWrongAttribute() throws Exception {
        String code = """
                      import java.lang.annotation.Repeatable;

                      @Repeatable(wrong=TestContainer.class)
                      @interface Test { int value(); }
                      @interface TestContainer { Test value(); }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:3:13: compiler.err.cant.resolve.location.args: kindname.method, wrong, , , (compiler.misc.location: kindname.annotation, java.lang.annotation.Repeatable, null)",
                "Test.java:3:1: compiler.err.annotation.missing.default.value: java.lang.annotation.Repeatable, value",
                "2 errors"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8361445
    public void testSuppressWarningsErroneousAttribute1() throws Exception {
        String code = """
                      @SuppressWarnings(CONST)
                      public class Test {
                          public static final String CONST = "";
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:1:19: compiler.err.cant.resolve: kindname.variable, CONST, , ",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8361445
    public void testSuppressWarningsErroneousAttribute2() throws Exception {
        String code = """
                      @SuppressWarnings(0)
                      public class Test {
                      }
                      """;
        Path curPath = Path.of(".");
        List<String> actual = new JavacTask(tb)
                .options("-XDrawDiagnostics", "-XDdev")
                .sources(code)
                .outdir(curPath)
                .run(Expect.FAIL)
                .getOutputLines(OutputKind.DIRECT);

        List<String> expected = List.of(
                "Test.java:1:19: compiler.err.prob.found.req: (compiler.misc.inconvertible.types: int, java.lang.String)",
                "1 error"
        );

        if (!Objects.equals(actual, expected)) {
            error("Expected: " + expected + ", but got: " + actual);
        }
    }

    @Test //JDK-8361445
    public void testSuppressWarningsErroneousAttribute3() throws Exception {
        String[] attributeValues = {
            "Test.BOOLEAN",
            "Test.BYTE",
            "Test.SHORT",
            "Test.INT",
            "Test.LONG",
            "Test.FLOAT",
            "Test.DOUBLE",
            "Test.CHAR",
            "Test.class",
            "@Deprecated",
            "E.A",
        };
        Set<String> variants = new HashSet<>();

        for (String attributeValue : attributeValues) {
            variants.add(attributeValue);
            variants.add("{" + attributeValue + "}");
        }

        for (String attributeValue1 : attributeValues) {
            for (String attributeValue2 : attributeValues) {
                variants.add("{" + attributeValue1 + ", " + attributeValue2 + "}");
            }
        }

        String code = """
                      @SuppressWarnings($ATTRIBUTE_VALUE)
                      public class Test {
                          public static final boolean BOOLEAN = false;
                          public static final byte BYTE = 0;
                          public static final short SHORT = 0;
                          public static final int INT = 0;
                          public static final long LONG = 0l;
                          public static final float FLOAT = 0.0;
                          public static final double DOUBLE = 0.0;
                          public static final char CHAR = '\0';
                      }
                      enum E {
                          A
                      }
                      """;

        for (String variant : variants) {
            System.out.println("current variant: " + variant);
            Path curPath = Path.of(".");
            List<String> actual = new JavacTask(tb)
                    .options("-XDrawDiagnostics", "-XDdev")
                    .sources(code.replace("$ATTRIBUTE_VALUE", variant))
                    .outdir(curPath)
                    .run(Expect.FAIL)
                    .getOutputLines(OutputKind.DIRECT);

            if (actual.isEmpty() || !actual.get(actual.size() - 1).contains("error")) {
                error("Incorrect actual errors: " + actual + " for variant: " + variant);
            }
        }
    }

}
