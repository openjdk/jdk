/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177650
 * @summary Verify JShell tool code completion
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.jshell:+open
 *          jdk.jshell/jdk.internal.jshell.tool
 *          java.desktop
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build ReplToolTesting TestingInputStream Compiler
 * @run junit ToolCompletionTest
 */
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

public class ToolCompletionTest extends ReplToolTesting {

    private final Compiler compiler = new Compiler();
    private final Path outDir = Paths.get("tool_completion_test");

    @Test
    public void testClassPathOnCmdLineIndexing() {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup", "--class-path", compiler.getPath(p1.resolve(jarName)).toString()},
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testClassPathViaEnvIndexing() {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup"},
                (a) -> assertCommand(a, "/env --class-path " + compiler.getPath(p1.resolve(jarName)).toString(), null),
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testClassPathChangeIndexing() {
        //verify that changing the classpath has effect:
        Path dir1 = outDir.resolve("dir1");
        compiler.compile(dir1,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName1 = "test1.jar";
        compiler.jar(dir1, jarName1, "p1/p2/Test.class", "p1/p3/Test.class");

        Path dir2 = outDir.resolve("dir2");
        compiler.compile(dir2,
                """
                package p1.p5;
                public class Test {
                }
                """,
                """
                package p1.p6;
                public class Test {
                }
                """);
        String jarName2 = "test2.jar";
        compiler.jar(dir2, jarName2, "p1/p5/Test.class", "p1/p6/Test.class");

        test(false, new String[]{"--no-startup", "--class-path", compiler.getPath(dir1.resolve(jarName1)).toString()},
                (a) -> assertCommand(a, "1", null),
                (a) -> assertCommand(a, "/env --class-path " + compiler.getPath(dir2.resolve(jarName2)).toString(), null),
                (a) -> assertCompletions(a, "p1.", ".*p5\\..*p6\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testModulePathOnCmdLineIndexing() {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                module m {
                    exports p1.p2;
                    exports p1.p3;
                }
                """,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup", "--module-path", compiler.getPath(p1.resolve(jarName)).toString()},
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testModulePathOnCmdLineIndexing2() throws IOException {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                module m {
                    exports p1.p2;
                    exports p1.p3;
                }
                """,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        Path lib = outDir.resolve("lib");
        Files.createDirectories(lib);
        compiler.jar(p1, lib.resolve(jarName), "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup", "--module-path", lib.toString()},
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testUpgradeModulePathIndexing() {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                module m {
                    exports p1.p2;
                    exports p1.p3;
                }
                """,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup", "-C--upgrade-module-path", "-C" + compiler.getPath(p1.resolve(jarName)).toString()},
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }

    @Test
    public void testBootClassPathPrepend() {
        Path p1 = outDir.resolve("dir1");
        compiler.compile(p1,
                """
                package p1.p2;
                public class Test {
                }
                """,
                """
                package p1.p3;
                public class Test {
                }
                """);
        String jarName = "test.jar";
        compiler.jar(p1, jarName, "p1/p2/Test.class", "p1/p3/Test.class");

        test(false, new String[]{"--no-startup", "-C-Xbootclasspath/p:" + compiler.getPath(p1.resolve(jarName)).toString(), "-C--source=8"},
                (a) -> assertCompletions(a, "p1.", ".*p2\\..*p3\\..*"),
                 //cancel the input, so that JShell can be finished:
                (a) -> assertCommand(a, "\003", null)
                );
    }
}
