/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8174243
 * @summary incorrect error message for nested service provider
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask ModuleTestBase
 * @run main WrongErrorMessageForNestedServiceProviderTest
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class WrongErrorMessageForNestedServiceProviderTest extends ModuleTestBase {
    public static void main(String... args) throws Exception {
        WrongErrorMessageForNestedServiceProviderTest t = new WrongErrorMessageForNestedServiceProviderTest();
        t.runTests();
    }

    private static final String twoServicesModuleDef =
            "module m {\n" +
            "    exports example;\n" +
            "    provides example.SomeService with example.ServiceImpl;\n" +
            "    provides example.SomeServiceOuter with example.Outer.ServiceImplOuter;\n" +
            "}";

    private static final String someServiceInt =
            "package example;\n" +
            "public interface SomeService {\n" +
            "    public void foo();\n" +
            "}";

    private static final String someServiceIntOuter =
            "package example;\n" +
            "public interface SomeServiceOuter {\n" +
            "    public void foo();\n" +
            "}";

    @Test
    public void testPositive(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                twoServicesModuleDef,
                someServiceInt,
                someServiceIntOuter,
                "package example;\n" +
                "public class ServiceImpl implements example.SomeService {\n" +
                "    public ServiceImpl() {}\n" +
                "    public void foo() {}\n" +
                "}",

                "package example;\n" +
                "class Outer {\n" +
                "    public static class ServiceImplOuter implements example.SomeServiceOuter {\n" +
                "        public ServiceImplOuter() {}\n" +
                "        public void foo() {}\n" +
                "    }\n" +
                "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        List<String> output = new JavacTask(tb)
                .outdir(classes)
                .options("-Werror", "-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList("");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testNegative(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                twoServicesModuleDef,
                someServiceInt,
                someServiceIntOuter,

                "package example;\n" +
                "class ServiceImpl implements example.SomeService {\n" +
                "    public ServiceImpl() {}\n" +
                "    public void foo() {}\n" +
                "}",

                "package example;\n" +
                "class Outer {\n" +
                "    static class ServiceImplOuter implements example.SomeServiceOuter {\n" +
                "        public ServiceImplOuter() {}\n" +
                "        public void foo() {}\n" +
                "    }\n" +
                "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        List<String> output = new JavacTask(tb)
                .outdir(classes)
                .options("-Werror", "-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList(
                "module-info.java:3:46: compiler.err.not.def.public: example.ServiceImpl, example",
                "module-info.java:4:57: compiler.err.not.def.public: example.Outer.ServiceImplOuter, example.Outer",
                "2 errors");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }

    @Test
    public void testClassWrappedByPrivateClass(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "module m {\n" +
                "    exports example;\n" +
                "    provides example.SomeServiceOuter with example.Outer1.Outer2.ServiceImplOuter;\n" +
                "}",

                someServiceIntOuter,

                "package example;\n" +
                "class Outer1 {\n" +
                "    static private class Outer2 {\n" +
                "        public static class ServiceImplOuter implements example.SomeServiceOuter {\n" +
                "            public ServiceImplOuter() {}\n" +
                "            public void foo() {}\n" +
                "        }\n" +
                "    }\n" +
                "}");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);

        List<String> output = new JavacTask(tb)
                .outdir(classes)
                .options("-Werror", "-XDrawDiagnostics")
                .files(findJavaFiles(src))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList("");
        if (!output.containsAll(expected)) {
            throw new Exception("Expected output not found");
        }
    }
}
