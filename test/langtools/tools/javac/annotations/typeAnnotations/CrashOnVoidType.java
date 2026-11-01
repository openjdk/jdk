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

/*
 * @test
 * @bug 8384235
 * @summary Check that javac does not creash when annotating void.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit ${test.main.class}
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

public class CrashOnVoidType {

    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testVoidParam() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                             public void op(@Ann void p) {}
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:5:25: compiler.err.void.not.allowed.here",
                "1 error"));
    }

    @Test
    void testVoidLocalVarType() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                             public void op() {
                                 @Ann void l;
                             }
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:6:14: compiler.err.void.not.allowed.here",
                "1 error"));
    }

    @Test
    void testVoidRecordComponent() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public record R(@Ann void v) {}

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "R.java:4:22: compiler.err.void.not.allowed.here",
                "1 error"));
    }

    @Test
    void testVoidExceptionParam() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                             public void op() {
                                 try {
                                 } catch (@Ann void e) {}
                             }
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:7:23: compiler.err.void.not.allowed.here",
                "1 error"));
    }

    @Test
    void testVoidUnion() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                             public void op() {
                                 try {
                                 } catch (Exception | @Ann void e) {}
                             }
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:7:30: compiler.err.illegal.start.of.type",
                "1 error"));
    }

    @Test
    void testVoidTypeParam() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         import java.util.List;

                         public class Test {
                             public List<@Ann void> l;
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:6:17: compiler.err.illegal.start.of.type",
                "Test.java:6:22: compiler.err.void.not.allowed.here",
                "2 errors"));
    }

    @Test
    void testVoidWildcardBound() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;
                         import java.util.List;

                         public class Test {
                             public List<? extends @Ann void> l;
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:6:17: compiler.err.illegal.start.of.type",
                "Test.java:6:32: compiler.err.void.not.allowed.here",
                "2 errors"));
    }

    @Test
    void testVoidTypeParamBound() throws Exception {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> out = new JavacTask(tb)
                .options("-d", classes.toString(), "-XDrawDiagnostics")
                .sources("""
                         import java.lang.annotation.ElementType;
                         import java.lang.annotation.Target;

                         public class Test {
                             public <T extends @Ann void> void op() {}
                         }

                         @Target(ElementType.TYPE_USE)
                         @interface Ann {}
                         """)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        tb.checkEqual(out, List.of(
                "Test.java:5:23: compiler.err.illegal.start.of.type",
                "Test.java:5:28: compiler.err.void.not.allowed.here",
                "2 errors"));
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
