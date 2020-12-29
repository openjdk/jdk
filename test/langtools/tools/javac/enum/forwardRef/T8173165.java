/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8173165
 * @summary javac should reject references to enum constants from nested classes inside instance initializers
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main T8173165
 */

import java.util.Arrays;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.TestRunner;
import toolbox.Task;

public class T8173165 extends TestRunner {
    ToolBox tb;

    public T8173165() {
        super(System.err);
        tb = new ToolBox();
    }

    @Override
    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    public static void main(String[] args) throws Exception {
        T8173165 t = new T8173165();
        t.runTests();
    }

    @Test
    public void testEnumConstantInInstanceVariableInitializer(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        String code = """
                import java.util.HashSet;
                public enum Test {
                    FOO;
                    public HashSet<Test> vals = new HashSet<Test>(){
                        Test test = FOO;
                        {
                            add(FOO);
                        }
                    };
                }""";
        tb.writeJavaFiles(src, code);
        List<String> output = new JavacTask(tb)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .options("-XDrawDiagnostics")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList(
                "Test.java:5:21: compiler.err.illegal.enum.static.ref",
                "Test.java:7:17: compiler.err.illegal.enum.static.ref",
                "2 errors");
        tb.checkEqual(expected, output);
    }

    @Test
    public void testEnumConstantInNestedEnum(Path base) throws Exception {
        Path src = base.resolve("src");
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        String code = """
                import java.util.HashSet;
                public enum OuterTest {
                    FOO;
                    enum InnerTest {
                        APPLE {
                            OuterTest test1 = FOO;
                        };
                        public HashSet<OuterTest> vals = new HashSet<OuterTest>(){
                            OuterTest test2 = FOO;
                            {
                                add(FOO);
                            }
                        };
                        OuterTest test3 = FOO;
                    }
                }""";
        tb.writeJavaFiles(src, code);
        new JavacTask(tb)
                .files(tb.findJavaFiles(src))
                .outdir(classes)
                .run()
                .writeAll();
    }
}
