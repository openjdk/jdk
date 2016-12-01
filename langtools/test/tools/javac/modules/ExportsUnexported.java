/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary tests for module declarations
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main ExportsUnexported
 */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import toolbox.JavacTask;
import toolbox.Task;

public class ExportsUnexported extends ModuleTestBase {

    public static void main(String... args) throws Exception {
        ExportsUnexported t = new ExportsUnexported();
        t.runTests();
    }

    @Test
    public void testLocations(Path base) throws Exception {
        String warningsTest = "package api;\n" +
                      "import impl.impl.*;\n" +
                      "@impl.impl^.DocAnn\n" +
                      "public abstract class Api<T extends impl.impl^.Cls&impl.impl^.Intf> extends impl.impl^.Cls implements impl.impl^.Intf, impl.impl^.NonDocAnn, impl.impl^.DocAnn {\n" +
                      "    public static <E extends impl.impl^.Cls&impl.impl^.Intf> impl.impl^.Cls m(impl.impl^.Intf i, impl.impl^.Cls c) throws impl.impl^.Exc { return null; }\n" +
                      "    public static impl.impl^.Cls f;\n" +
                      "}";
        String noWarningsTest = "package api;\n" +
                      "import impl.impl.*;\n" +
                      "@impl.impl.NonDocAnn\n" +
                      "public abstract class Api {\n" +
                      "    private static abstract class I <T extends impl.impl.Cls&impl.impl.Intf> extends impl.impl.Cls implements impl.impl.Intf, impl.impl.NonDocAnn, impl.impl.DocAnn {\n" +
                      "        public static abstract class II <T extends impl.impl.Cls&impl.impl.Intf> extends impl.impl.Cls implements impl.impl.Intf, impl.impl.NonDocAnn, impl.impl.DocAnn { }\n" +
                      "        public static <E extends impl.impl.Cls&impl.impl.Intf> impl.impl.Cls m(impl.impl.Intf i, impl.impl.Cls c) throws impl.impl.Exc { return null; }\n" +
                      "        public static impl.impl.Cls f;\n" +
                      "    }\n" +
                      "    private static <E extends impl.impl.Cls&impl.impl.Intf> impl.impl.Cls m(impl.impl.Intf i, impl.impl.Cls c) throws impl.impl.Exc { return null; }\n" +
                      "    private static impl.impl.Cls f1;\n" +
                      "    public static void m() { new impl.impl.Cls(); }\n" +
                      "    public static Object f2 = new impl.impl.Cls();\n" +
                      "}";
        for (String genericTest : new String[] {warningsTest, noWarningsTest}) {
            for (String test : new String[] {genericTest, genericTest.replaceAll("impl\\.impl\\^.([A-Za-z])", "^$1").replaceAll("impl\\.impl\\.([A-Za-z])", "$1")}) {
                System.err.println("testing: " + test);

                Path src = base.resolve("src");
                Path src_m1 = src.resolve("m1");
                StringBuilder testCode = new StringBuilder();
                List<String> expectedLog = new ArrayList<>();
                int line = 1;
                int col  = 1;

                for (int i = 0; i < test.length(); i++) {
                    char c = test.charAt(i);

                    if (c == '^') {
                        StringBuilder typeName = new StringBuilder();
                        for (int j = i + 1 + (test.charAt(i + 1) == '.' ? 1 : 0);
                             j < test.length() && Character.isJavaIdentifierPart(test.charAt(j));
                             j++) {
                            typeName.append(test.charAt(j));
                        }
                        String kindName;
                        switch (typeName.toString()) {
                            case "Exc": case "DocAnn": case "NonDocAnn":
                            case "Cls": kindName = "kindname.class"; break;
                            case "Intf": kindName = "kindname.interface"; break;
                            default:
                                throw new AssertionError(typeName.toString());
                        }
                        expectedLog.add("Api.java:" + line + ":" + col + ": compiler.warn.leaks.not.accessible.unexported: " + kindName + ", impl.impl." + typeName + ", m1");
                        continue;
                    }

                    if (c == '\n') {
                        line++;
                        col = 0;
                    }

                    testCode.append(c);
                    col++;
                }

                if (!expectedLog.isEmpty()) {
                    expectedLog.add("" + expectedLog.size() + " warning" + (expectedLog.size() == 1 ? "" : "s"));
                    expectedLog.add("- compiler.err.warnings.and.werror");
                    expectedLog.add("1 error");
                }

                Collections.sort(expectedLog);

                tb.writeJavaFiles(src_m1,
                                  "module m1 { exports api; }",
                                  testCode.toString(),
                                  "package impl.impl; public class Cls { }",
                                  "package impl.impl; public class Exc extends Exception { }",
                                  "package impl.impl; public interface Intf { }",
                                  "package impl.impl; @java.lang.annotation.Documented public @interface DocAnn { }",
                                  "package impl.impl; public @interface NonDocAnn { }");
                Path classes = base.resolve("classes");
                tb.createDirectories(classes);

                List<String> log = new JavacTask(tb)
                        .options("-XDrawDiagnostics",
                                 "-Werror",
                                 "--module-source-path", src.toString(),
                                 "-Xlint:exports")
                        .outdir(classes)
                        .files(findJavaFiles(src))
                        .run(expectedLog.isEmpty() ? Task.Expect.SUCCESS : Task.Expect.FAIL)
                        .writeAll()
                        .getOutputLines(Task.OutputKind.DIRECT);

                log = new ArrayList<>(log);
                Collections.sort(log);

                if (expectedLog.isEmpty() ? !log.equals(Arrays.asList("")) : !log.equals(expectedLog)) {
                    throw new Exception("expected output not found in: " + log + "; " + expectedLog);
                }
            }
        }
    }

