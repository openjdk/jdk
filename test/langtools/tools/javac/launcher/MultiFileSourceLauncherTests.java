/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304400
 * @summary Test source launcher running Java programs spanning multiple files
 * @modules jdk.compiler/com.sun.tools.javac.launcher
 * @run junit MultiFileSourceLauncherTests
 */

import static org.junit.jupiter.api.Assertions.*;

import com.sun.tools.javac.launcher.Fault;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.*;

class MultiFileSourceLauncherTests {
    @Test
    void testHelloWorldInTwoCompilationUnits(@TempDir Path base) throws Exception {
        var hello = Files.writeString(base.resolve("Hello.java"),
                    """
                    public class Hello {
                      public static void main(String... args) {
                        System.out.println("Hello " + new World("Terra"));
                        System.out.println(Hello.class.getResource("Hello.java"));
                        System.out.println(Hello.class.getResource("World.java"));
                      }
                    }
                    """);
        Files.writeString(base.resolve("World.java"),
                    """
                    record World(String name) {}
                    """);

        var run = Run.of(hello);
        assertLinesMatch(
                """
                Hello World[name=Terra]
                \\Qfile:\\E.+\\QHello.java\\E
                \\Qfile:\\E.+\\QWorld.java\\E
                """.lines(),
                run.stdOut().lines());
        assertTrue(run.stdErr().isEmpty(), run.toString());
        assertNull(run.exception(), run.toString());
    }

