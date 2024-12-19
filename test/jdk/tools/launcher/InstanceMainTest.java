/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @test
 * @bug 8329420
 * @summary test execution priority and behavior of main methods
 * @run main InstanceMainTest
 */
public class InstanceMainTest extends TestHelper {

    private static final String[] SOURCES = new String[] {
            // static dominating with args
            """
            class MainClass {
                static void main() {
                    throw new AssertionError();
                }
                static void main(String[] args) {
                }
            }
            """,

            // instance dominating static
            """
            class MainClass {
                void main(String[] args) {
                }
                static void main() {
                    throw new AssertionError();
                }
            }
            """,

            // instance dominating with args
            """
            class MainClass {
                void main() {
                    throw new AssertionError();
                }
                void main(String[] args) {
                }
            }
            """,

            // instance no args
            """
            class MainClass {
                void main() {
                }
            }
            """,

            // unnamed class static dominating with args
            """
            static void main() {
                throw new AssertionError();
            }
            static void main(String[] args) {
            }
            """,

            // main with args dominating main without args
            """
            void main(String[] args) {
            }
            static void main() {
                throw new AssertionError();
            }
            """,

            // unnamed class instance dominating with args
            """
            void main() {
                throw new AssertionError();
            }
            void main(String[] args) {
            }
            """,

            // unnamed class instance main no args
            """
            void main() {
            }
            """,

            // instance main with args dominating super
            """
            class MainClass extends SuperClass {
                void main() {
                    throw new AssertionError();
                }
            }
            class SuperClass {
                void main(String[] args) {
                }
            }
            """,

            // super instance main with args dominating
            """
            public class MainClass extends Super {
            }

            class Super {
                public void main(String... args) {
                }

                public void main() {
                    throw new AssertionError();
                }
            }
            """,

            // ignore super instance main
            """
            public class MainClass extends Super {
                public static void main(String... args) {
                }
            }

            class Super {
                public static void main(String... args) {
                    throw new AssertionError();
                }
            }
            """,

            // enum main
            """
            enum MainClass {
                A;

                public static void main() {
                }
            }
            """,

            // record main
            """
            record MainClass() {
                 static void main() {
                     System.out.println("Done!");
                 }
            }
            """,
            // interface main
            """
            interface MainClass {
                 static void main() {
                     System.out.println("Done!");
                 }
            }
            """
    };

    private static void testMethodOrder() throws Exception {
        for (String source : SOURCES) {
            performTest(source, true, tr -> {
                if (!tr.isOK()) {
                    System.err.println(source);
                    System.err.println(tr);
                    throw new AssertionError();
                }
            });
        }
    }

    record TestCase(String sourceCode, boolean enablePreview, List<String> expectedOutput) {

        public TestCase(String sourceCode, List<String> expectedOutput) {
            this(sourceCode, true, expectedOutput);
        }

    }

    private static final TestCase[] EXECUTION_ORDER = new TestCase[] {
            new TestCase("""
                     public class MainClass {
                         public MainClass() {
                             System.out.println("Constructor called!");
                         }
                         public static void main() {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    List.of("main called!")),
            new TestCase("""
                     public class MainClass {
                         public MainClass() {
                             System.out.println("Constructor called!");
                         }
                         public void main() {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    List.of("Constructor called!", "main called!"))
    };

    private static void testExecutionOrder() throws Exception {
        for (TestCase testCase : EXECUTION_ORDER) {
            performTest(testCase.sourceCode, testCase.enablePreview(), tr -> {
                if (!Objects.equals(testCase.expectedOutput, tr.testOutput)) {
                    throw new AssertionError("Unexpected output, " +
                            "expected: " + testCase.expectedOutput +
                            ", actual: " + tr.testOutput);
                }
            });
        }
    }

    private static final TestCase[] EXECUTION_ERRORS = new TestCase[] {
            new TestCase("""
                     public class MainClass {
                         public MainClass() {
                             System.out.println("Constructor called!");
                             if (true) throw new Error();
                         }
                         public void main(String... args) {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    List.of("Constructor called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<init>(MainClass.java:4)")),
            new TestCase("""
                     public class MainClass {
                         public MainClass() {
                             System.out.println("Constructor called!");
                             if (true) throw new Error();
                         }
                         public void main() {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    List.of("Constructor called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<init>(MainClass.java:4)")),
            new TestCase("""
                     public class MainClass {
                         static int idx;
                         public MainClass() {
                             System.out.println("Constructor called!");
                             if (idx++ == 0) throw new Error();
                         }
                         public void main(String... args) {
                             System.out.println("main called!");
                         }
                         public void main() {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    List.of("Constructor called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<init>(MainClass.java:5)")),
            new TestCase("""
                     public class MainClass {
                         static {
                             System.out.println("static init called!");
                             if (true) throw new Error();
                         }
                         public static void main(String... args) {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    false,
                    List.of("static init called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<clinit>(MainClass.java:4)")),
            new TestCase("""
                     public class MainClass {
                         static {
                             System.out.println("static init called!");
                             if (true) throw new Error();
                         }
                         public static void main(String... args) {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    true,
                    List.of("static init called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<clinit>(MainClass.java:4)")),
            new TestCase("""
                     public class MainClass {
                         static {
                             System.out.println("static init called!");
                             if (true) throw new Error();
                         }
                         public void main(String... args) {
                             System.out.println("main called!");
                         }
                     }
                     """,
                    true,
                    List.of("static init called!",
                            "Exception in thread \"main\" java.lang.Error",
                            "\tat MainClass.<clinit>(MainClass.java:4)")),
    };

    private static void testExecutionErrors() throws Exception {
        for (TestCase testCase : EXECUTION_ERRORS) {
            performTest(testCase.sourceCode, testCase.enablePreview(), tr -> {
                for (int i = 0; i < testCase.expectedOutput.size(); i++) {
                    if (i >= tr.testOutput.size() ||
                            !Objects.equals(testCase.expectedOutput.get(i),
                                    tr.testOutput.get(i))) {
                        throw new AssertionError("Unexpected output, " +
                                "expected: " + testCase.expectedOutput +
                                ", actual: " + tr.testOutput +
                                ", failed comparison at index: " + i);
                    }
                }
            });
        }
    }

    private static void performTest(String source, boolean enablePreview, Consumer<TestResult> validator) throws Exception {
        Path mainClass = Path.of("MainClass.java");
        Files.writeString(mainClass, source);
        var version = System.getProperty("java.specification.version");
        var previewRuntime = enablePreview ? "--enable-preview" : "-DtestNoPreview";
        var previewCompile = enablePreview ? "--enable-preview" : "-XDtestNoPreview";
        var trSource = doExec(javaCmd, previewRuntime, "--source", version, "MainClass.java");
        validator.accept(trSource);
        compile(previewCompile, "--source", version, "MainClass.java");
        String cp = mainClass.toAbsolutePath().getParent().toString();
        var trCompile = doExec(javaCmd, previewRuntime, "--class-path", cp, "MainClass");
        validator.accept(trCompile);
    }

    public static void main(String... args) throws Exception {
        testMethodOrder();
        testExecutionOrder();
        testExecutionErrors();
    }
}