    @Test
    public void testAccessibleToSpecificOrAll(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_lib1 = src.resolve("lib1");
        tb.writeJavaFiles(src_lib1,
                          "module lib1 { exports lib1; }",
                          "package lib1; public class Lib1 {}");
        Path src_lib2 = src.resolve("lib2");
        tb.writeJavaFiles(src_lib2,
                          "module lib2 { exports lib2; }",
                          "package lib2; public class Lib2 {}");
        Path src_api = src.resolve("api");
        tb.writeJavaFiles(src_api,
                          "module api {\n" +
                          "    exports api;\n" +
                          "    exports qapi1 to qual1;\n" +
                          "    exports qapi2 to qual1, qual2;\n" +
                          "    requires transitive lib1;\n" +
                          "    requires lib2;\n" +
                          "}\n",
                          "package api;\n" +
                          "public class Api {\n" +
                          "    public lib1.Lib1 lib1;\n" +
                          "    public lib2.Lib2 lib2;\n" +
                          "    public qapi1.QApi1 qapi1;\n" +
                          "    public impl.Impl impl;\n" +
                          "}",
                          "package qapi1;\n" +
                          "public class QApi1 {\n" +
                          "    public qapi2.QApi2 qapi2;\n" +
                          "}",
                          "package qapi2;\n" +
                          "public class QApi2 {\n" +
                          "    public qapi1.QApi1 qapi1;\n" +
                          "}",
                          "package impl;\n" +
                          "public class Impl {\n" +
                          "}");
        Path src_qual1 = src.resolve("qual1");
        tb.writeJavaFiles(src_qual1, "module qual1 { }");
        Path src_qual2 = src.resolve("qual2");
        tb.writeJavaFiles(src_qual2, "module qual2 { }");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "--module-source-path", src.toString(),
                         "-Xlint:exports")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
            "Api.java:4:16: compiler.warn.leaks.not.accessible.not.required.transitive: kindname.class, lib2.Lib2, lib2",
            "Api.java:5:17: compiler.warn.leaks.not.accessible.unexported.qualified: kindname.class, qapi1.QApi1, api",
            "Api.java:6:16: compiler.warn.leaks.not.accessible.unexported: kindname.class, impl.Impl, api",
            "- compiler.err.warnings.and.werror",
            "1 error",
            "3 warnings"
        );

        if (!log.equals(expected))
            throw new Exception("expected output not found");
    }

    @Test
    public void testNestedClasses(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_api = src.resolve("api");
        tb.writeJavaFiles(src_api,
                          "module api {\n" +
                          "    exports api;\n" +
                          "}\n",
                          "package api;\n" +
                          "import impl.Impl.Nested;\n" +
                          "public class Api {\n" +
                          "    public impl.Impl impl1;\n" +
                          "    public impl.Impl.Nested impl2;\n" +
                          "    public Nested impl3;\n" +
                          "}",
                          "package impl;\n" +
                          "public class Impl {\n" +
                          "    public static class Nested {\n" +
                          "    }\n" +
                          "}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "--module-source-path", src.toString(),
                         "-Xlint:exports")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
            "Api.java:4:16: compiler.warn.leaks.not.accessible.unexported: kindname.class, impl.Impl, api",
            "Api.java:5:16: compiler.warn.leaks.not.accessible.unexported: kindname.class, impl.Impl, api",
            "Api.java:6:12: compiler.warn.leaks.not.accessible.unexported: kindname.class, impl.Impl.Nested, api",
            "- compiler.err.warnings.and.werror",
            "1 error",
            "3 warnings"
        );

        if (!log.equals(expected))
            throw new Exception("expected output not found");
    }

    @Test
    public void testProtectedAndInaccessible(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_api = src.resolve("api");
        tb.writeJavaFiles(src_api,
                          "module api {\n" +
                          "    exports api;\n" +
                          "}\n",
                          "package api;\n" +
                          "public class Api extends PackagePrivateClass<PackagePrivateInterface> implements PackagePrivateInterface<PackagePrivateClass> {\n" +
                          "    protected PackagePrivateClass<?> f1;\n" +
                          "    protected PackagePrivateInterface<?> f2;\n" +
                          "    protected Inner f3;\n" +
                          "    protected PrivateInner f4;\n" +
                          "    protected impl.Impl f5;\n" +
                          "    public static class InnerClass extends PrivateInner {}\n" +
                          "    protected static class Inner {}\n" +
                          "    private static class PrivateInner {}\n" +
                          "}\n" +
                          "class PackagePrivateClass<T> {}\n" +
                          "interface PackagePrivateInterface<T> {}",
                          "package impl;\n" +
                          "public class Impl {\n" +
                          "}");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "--module-source-path", src.toString(),
                         "-Xlint:exports")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
            "Api.java:2:46: compiler.warn.leaks.not.accessible: kindname.interface, api.PackagePrivateInterface, api",
            "Api.java:2:106: compiler.warn.leaks.not.accessible: kindname.class, api.PackagePrivateClass, api",
            "Api.java:3:15: compiler.warn.leaks.not.accessible: kindname.class, api.PackagePrivateClass, api",
            "Api.java:4:15: compiler.warn.leaks.not.accessible: kindname.interface, api.PackagePrivateInterface, api",
            "Api.java:6:15: compiler.warn.leaks.not.accessible: kindname.class, api.Api.PrivateInner, api",
            "Api.java:7:19: compiler.warn.leaks.not.accessible.unexported: kindname.class, impl.Impl, api",
            "- compiler.err.warnings.and.werror",
            "1 error",
            "6 warnings"
        );

        if (!log.equals(expected))
            throw new Exception("expected output not found");
    }

    @Test
    public void testSuppressResetProperly(Path base) throws Exception {
        Path src = base.resolve("src");
        Path src_api = src.resolve("api");
        tb.writeJavaFiles(src_api,
                          "module api {\n" +
                          "    exports api;\n" +
                          "}\n",
                          "package api;\n" +
                          "public class Api {\n" +
                          "    @SuppressWarnings(\"exports\")\n" +
                          "    public PackagePrivateClass f1;\n" +
                          "    public PackagePrivateClass f2;\n" +
                          "    @SuppressWarnings(\"exports\")\n" +
                          "    public void t() {}\n" +
                          "    public PackagePrivateClass f3;\n" +
                          "    @SuppressWarnings(\"exports\")\n" +
                          "    public static class C {\n" +
                          "        public PackagePrivateClass f4;\n" +
                          "    }\n" +
                          "    public PackagePrivateClass f5;\n" +
                          "}\n" +
                          "class PackagePrivateClass<T> {}\n");
        Path classes = base.resolve("classes");
        tb.createDirectories(classes);

        List<String> log = new JavacTask(tb)
                .options("-XDrawDiagnostics",
                         "-Werror",
                         "--module-source-path", src.toString(),
                         "-Xlint:exports")
                .outdir(classes)
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        List<String> expected = Arrays.asList(
            "Api.java:5:12: compiler.warn.leaks.not.accessible: kindname.class, api.PackagePrivateClass, api",
            "Api.java:8:12: compiler.warn.leaks.not.accessible: kindname.class, api.PackagePrivateClass, api",
            "Api.java:13:12: compiler.warn.leaks.not.accessible: kindname.class, api.PackagePrivateClass, api",
            "- compiler.err.warnings.and.werror",
            "1 error",
            "3 warnings"
        );

        if (!log.equals(expected))
            throw new Exception("expected output not found");
    }

}
