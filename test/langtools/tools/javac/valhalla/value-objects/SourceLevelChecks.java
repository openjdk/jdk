/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388340
 * @summary Verify source level checks are performed properly
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit SourceLevelChecks
*/

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

import static org.junit.jupiter.api.Assertions.*;

public class SourceLevelChecks {

    private Path base;
    private final ToolBox tb = new ToolBox();

    @Test
    public void testValueAsATypeOld() throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");

        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");
        tb.writeJavaFiles(libSrc,
                          """
                          class value {}
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run()
            .writeAll();

        Path test = current.resolve("test");
        Path src = test.resolve("src");
        Path classes = test.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          class Test {
                              value record;
                              value value;
                              value sealed;
                              value @A1 [] f1;
                              value f2[];
                              value f2 @A1 [];
                              value @A1 [] m1() {}
                              value m2()[] {}
                              value m2() @A1 [] {}
                              value @A1 abstract sealed @A2 class C1 {}
                              sealed @A1 value abstract @A2 class C2 {}
                              non-sealed @A1 value abstract @A2 class C3 {}
                          }
                          @interface A1 {}
                          @interface A2 {}
                          """);

        Files.createDirectories(classes);

        record TestCase(List<String> options, Task.Expect expected, List<String> expectedOutput) {
            public TestCase(List<String> options, Task.Expect expected, String... expectedOutput) {
                this(options, expected, List.of(expectedOutput));
            }
        }
        String currentVersion = Integer.toString(Runtime.version().feature());
        List<String> expectedValueWarnings = new ArrayList<>(List.of(
             "Test.java:2:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:3:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:4:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:5:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:6:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:7:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:8:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:9:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
             "Test.java:10:5: compiler.warn.restricted.type.not.allowed.preview: value, 28"
        ));

        TestCase[] testCases = new TestCase[] {
            new TestCase(List.of("--release", "16"),
                         Task.Expect.FAIL,
                         concat(expectedValueWarnings,
                                "Test.java:11:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                                "Test.java:11:24: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.sealed.classes), 16, 17",
                                "2 errors",
                                "9 warnings")),
            new TestCase(List.of("--release", "25"),
                         Task.Expect.FAIL,
                         concat(expectedValueWarnings,
                                "Test.java:11:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                                "1 error",
                                "9 warnings")),
            new TestCase(List.of("--release", currentVersion),
                         Task.Expect.FAIL,
                         concat(expectedValueWarnings,
                                "Test.java:11:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                                "1 error",
                                "9 warnings")),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:3:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:4:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:5:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:6:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:7:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:8:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:9:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:10:5: compiler.err.restricted.type.not.allowed.here: value",
                         "9 errors")
        };

        for (TestCase testCase : testCases) {
            List<String> options = new ArrayList<>();

            options.addAll(testCase.options());
            options.add("-XDrawDiagnostics");

            var log =
                    new JavacTask(tb)
                        .options(options)
                        .classpath(libClasses)
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .run(testCase.expected())
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

            assertEquals(testCase.expectedOutput(), log);
        }
    }

    private List<String> concat(List<String> base, String... additional) {
        List<String> result = new ArrayList<>();

        result.addAll(base);
        result.addAll(List.of(additional));
        return result;
    }

    @Test
    public void testValueNonSealedErrorCheck() throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");

        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");
        tb.writeJavaFiles(libSrc,
                          """
                          class value {}
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run()
            .writeAll();

        Path test = current.resolve("test");
        Path src = test.resolve("src");
        Path classes = test.resolve("classes");
        tb.writeJavaFiles(src,
                          """
                          class Test {
                              value non-sealed class C2 extends C1 {}
                              sealed class C1 {}
                          }
                          """);

        Files.createDirectories(classes);

        record TestCase(List<String> options, Task.Expect expected, String... expectedOutput) {}
        String currentVersion = Integer.toString(Runtime.version().feature());

        TestCase[] testCases = new TestCase[] {
            new TestCase(List.of("--release", "16"),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "Test.java:2:11: compiler.err.feature.not.supported.in.source.plural: (compiler.misc.feature.sealed.classes), 16, 17",
                         "2 errors"),
            new TestCase(List.of("--release", "25"),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         Task.Expect.FAIL,
                         "Test.java:2:22: compiler.err.non.abstract.value.class.cant.be.sealed.or.non.sealed",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error")
        };

        for (TestCase testCase : testCases) {
            List<String> options = new ArrayList<>();

            options.addAll(testCase.options());
            options.add("-XDrawDiagnostics");

            var log =
                    new JavacTask(tb)
                        .options(options)
                        .classpath(libClasses)
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .run(testCase.expected())
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

            assertEquals(List.of(testCase.expectedOutput()), log);
        }
    }

    @Test
    public void testLocalClassValue() throws Exception {
        Path current = base.resolve(".");
        Path lib = current.resolve("lib");

        Path libSrc = lib.resolve("src");
        Path libClasses = lib.resolve("classes");
        tb.writeJavaFiles(libSrc,
                          """
                          class value {}
                          """);

        Files.createDirectories(libClasses);

        new JavacTask(tb)
            .outdir(libClasses)
            .files(tb.findJavaFiles(libSrc))
            .run()
            .writeAll();

        Path test = current.resolve("test");
        Path src = test.resolve("src");
        Path classes = test.resolve("classes");

        Files.createDirectories(classes);

        record TestCase(List<String> options, String input, Task.Expect expected, String... expectedOutput) {}
        String currentVersion = Integer.toString(Runtime.version().feature());

        TestCase[] testCases = new TestCase[] {
            new TestCase(List.of("--release", "25"),
                         "value class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion),
                         "value class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         "value class VC {}",
                         Task.Expect.SUCCESS,
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile"),
            new TestCase(List.of("--release", "25"),
                         "value sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion),
                         "value sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         "value sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:22: compiler.err.non.abstract.value.class.cant.be.sealed.or.non.sealed",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
            new TestCase(List.of("--release", "25"),
                         "value non-sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion),
                         "value non-sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:9: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error"),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         "value non-sealed class VC {}",
                         Task.Expect.FAIL,
                         "Test.java:3:26: compiler.err.non.abstract.value.class.cant.be.sealed.or.non.sealed",
                         "- compiler.note.preview.filename: Test.java, DEFAULT",
                         "- compiler.note.preview.recompile",
                         "1 error"),
        };

        String template = """
                          class Test {
                              void test() {
                                  #TEST
                              }
                          }
                          """;
        for (TestCase testCase : testCases) {
            List<String> options = new ArrayList<>();

            options.addAll(testCase.options());
            options.add("-XDrawDiagnostics");

            tb.writeJavaFiles(src,
                              template.replace("#TEST", testCase.input()));

            var log =
                    new JavacTask(tb)
                        .options(options)
                        .classpath(libClasses)
                        .outdir(classes)
                        .files(tb.findJavaFiles(src))
                        .run(testCase.expected())
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

            assertEquals(List.of(testCase.expectedOutput()), log);
        }
    }

    @BeforeEach
    public void setUp(TestInfo info) throws IOException {
        base = Path.of(info.getTestMethod().orElseThrow().getName());
        Files.createDirectories(base);
    }
}
