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
                              value @A1 abstract sealed @A2 class C {}
                          }
                          @interface A1 {}
                          @interface A2 {}
                          """);

        Files.createDirectories(classes);

        record TestCase(List<String> options, Task.Expect expected, String... expectedOutput) {}
        String currentVersion = Integer.toString(Runtime.version().feature());

        TestCase[] testCases = new TestCase[] {
            new TestCase(List.of("--release", "25"),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:3:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:4:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:5:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:6:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error",
                         "4 warnings"),
            new TestCase(List.of("--release", currentVersion),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:3:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:4:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:5:5: compiler.warn.restricted.type.not.allowed.preview: value, 28",
                         "Test.java:6:5: compiler.err.preview.feature.disabled.plural: (compiler.misc.feature.value.classes)",
                         "1 error",
                         "4 warnings"),
            new TestCase(List.of("--release", currentVersion, "--enable-preview"),
                         Task.Expect.FAIL,
                         "Test.java:2:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:3:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:4:5: compiler.err.restricted.type.not.allowed.here: value",
                         "Test.java:5:5: compiler.err.restricted.type.not.allowed.here: value",
                         "4 errors")
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

    @BeforeEach
    public void setUp(TestInfo info) throws IOException {
        base = Path.of(info.getTestMethod().orElseThrow().getName());
        Files.createDirectories(base);
    }
}
