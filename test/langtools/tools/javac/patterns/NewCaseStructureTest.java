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
 * @bug 8294942
 * @summary Check compilation outcomes for various combinations of case label element.
 * @library /tools/lib /tools/javac/lib
 * @modules
 *      jdk.compiler/com.sun.tools.javac.api
 *      jdk.compiler/com.sun.tools.javac.file
 *      jdk.compiler/com.sun.tools.javac.main
 *      jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @compile NewCaseStructureTest.java
 * @run main NewCaseStructureTest
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class NewCaseStructureTest extends TestRunner {

    private static final String JAVA_VERSION = System.getProperty("java.specification.version");

    ToolBox tb;

    public static void main(String... args) throws Exception {
        new NewCaseStructureTest().runTests();
    }

    NewCaseStructureTest() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testCorrectMultiLabelCaseStructure(Path base) throws Exception {
        for (String pattern : new String[] {"String s",
                                            "R(int i)",
                                            "null, default",
                                            "null",
                                            "1",
                                            "1, 2",
                                            "1, 2, 3"}) {
            for (String sep : new String[] {":", "->"}) {
                doTest(base,
                       """
                       package test;
                       public class Test {
                           private int test(${switchType} obj) {
                               return switch (obj) {
                                   case ${pattern} ${sep} { yield 0; }
                                   ${default}
                               };
                           }
                       }
                       record R(int i) {}
                       """.replace("${switchType}", pattern.contains("1") ? "Integer" : "Object")
                          .replace("${pattern}", pattern)
                          .replace("${sep}", sep)
                          .replace("${default}", pattern.contains("default") ? "" : "default " + sep + " { yield 1; }"),
                       false);
            }
        }
    }

    @Test
    public void testMalformedCaseStructure(Path base) throws Exception {
        for (String pattern : new String[] {"String s, Integer i",
                                            "String s, R(int i)",
                                            "E1(), E2()",
                                            "String s, null",
                                            "String s, default",
                                            "String s, null, default",
                                            "null, String s",
                                            "null, default, String s",
                                            "default, String s",
                                            "1, Integer i",
                                            "1, 2, 3, Integer i",
                                            "Integer i, 1, 2, 3",
                                            "1, null",
                                            "1, 2, 3, null",
                                            "null, 1, 2, 3",
                                            "default, null",
                                            "default"}) {
            for (String sep : new String[] {":", "->"}) {
                doTest(base,
                       """
                       package test;
                       public class Test {
                           private int test(${switchType} obj) {
                               return switch (obj) {
                                   case ${pattern} ${sep} { yield 0; }
                                   ${default}
                               };
                           }
                       }
                       record R(int i) {}
                       record E1() {}
                       record E2() {}
                       """.replace("${switchType}", pattern.contains("1") ? "Integer" : "Object")
                          .replace("${pattern}", pattern)
                          .replace("${sep}", sep)
                          .replace("${default}", pattern.contains("default") ? "" : "default " + sep + " { yield 1; }"),
                       true);
            }
        }
    }

    @Test
    public void testSwitchLabeledStatementGroups(Path base) throws Exception {
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case null:
                           case Object obj: System.err.println(); yield 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.flows.through.to.pattern",
               "1 error");
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case null: System.err.println();
                           case Object obj: System.err.println(); yield 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.flows.through.to.pattern",
               "1 error");
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case Object obj:
                           case null: System.err.println(); yield 0;
                       };
                   }
               }
               """,
               "Test.java:5:18: compiler.err.flows.through.from.pattern",
               "1 error");
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case Object obj: System.err.println();
                           case null: System.err.println();
                                      yield 0;
                       };
                   }
               }
               """);
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case String s: System.err.println();
                           case E1(): System.err.println(); yield 0;
                           default: yield 0;
                       };
                   }
               }
               record E1() {}
               """);
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Object o) {
                       return switch (o) {
                           case String s: System.err.println();
                           default: System.err.println(); yield 0;
                       };
                   }
               }
               """);
        doTest(base,
               """
               package test;
               public class Test {
                   private void test(Object o) {
                       switch (o) {
                           case String s:
                           case Integer i:
                           case Object obj:
                       }
                   }
               }
               """);
        doTest(base,
               """
               package test;
               public class Test {
                   private void test(Object o) {
                       switch (o) {
                           case Object obj: System.err.println();
                           case null: System.err.println(obj);
                       }
                   }
               }
               """,
               "Test.java:6:43: compiler.err.cant.resolve.location: kindname.variable, obj, , , (compiler.misc.location: kindname.class, test.Test, null)",
               "1 error");

        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case default -> 0;
                           case 0 -> 0;
                       };
                   }
               }
               """,
               "Test.java:5:18: compiler.err.default.label.not.allowed",
               "1 error");
    }

    @Test
    public void testDominance(Path base) throws Exception {
        //A case label with a case pattern p (guarded or unguarded) dominates another case label with a case constant c if p dominates c, which is defined as follows:
        // A type pattern that declares a pattern variable of type T dominates a constant c of a primitive type P if the wrapper class of P ([5.1.7]) is a subtype of the erasure of T.
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case Integer i when i > 0 -> 0;
                           case 0 -> 0;
                           case Integer i -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        // A type pattern that declares a pattern variable of type T dominates an enum constant c of type E if E is a subtype of the erasure of the type of T.
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(E o) {
                       return switch (o) {
                           case E e when e == E.A -> 0;
                           case B -> 0;
                           case E e -> 0;
                       };
                   }
               }
               enum E {A, B;}
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        //dtto for String:
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(String o) {
                       return switch (o) {
                           case String s when s.isEmpty() -> 0;
                           case "a" -> 0;
                           case String s -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        // A parenthesized pattern dominates a constant c if its contained pattern dominates c.
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case Integer i when i > 0 -> 0;
                           case 0 -> 0;
                           case Integer i -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        // A default label dominates a case label with a case pattern, and it also dominates a case label with a null case constant.
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           default -> 0;
                           case Integer i when i > 0 -> 0;
                           case Integer i when i > 0 -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case Integer i when i > 0 -> 0;
                           default -> 0;
                           case null -> 0;
                       };
                   }
               }
               """,
               "Test.java:7:18: compiler.err.pattern.dominated",
               "1 error");
        // case label with a default dominates all other switch labels.
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case null, default -> 0;
                           case Integer i when i > 0 -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");
        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           case null, default -> 0;
                           case 0 -> 0;
                           case 1 -> 0;
                       };
                   }
               }
               """,
               "Test.java:6:18: compiler.err.pattern.dominated",
               "1 error");

        doTest(base,
               """
               package test;
               public class Test {
                   private int test(Integer o) {
                       return switch (o) {
                           default -> 0;
                           case 0 -> 0;
                       };
                   }
               }
               """);
    }

    private void doTest(Path base, String testCode, boolean expectErrors) throws IOException {
        doTest(base, testCode, expectErrors, (String[]) null);
    }

    private void doTest(Path base, String testCode, String... output) throws IOException {
        doTest(base, testCode, output != null && output.length > 0, output);
    }

    private void doTest(Path base, String testCode, boolean expectErrors, String... output) throws IOException {
        Path current = base.resolve(".");
        Path src = current.resolve("src");

        tb.writeJavaFiles(src, testCode);

        Path classes = current.resolve("classes");

        Files.createDirectories(classes);

        List<String> actual = new JavacTask(tb)
            .options("-XDrawDiagnostics",
                     "-Xlint:-preview",
                     "-XDshould-stop.at=FLOW")
            .outdir(classes)
            .files(tb.findJavaFiles(src))
            .run(expectErrors? Task.Expect.FAIL : Task.Expect.SUCCESS)
            .writeAll()
            .getOutputLines(Task.OutputKind.DIRECT);

        if (output != null) {
            actual.remove("- compiler.note.preview.filename: Test.java, DEFAULT");
            actual.remove("- compiler.note.preview.recompile");
            actual.remove("");

            List<String> expected = List.of(output);

            if (!Objects.equals(expected, actual)) {
                throw new AssertionError("Unexpected output: " + actual);
            }
        }
    }
}
