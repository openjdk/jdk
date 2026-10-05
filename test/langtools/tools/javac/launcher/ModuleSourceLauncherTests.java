/*
 * Copyright (c) 2023, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304400 8332226 8346778 8377010
 * @summary Test source launcher running Java programs contained in one module
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.launcher
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavaTask toolbox.JavacTask toolbox.ToolBox
 * @run junit ModuleSourceLauncherTests
 */

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import toolbox.JavaTask;
import toolbox.JavacTask;
import toolbox.Task;
import toolbox.ToolBox;

class ModuleSourceLauncherTests {
    private final ToolBox tb = new ToolBox();

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
        var badFolder = Files.createDirectories(base.resolve(".bad"));
        Files.writeString(badFolder.resolve("bad.txt"), "bad");

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
            var actual = reader.list().toList();
            assertLinesMatch(
                    """
                    .bad/
                    .bad/bad.txt
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
                    """.lines().toList(),
                    actual, "Actual lines -> " + actual);
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
        new JavacTask(tb)
                .options("--module-source-path", base.toString(),
                         "--module", "foo")
                .outdir(base)
                .run();

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
                    System.out.println("bar=" + Prog1.class.getModule().isNativeAccessEnabled());
                    System.out.println("foo=" + foo.Foo.class.getModule().isNativeAccessEnabled());
                  }
                }
                """);

        var run = new JavaTask(tb)
                .vmOptions("--module-path", base.toString(),
                           "--enable-native-access", "foo,bar,baz,ALL-UNNAMED")
                .className(base.resolve("bar/bar/Prog1.java").toString())
                .run();

        assertAll(
                () -> assertLinesMatch(
                      """
                      Foo[]
                      bar=true
                      foo=true
                      """.lines(), run.getOutputLines(Task.OutputKind.STDOUT).stream()),
                () -> assertLinesMatch(
                      """
                      WARNING: Unknown module: baz specified to --enable-native-access
                      """.lines(), run.getOutputLines(Task.OutputKind.STDERR).stream())
        );
    }

    @Test
    void testInheritedMainFromOtherPackage(@TempDir Path base) throws Exception {
        var appPackageDir = Files.createDirectories(base.resolve("app"));
        var mainFile = Files.writeString(appPackageDir.resolve("Main.java"),
                """
                package app;
                import lib.Base;
                public class Main extends Base {}
                """);
        var libPackageDir = Files.createDirectories(base.resolve("lib"));
        Files.writeString(libPackageDir.resolve("Base.java"),
                """
                package lib;
                public class Base {
                    protected void main() {
                        System.out.println("same module separate packages");
                    }
                }
                """);
        Files.writeString(base.resolve("module-info.java"),
                """
                module app {}
                """);

        var run = Run.of(mainFile);
        assertAll("Run -> " + run,
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertLinesMatch(
                        """
                        same module separate packages
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertNull(run.exception())
        );
    }

    @Test
    void testInheritedStaticMainFromOtherPackage(@TempDir Path base) throws Exception {
        var appPackageDir = Files.createDirectories(base.resolve("app"));
        var mainFile = Files.writeString(appPackageDir.resolve("Main.java"),
                """
                package app;
                import lib.Base;
                public class Main extends Base {}
                """);
        var libPackageDir = Files.createDirectories(base.resolve("lib"));
        Files.writeString(libPackageDir.resolve("Base.java"),
                """
                package lib;
                public class Base {
                    protected static void main() {
                        System.out.println("static same module separate packages");
                    }
                }
                """);
        Files.writeString(base.resolve("module-info.java"),
                """
                module app {}
                """);

        var run = Run.of(mainFile);
        assertAll("Run -> " + run,
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertLinesMatch(
                        """
                        static same module separate packages
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertNull(run.exception())
        );
    }

    @Test
    void testInheritedMainFromOtherModule(@TempDir Path base) throws Exception {
        var sourceRoot = base.resolve("src");
        var dependencyModuleDir = Files.createDirectories(sourceRoot.resolve("lib"));
        Files.writeString(dependencyModuleDir.resolve("module-info.java"),
                """
                module lib {
                    exports lib;
                }
                """);
        var libPackageDir = Files.createDirectories(dependencyModuleDir.resolve("lib"));
        Files.writeString(libPackageDir.resolve("Base.java"),
                """
                package lib;
                public class Base {
                    protected void main() {
                        System.out.println("main from across modules");
                    }
                }
                """);
        var compiledModule = Files.createDirectories(base.resolve("mods"));
        new JavacTask(tb)
                .options("--module-source-path", sourceRoot.toString(),
                         "--module", "lib")
                .outdir(compiledModule)
                .run();

        var appModuleDir = Files.createDirectories(sourceRoot.resolve("app"));
        var appPackageDir = Files.createDirectories(appModuleDir.resolve("app"));
        var mainFile = Files.writeString(appPackageDir.resolve("Main.java"),
                """
                package app;
                import lib.Base;
                public class Main extends Base {}
                """);
        Files.writeString(appModuleDir.resolve("module-info.java"),
                """
                module app {
                    requires lib;
                }
                """);

        var run = new JavaTask(tb)
                .vmOptions("--module-path", compiledModule.toString(),
                           "--add-modules", "lib")
                .className(mainFile.toString())
                .run();

        assertAll(
                () -> assertLinesMatch(
                        """
                        main from across modules
                        """.lines(), run.getOutputLines(Task.OutputKind.STDOUT).stream()),
                () -> assertTrue(run.getOutput(Task.OutputKind.STDERR).isEmpty(),
                                 run.getOutput(Task.OutputKind.STDERR))
        );
    }

    @Test
    void testInheritedStaticMainFromOtherModule(@TempDir Path base) throws Exception {
        var sourceRoot = base.resolve("src");
        var dependencyModuleDir = Files.createDirectories(sourceRoot.resolve("lib"));
        Files.writeString(dependencyModuleDir.resolve("module-info.java"),
                """
                module lib {
                    exports lib;
                }
                """);
        var libPackageDir = Files.createDirectories(dependencyModuleDir.resolve("lib"));
        Files.writeString(libPackageDir.resolve("Base.java"),
                """
                package lib;
                public class Base {
                    protected static void main() {
                        System.out.println("static main from across modules");
                    }
                }
                """);
        var compiledModule = Files.createDirectories(base.resolve("mods"));
        new JavacTask(tb)
                .options("--module-source-path", sourceRoot.toString(),
                         "--module", "lib")
                .outdir(compiledModule)
                .run();

        var appModuleDir = Files.createDirectories(sourceRoot.resolve("app"));
        var appPackageDir = Files.createDirectories(appModuleDir.resolve("app"));
        var mainFile = Files.writeString(appPackageDir.resolve("Main.java"),
                """
                package app;
                import lib.Base;
                public class Main extends Base {}
                """);
        Files.writeString(appModuleDir.resolve("module-info.java"),
                """
                module app {
                    requires lib;
                }
                """);

        var run = new JavaTask(tb)
                .vmOptions("--module-path", compiledModule.toString(),
                           "--add-modules", "lib")
                .className(mainFile.toString())
                .run();

        assertAll(
                () -> assertLinesMatch(
                        """
                        static main from across modules
                        """.lines(), run.getOutputLines(Task.OutputKind.STDOUT).stream()),
                () -> assertTrue(run.getOutput(Task.OutputKind.STDERR).isEmpty(),
                                 run.getOutput(Task.OutputKind.STDERR))
        );
    }

    @Test
    void testServiceLoading(@TempDir Path base) throws Exception {
        var packageFolder = Files.createDirectories(base.resolve("p"));
        var mainFile = Files.writeString(packageFolder.resolve("Main.java"),
                """
                package p;

                import java.util.ServiceLoader;
                import java.util.spi.ToolProvider;

                class Main {
                    public static void main(String... args) throws Exception {
                        System.out.println(Main.class + " in " + Main.class.getModule());
                        System.out.println("1");
                        System.out.println(Main.class.getResource("/p/Main.java"));
                        System.out.println(Main.class.getResource("/p/Main.class"));
                        System.out.println("2");
                        System.out.println(Main.class.getResource("/p/Tool.java"));
                        System.out.println(Main.class.getResource("/p/Tool.class"));
                        System.out.println("3");
                        System.out.println(ToolProvider.findFirst("p.Tool")); // empty due to SCL being used
                        System.out.println("4");
                        listToolProvidersIn(Main.class.getModule().getLayer());
                        System.out.println("5");
                        Class.forName("p.Tool"); // trigger compilation of "p/Tool.java"
                        System.out.println(Main.class.getResource("/p/Tool.class"));
                        System.out.println("6");
                        listToolProvidersIn(Main.class.getModule().getLayer());
                    }

                    static void listToolProvidersIn(ModuleLayer layer) {
                        try {
                            ServiceLoader.load(layer, ToolProvider.class).stream()
                                .filter(service -> service.type().getModule().getLayer() == layer)
                                .map(ServiceLoader.Provider::get)
                                .forEach(System.out::println);
                        } catch (java.util.ServiceConfigurationError error) {
                            error.printStackTrace(System.err);
                        }
                    }
                }
                """);
        Files.writeString(packageFolder.resolve("Tool.java"),
                """
                package p;

                import java.io.PrintWriter;
                import java.util.spi.ToolProvider;

                public record Tool(String name) implements ToolProvider {
                   public static void main(String... args) {
                     System.exit(new Tool().run(System.out, System.err, args));
                   }

                   public Tool() {
                     this(Tool.class.getName());
                   }

                   @Override
                   public int run(PrintWriter out, PrintWriter err, String... args) {
                     out.println(name + "/out");
                     err.println(name + "/err");
                     return 0;
                   }
                }
                """);
        Files.writeString(base.resolve("module-info.java"),
                """
                module m {
                    uses java.util.spi.ToolProvider;
                    provides java.util.spi.ToolProvider with p.Tool;
                }
                """);

        var run = Run.of(mainFile);
        assertAll("Run -> " + run,
                () -> assertLinesMatch(
                        """
                        class p.Main in module m
                        1
                        .*/p/Main.java
                        .*:p/Main.class
                        2
                        .*/p/Tool.java
                        null
                        3
                        Optional.empty
                        4
                        Tool[name=p.Tool]
                        5
                        .*:p/Tool.class
                        6
                        Tool[name=p.Tool]
                        """.lines(),
                        run.stdOut().lines()),
                () -> assertTrue(run.stdErr().isEmpty()),
                () -> assertNull(run.exception())
        );
    }
}
