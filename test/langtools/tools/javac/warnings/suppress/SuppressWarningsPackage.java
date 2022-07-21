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
 * @bug 8280866
 * @summary Verify SuppressWarnings works on package clauses and modules.
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 * @modules jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.TestRunner toolbox.ToolBox
 * @run main SuppressWarningsPackage
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import toolbox.JavacTask;
import toolbox.Task.Expect;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class SuppressWarningsPackage extends TestRunner {
    public static void main(String... args) throws Exception {
        SuppressWarningsPackage t = new SuppressWarningsPackage();
        t.runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    public SuppressWarningsPackage() throws IOException {
        super(System.err);
    }

    @Test
    public void testSuppressWarningsOnPackageInfo(Path base) throws IOException {
        Path src = base.resolve("src");
        Path classes = Files.createDirectories(base.resolve("classes"));
        TestCase[] testCases = new TestCase[] {
            new TestCase("",
                         "package-info.java:1:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "package-info.java:1:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "Use.java:2:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "Use.java:2:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "4 warnings"),
            new TestCase("@SuppressWarnings(\"deprecation\")",
                         "Use.java:2:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "Use.java:2:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "2 warnings")
        };
        for (TestCase tc : testCases) {
            tb.writeJavaFiles(src,
                              """
                              @DeprecatedAnn(DeprecatedClass.class)
                              #
                              package test;
                              """.replace("#", tc.sw),
                              """
                              package test;
                              @Deprecated
                              public @interface DeprecatedAnn {
                                  public Class<?> value();
                              }
                              """,
                              """
                              package test;
                              @Deprecated
                              public class DeprecatedClass {
                                  public static class Nested {}
                              }
                              """,
                              """
                              package test;
                              @DeprecatedAnn(DeprecatedClass.class)
                              public class Use {}
                              """);

            List<String> log = new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .options("-XDrawDiagnostics",
                             "-Xlint:deprecation")
                    .run(Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

            if (!Objects.equals(log, List.of(tc.expectedOutput))) {
                error("Unexpected output, expected:\n" + Arrays.toString(tc.expectedOutput) +
                                         "\nactual:\n" + log);
            }
        }
    }

    @Test
    public void testSuppressWarningsOnModuleInfo(Path base) throws IOException {
        Path src = base.resolve("src");
        Path classes = Files.createDirectories(base.resolve("classes"));
        TestCase[] testCases = new TestCase[] {
            new TestCase("",
                         "module-info.java:3:12: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "module-info.java:4:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "module-info.java:4:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "module-info.java:7:14: compiler.warn.has.been.deprecated: test.Service, test",
                         "module-info.java:8:18: compiler.warn.has.been.deprecated: test.Service, test",
                         "module-info.java:8:36: compiler.warn.has.been.deprecated: test.ServiceImpl, test",
                         "package-info.java:1:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "package-info.java:1:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "Use.java:2:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "Use.java:2:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "10 warnings"),
            new TestCase("@SuppressWarnings(\"deprecation\")",
                         "module-info.java:3:12: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "package-info.java:1:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "package-info.java:1:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "Use.java:2:2: compiler.warn.has.been.deprecated: test.DeprecatedAnn, test",
                         "Use.java:2:16: compiler.warn.has.been.deprecated: test.DeprecatedClass, test",
                         "5 warnings")
        };
        for (TestCase tc : testCases) {
            tb.writeJavaFiles(src,
                              """
                              import test.DeprecatedAnn;
                              import test.DeprecatedClass;
                              import test.DeprecatedClass.Nested;
                              @DeprecatedAnn(DeprecatedClass.class)
                              #
                              module m {
                                  uses test.Service;
                                  provides test.Service with test.ServiceImpl;
                              }
                              """.replace("#", tc.sw),
                              """
                              @DeprecatedAnn(DeprecatedClass.class)
                              package test;
                              """,
                              """
                              package test;
                              @Deprecated
                              public @interface DeprecatedAnn {
                                  public Class<?> value();
                              }
                              """,
                              """
                              package test;
                              @Deprecated
                              public class DeprecatedClass {
                                  public static class Nested {}
                              }
                              """,
                              """
                              package test;
                              @Deprecated
                              public interface Service {}
                              """,
                              """
                              package test;
                              @Deprecated
                              public class ServiceImpl implements Service {}
                              """,
                              """
                              package test;
                              @DeprecatedAnn(DeprecatedClass.class)
                              public class Use {}
                              """);

            List<String> log = new JavacTask(tb)
                    .outdir(classes)
                    .files(tb.findJavaFiles(src))
                    .options("-XDrawDiagnostics",
                             "-Xlint:deprecation")
                    .run(Expect.SUCCESS)
                    .writeAll()
                    .getOutputLines(OutputKind.DIRECT);

            if (!Objects.equals(log, List.of(tc.expectedOutput))) {
                error("Unexpected output, expected:\n" + Arrays.toString(tc.expectedOutput) +
                                         "\nactual:\n" + log);
            }
        }
    }

    record TestCase(String sw, String... expectedOutput) {}
}
