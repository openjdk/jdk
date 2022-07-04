/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8280688
 * @summary doclint reference checks withstand warning suppression
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.JavacTask toolbox.JavadocTask toolbox.TestRunner toolbox.ToolBox
 * @run main DocLintReferencesTest
 */

import toolbox.JavacTask;
import toolbox.JavadocTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Combo test for how javac and javadoc handle {@code @see MODULE/TYPE}
 * for different combinations of MODULE and TYPE, with and without
 * {@code @SuppressWarnings("doclint") }.
 *
 * Generally, in javac, references to unknown elements are reported
 * as suppressible warnings if the module is not resolved in the module graph.
 * Otherwise, in both javac and javadoc, any issues with references
 * are reported as errors.
 *
 * This allows references to other modules to appear in documentation comments
 * without causing a hard error if the modules are not available at compile-time.
 */
public class DocLintReferencesTest extends TestRunner {

    public static void main(String... args) throws Exception {
        DocLintReferencesTest t = new DocLintReferencesTest();
        t.runTests();
    }

    DocLintReferencesTest() {
        super(System.err);
    }

    private final ToolBox tb = new ToolBox();

    enum SuppressKind { NO, YES }
    enum ModuleKind { NONE, BAD, NOT_FOUND, GOOD }
    enum TypeKind { NONE, BAD, NOT_FOUND, GOOD }

    @Test
    public void comboTest () {
        for (SuppressKind sk : SuppressKind.values() ) {
            for (ModuleKind mk : ModuleKind.values() ) {
                for (TypeKind tk: TypeKind.values() ) {
                    if (mk == ModuleKind.NONE && tk == TypeKind.NONE) {
                        continue;
                    }

                    try {
                        test(sk, mk, tk);
                    } catch (Throwable e) {
                        error("Exception " + e);
                    }
                }
            }
        }
    }

    void test(SuppressKind sk, ModuleKind mk, TypeKind tk) throws Exception {
        out.println();
        out.println("*** Test SuppressKind:" + sk + " ModuleKind: " + mk + " TypeKind: " + tk);
        Path base = Path.of(sk + "-" + mk + "-" + tk);

        String sw = switch (sk) {
            case NO -> "";
            case YES -> "@SuppressWarnings(\"doclint\")";
        };
        String m = switch (mk) {
            case NONE -> "";
            case BAD -> "bad-name/";
            case NOT_FOUND -> "not.found/";
            case GOOD -> "java.base/";
        };
        String t = switch (tk) {
            case NONE -> "";
            case BAD -> "bad-name";
            case NOT_FOUND -> "java.lang.NotFound";
            case GOOD -> "java.lang.Object";
        };

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * Comment.
                 * @see #M##T#
                 */
                 #SW#
                public class C {
                   private C() { }
                }
                """
                .replace("#M#", m)
                .replace("#T#", t)
                .replace("#SW#", sw));

        testJavac(sk, mk, tk, base, src);
        testJavadoc(sk, mk, tk, base, src);
    }

    void testJavac(SuppressKind sk, ModuleKind mk, TypeKind tk, Path base, Path src) throws Exception {
        Files.createDirectories(base.resolve("classes"));

        out.println("javac:");
        try {
            String s = predictOutput(sk, mk, tk, false);
            Task.Expect e = s.isEmpty() ? Task.Expect.SUCCESS : Task.Expect.FAIL;

            String o = new JavacTask(tb)
                    .outdir(base.resolve("classes"))
                    .options("-Xdoclint:all/protected", "-Werror")
                    .files(tb.findJavaFiles(src))
                    .run(e)
                    .writeAll()
                    .getOutput(Task.OutputKind.DIRECT);

            checkOutput(s, o);

        } catch (Throwable t) {
            error("Error: " + t);
        }
        out.println();
    }

    void testJavadoc(SuppressKind sk, ModuleKind mk, TypeKind tk, Path base, Path src) throws Exception {
        Files.createDirectories(base.resolve("api"));

        out.println("javadoc:");
        try {
            String s = predictOutput(sk, mk, tk, true);
            Task.Expect e = s.isEmpty() ? Task.Expect.SUCCESS : Task.Expect.FAIL;

            String o = new JavadocTask(tb)
                    .outdir(base.resolve("api"))
                    .options("-Xdoclint", "-Werror", "-quiet", "-sourcepath", src.toString(), "p")
                    .run(e)
                    .writeAll()
                    .getOutput(Task.OutputKind.DIRECT);

            checkOutput(s, o);

        } catch (Throwable t) {
            error("Error: " + t);
        }
        out.println();
    }

    private static final String ERROR_UNEXPECTED_TEXT = "error: unexpected text";
    private static final String ERROR_REFERENCE_NOT_FOUND = "error: reference not found";
    private static final String WARNING_MODULE_FOR_REFERENCE_NOT_FOUND = "warning: module for reference not found: not.found";
    private static final String EMPTY = "";

    /**
     * Returns the expected diagnostic, if any, based on the parameters of the test case.
     *
     * The "interesting" cases are those for which the module name is not found,
     * in which case an error for "reference not found" is reduced to warning,
     * which may be suppressed.
     *
     * @param sk whether @SuppressWarnings is present of not
     * @param mk the kind of module in the reference
     * @param tk the kind of class or interface name in the reference
     * @param strict whether all "not found" references are errors,
     *               or just warnings if the module name is not found
     * @return a diagnostic string, or an empty string if no diagnostic should be generated
     */
    String predictOutput(SuppressKind sk, ModuleKind mk, TypeKind tk, boolean strict) {
        return switch (mk) {
            case NONE -> switch(tk) {
                case NONE -> throw new Error("should not happen"); // filtered out in combo loops
                case BAD -> ERROR_UNEXPECTED_TEXT;
                case NOT_FOUND -> ERROR_REFERENCE_NOT_FOUND;
                case GOOD -> EMPTY;
            };

            case BAD -> ERROR_UNEXPECTED_TEXT;

            case NOT_FOUND -> switch(tk) {
                case BAD -> ERROR_UNEXPECTED_TEXT;
                case NONE, NOT_FOUND, GOOD -> strict
                        ? ERROR_REFERENCE_NOT_FOUND
                        : sk == SuppressKind.YES
                            ? EMPTY
                            : WARNING_MODULE_FOR_REFERENCE_NOT_FOUND;
            };

            case GOOD -> switch(tk) {
                case BAD -> ERROR_UNEXPECTED_TEXT;
                case NOT_FOUND -> ERROR_REFERENCE_NOT_FOUND;
                case GOOD, NONE -> EMPTY;
            };
        };
    }

    /**
     * Checks the actual output against the expected string, generated by {@code predictError}.
     * If the expected string is empty, the output should be empty.
     * If the expected string is not empty, it should be present in the output.
     *
     * @param expect the expected string
     * @param found  the output
     */
    void checkOutput(String expect, String found) {
        if (expect.isEmpty()) {
            if (found.isEmpty()) {
                out.println("Output OK");
            } else {
                error("unexpected output");
            }
        } else {
            if (found.contains(expect)) {
                out.println("Output OK");
            } else {
                error("expected output not found: " + expect);
            }
        }

    }
}
