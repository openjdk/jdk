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
 * @bug 8389737
 * @summary Verify super field access is checked properly.
 * @library /tools/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run junit SuperFieldAccess
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.ToolBox;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import toolbox.Task;

import static org.junit.jupiter.api.Assertions.*;

public class SuperFieldAccess {
    Path base;
    ToolBox tb = new ToolBox();

    @Test
    void testIllegalSuperAccessInEarlyContext() throws Exception {
        //cases illegal per JLS 15.11.2
        failingCase("""
                    class Super {
                        int field;
                    }

                    class Sub extends Super {

                        int field;

                        Sub(int ignored) {
                        }

                        Sub() {
                            int v = super.field;
                            this(v);
                        }
                    }
                    """,
                    "Super.java:13:22: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
        failingCase("""
                    class Super {
                        int field;
                    }

                    class T2 {
                        class Nested extends Super {
                            Nested() {
                                super.field = 0; //illegal per JLS 15.11.2?
                                super();
                            }
                        }
                    }
                    """,
                    "Super.java:8:18: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");

        failingCase("""
                    class NotInheritedTest2 {
                        private int field;
                        class Nested extends NotInheritedTest2 {
                            Nested() {
                                super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "NotInheritedTest2.java:5:18: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
        failingCase("""
                    class NotInheritedTest2 {
                        private int field;
                        class Nested extends NotInheritedTest2 {
                            private int field;
                            Nested() {
                                super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "NotInheritedTest2.java:6:18: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
        failingCase("""
                    class NotInheritedTest2 {
                        int field;
                        class Nested extends NotInheritedTest2 {
                            Nested() {
                                super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "NotInheritedTest2.java:5:18: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
        failingCase("""
                    class Super {
                        int field;
                    }
                    class T3 {
                        class Nested extends Super {
                            Nested() {
                                Nested.super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "Super.java:7:25: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
        failingCase("""
                    class Super {
                        int field;
                    }

                    class T1 extends Super {
                        T1() {
                            class Nested {
                                Nested() {
                                    super();
                                    T1.super.field = 0;
                                }
                            }
                            super();
                        }
                    }
                    """,
                    "Super.java:10:19: compiler.err.cant.ref.before.ctor.called: super",
                    "1 error");
        failingCase("""
                    class NotInheritedTest {
                        private int field;
                        class Intermediate extends NotInheritedTest {}
                        class Nested extends Intermediate {
                            Nested() {
                                super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "NotInheritedTest.java:6:18: compiler.err.report.access: field, private, NotInheritedTest",
                    "1 error");
        failingCase("""
                    class NotInheritedTest {
                        int field;
                        class Intermediate extends NotInheritedTest {}
                        class Nested extends Intermediate {
                            int field;
                            Nested() {
                                super.field = 0;
                                super();
                            }
                        }
                    }
                    """,
                    "NotInheritedTest.java:7:18: compiler.err.cant.ref.before.ctor.called: field",
                    "1 error");
    }

    private void failingCase(String code, String... errors) throws IOException {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        List<String> log = new JavacTask(tb)
                .outdir(classes)
                .options("-XDrawDiagnostics")
                .sources(code)
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = List.of(errors);

        assertEquals(expected, log);
    }

    @Test
    void testLegalSuperAccessInEarlyContext() throws Exception {
        passingCase("""
                    class Test {
                    class Super {
                        int field1;
                        int field2;
                    }

                    class T1 extends Super {
                        class Nested {
                            Nested() {
                                T1.super.field1 = 1;
                                field2 = 2;
                                super();
                            }
                        }
                    }
                    void main() {
                        T1 t1 = new T1();
                        t1.new Nested();
                        System.out.println(t1.field1);
                        System.out.println(t1.field2);
                    }
                    }
                    """,
                    "1",
                    "2");
    }

    private void passingCase(String code, String... output) throws IOException {
        Path classes = base.resolve("classes");
        Files.createDirectories(classes);
        new JavacTask(tb)
            .outdir(classes)
            .options("-XDrawDiagnostics")
            .sources(code)
            .run(Task.Expect.SUCCESS)
            .writeAll();

        List<String> log = new JavaTask(tb)
            .classpath(classes.toString())
            .className("Test")
            .run()
            .writeAll()
            .getOutputLines(Task.OutputKind.STDOUT);

        List<String> expected = List.of(output);

        assertEquals(expected, log);
    }

    @BeforeEach
    public void setUp(TestInfo info) {
        base = Paths.get(".")
                    .resolve(info.getTestMethod()
                                 .orElseThrow()
                                 .getName());
    }
}