    @Test
    void testLoadingOfEnclosedTypes(@TempDir Path base) throws Exception {
        var hello = Files.writeString(base.resolve("Hello.java"),
                    """
                    public class Hello {
                      public static void main(String... args) throws Exception {
                        System.out.println(Class.forName("World$Core"));
                        System.out.println(Class.forName("p.q.Unit$Fir$t"));
                        System.out.println(Class.forName("p.q.Unit$123$Fir$t$$econd"));
                        System.out.println(Class.forName("$.$.$"));
                      }
                    }
                    """);
        Files.writeString(base.resolve("World.java"),
                    """
                    record World(String name) {
                      record Core() {}
                    }
                    """);
        var pq = Files.createDirectories(base.resolve("p/q"));
        Files.writeString(pq.resolve("Unit.java"),
                    """
                    package p.q;
                    record Unit() {
                      record Fir$t() {
                        record $econd() {}
                      }
                    }
                    """);
        Files.writeString(pq.resolve("Unit$123.java"),
                    """
                    package p.q;
                    record Unit$123() {
                      record Fir$t() {
                        record $econd() {}
                      }
                    }
                    """);
        var $$ = Files.createDirectories(base.resolve("$/$"));
        Files.writeString($$.resolve("$.java"),
                    """
                    package $.$;
                    record $($ $) {}
                    """);

        var run = Run.of(hello);
        assertAll("Run -> " + run,
                () -> assertLinesMatch(
                        """
                        class World$Core
                        class p.q.Unit$Fir$t
                        class p.q.Unit$123$Fir$t$$econd
                        class $.$.$
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception())
        );
    }

    @Test
    void testMultiplePackages(@TempDir Path base) throws Exception {
        var packageA = Files.createDirectories(base.resolve("a"));
        var hello = Files.writeString(packageA.resolve("Hello.java"),
                    """
                    package a;
                    import b.World;
                    public class Hello {
                      public static void main(String... args) {
                        System.out.println("Hello " + new World("in package b"));
                      }
                    }
                    """);
        var packageB = Files.createDirectories(base.resolve("b"));
        Files.writeString(packageB.resolve("World.java"),
                    """
                    package b;
                    public record World(String name) {}
                    """);

        var run = Run.of(hello);
        assertLinesMatch(
                """
                Hello World[name=in package b]
                """.lines(),
                run.stdOut().lines());
        assertTrue(run.stdErr().isEmpty(), run.toString());
        assertNull(run.exception(), run.toString());
    }

    @Test
    void testMissingSecondUnit(@TempDir Path base) throws Exception {
        var program = Files.writeString(base.resolve("Program.java"),
                    """
                    public class Program {
                      public static void main(String... args) {
                        System.out.println("Hello " + new MissingSecondUnit());
                      }
                    }
                    """);

        var run = Run.of(program);
        assertTrue(run.stdOut().isEmpty(), run.toString());
        assertLinesMatch(
                """
                %s:3: error: cannot find symbol
                    System.out.println("Hello " + new MissingSecondUnit());
                                                      ^
                  symbol:   class MissingSecondUnit
                  location: class Program
                1 error
                """.formatted(program.toString())
                        .lines(),
                run.stdErr().lines(),
                run.toString());
        assertTrue(run.exception() instanceof Fault);
    }

    @Test
    void testSecondUnitWithSyntaxError(@TempDir Path base) throws Exception {
        var program = Files.writeString(base.resolve("Program.java"),
                    """
                    public class Program {
                      public static void main(String... args) {
                        System.out.println("Hello " + new BrokenSecondUnit());
                      }
                    }
                    """);
        var broken = Files.writeString(base.resolve("BrokenSecondUnit.java"),
                    """
                    record BrokenSecondUnit {}
                    """);

        var run = Run.of(program);
        assertTrue(run.stdOut().isEmpty(), run.toString());
        assertLinesMatch(
                """
                %s:1: error: '(' expected
                >> MORE LINES >>
                """.formatted(broken.toString())
                   .lines(),
                run.stdErr().lines(),
                run.toString());
        assertTrue(run.exception() instanceof Fault);
    }

    @Test
    void onlyJavaFilesReferencedByTheProgramAreCompiled(@TempDir Path base) throws Exception {
        var prog = Files.writeString(base.resolve("Prog.java"),
                    """
                    class Prog {
                      public static void main(String... args) {
                        Helper.run();
                      }
                    }
                    """);
        Files.writeString(base.resolve("Helper.java"),
                    """
                    class Helper {
                      static void run() {
                        System.out.println("Hello!");
                      }
                    }
                    """);

        var old = Files.writeString(base.resolve("OldProg.java"),
                    """
                    class OldProg {
                      public static void main(String... args) {
                        Helper.run()
                      }
                    }
                    """);

        var run = Run.of(prog);
        assertAll("Run := " + run,
                () -> assertLinesMatch(
                        """
                        Hello!
                        """.lines(), run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()));

        var fail = Run.of(old);
        assertAll("Run := " + fail,
                () -> assertTrue(fail.stdOut().isEmpty()),
                () -> assertLinesMatch(
                        """
                        %s:3: error: ';' expected
                            Helper.run()
                                        ^
                        1 error
                        """.formatted(old).lines(), fail.stdErr().lines()),
                () -> assertNotNull(fail.exception()));
    }

    @Test
    void classesDeclaredInSameFileArePreferredToClassesInOtherFiles(@TempDir Path base) throws Exception {
        var prog = Files.writeString(base.resolve("Prog.java"),
                """
                class Helper {
                  static void run() {
                    System.out.println("Same file.");
                  }
                }
                public class Prog {
                  public static void main(String... args) {
                    Helper.run();
                  }
                }
                """);
        Files.writeString(base.resolve("Helper.java"),
                """
                class Helper {
                  static void run() {
                    System.out.println("Other file.");
                  }
                }
                """);

        var run = Run.of(prog);
        assertAll("Run := " + run,
                () -> assertLinesMatch(
                        """
                        Same file.
                        """.lines(), run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()));
    }

    @Test
    void duplicateDeclarationOfClassFails(@TempDir Path base) throws Exception {
        var prog = Files.writeString(base.resolve("Prog.java"),
                    """
                    class Prog {
                      public static void main(String... args) {
                        Helper.run();
                        Aux.cleanup();
                      }
                    }
                    class Aux {
                      static void cleanup() {}
                    }
                    """);
        var helper = Files.writeString(base.resolve("Helper.java"),
                    """
                    class Helper {
                      static void run() {}
                    }
                    class Aux {
                      static void cleanup() {}
                    }
                    """);


        var fail = Run.of(prog);
        assertAll("Run := " + fail,
                () -> assertTrue(fail.stdOut().isEmpty()),
                () -> assertLinesMatch(
                        """
                        %s:4: error: duplicate class: Aux
                        class Aux {
                        ^
                        1 error
                        """.formatted(helper).lines(), fail.stdErr().lines()),
                () -> assertNotNull(fail.exception()));
    }
}
