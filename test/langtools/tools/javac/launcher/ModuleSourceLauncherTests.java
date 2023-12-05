/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Test source launcher running Java programs contained in one module
 * @modules jdk.compiler/com.sun.tools.javac.launcher
 * @run junit ModuleSourceLauncherTests
 */

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;

class ModuleSourceLauncherTests {
    @Test
    void testHelloModularWorld(@TempDir Path base) throws Exception {
        var packageFolder = Files.createDirectories(base.resolve("com/greetings"));
        var mainFile = Files.writeString(packageFolder.resolve("Main.java"),
                """
                package com.greetings;
                public class Main {
                  public static void main(String... args) {
                    System.out.println("Greetings!");
                    System.out.println("   module -> " + Main.class.getModule().getName());
                    System.out.println("  package -> " + Main.class.getPackageName());
                    System.out.println("    class -> " + Main.class.getSimpleName());
                  }
                }
                """);
        Files.writeString(base.resolve("module-info.java"),
                """
                module com.greetings {}
                """);

        var run = Run.of(mainFile);
        assertAll("Run -> " + run,
                () -> assertLinesMatch(
                        """
                        Greetings!
                           module -> com.greetings
                          package -> com.greetings
                            class -> Main
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception())
        );

        var module = run.result().programClass().getModule();
        assertEquals("com.greetings", module.getName());
        var reference = module.getLayer().configuration().findModule(module.getName()).orElseThrow().reference();
        try (var reader = reference.open()) {
            assertLinesMatch(
                    """
                    com/
                    com/greetings/
                    com/greetings/Main.class
                    com/greetings/Main.java
                    module-info.class
                    module-info.java
                    """.lines(),
                    reader.list());
        }
    }

    @Test
    void testTwoAndHalfPackages(@TempDir Path base) throws Exception {
        var fooFolder = Files.createDirectories(base.resolve("foo"));
        var program = Files.writeString(fooFolder.resolve("Main.java"),
                """
                package foo;
                public class Main {
                  public static void main(String... args) throws Exception {
                    var module = Main.class.getModule();
                    System.out.println("To the " + bar.Bar.class + " from " + module);
                    try (var stream = module.getResourceAsStream("baz/baz.txt")) {
                      System.out.println(new String(stream.readAllBytes()));
                    }
                  }
                }
                """);
        var barFolder = Files.createDirectories(base.resolve("bar"));
        Files.writeString(barFolder.resolve("Bar.java"), "package bar; public record Bar() {}");
        var bazFolder = Files.createDirectories(base.resolve("baz"));
        Files.writeString(bazFolder.resolve("baz.txt"), "baz");

        Files.writeString(base.resolve("module-info.java"),
                """
                module m {
                  exports foo;
                  exports bar;
                  opens baz;
                }
                """);

        var run = Run.of(program);
        var result = run.result();
        assertAll("Run -> " + run,
                () -> assertLinesMatch(
                        """
                        To the class bar.Bar from module m
                        baz
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception()),
                () -> assertEquals(Set.of("foo", "bar", "baz"), result.programClass().getModule().getPackages())
        );

        var module = run.result().programClass().getModule();
        assertEquals("m", module.getName());
        var reference = module.getLayer().configuration().findModule(module.getName()).orElseThrow().reference();
        try (var reader = reference.open()) {
            assertLinesMatch(
                    """
                    bar/
                    bar/Bar.class
                    bar/Bar.java
                    baz/
                    baz/baz.txt
                    foo/
                    foo/Main.class
                    foo/Main.java
                    module-info.class
                    module-info.java
                    """.lines(),
                    reader.list());
        }
    }

    @Test
    void testUserModuleOnModulePath(@TempDir Path base) throws Exception {
        Files.createDirectories(base.resolve("foo", "foo"));
        Files.writeString(base.resolve("foo", "module-info.java"),
                """
                module foo {
                  exports foo;
                }
                """);
        Files.writeString(base.resolve("foo", "foo", "Foo.java"),
                """
                package foo;
                public record Foo() {}
                """);
        var javac = ToolProvider.findFirst("javac").orElseThrow();
        javac.run(System.out, System.err, "--module-source-path", base.toString(), "--module", "foo", "-d", base.toString());

        Files.createDirectories(base.resolve("bar", "bar"));
        Files.writeString(base.resolve("bar", "module-info.java"),
                """
                module bar {
                  requires foo;
                }
                """);
        Files.writeString(base.resolve("bar", "bar","Prog1.java"),
                """
                package bar;
                class Prog1 {
                  public static void main(String... args) {
                    System.out.println(new foo.Foo());
                  }
                }
                """);

        var command = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-p", ".",
                "bar/bar/Prog1.java");
        var redirectedOut = base.resolve("out.redirected");
        var redirectedErr = base.resolve("err.redirected");
        var process = new ProcessBuilder(command)
                .directory(base.toFile())
                .redirectOutput(redirectedOut.toFile())
                .redirectError(redirectedErr.toFile())
                .start();
        var code = process.waitFor();
        var out = Files.readAllLines(redirectedOut);
        var err = Files.readAllLines(redirectedErr);

        assertAll(
                () -> assertEquals(0, code),
                () -> assertLinesMatch(
                      """
                      Foo[]
                      """.lines(), out.stream()),
                () -> assertTrue(err.isEmpty())
        );
    }
}
