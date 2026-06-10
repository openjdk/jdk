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
 * @summary Test javac incremental compilation with modules
 * @run junit TestIncrementalComp
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TestIncrementalComp {

    static final ToolProvider JAVAC = ToolProvider.findFirst("javac")
            .orElseThrow();

    record TestCase(String srcDir, Map<Path, String> sources, Set<String> modules, String mainModule, Map<String, String> addReadsEdges) {
        TestCase(String srcDir, Map<Path, String> sources, Set<String> modules, String mainModule) {
            this(srcDir, sources, modules, mainModule, Map.of());
        }
    }

    @ParameterizedTest
    @MethodSource("cases")
    public void test(TestCase testCase) throws Throwable {
        Path workDir = Path.of(testCase.srcDir());
        // set up test sources
        Path localTestModules = workDir.resolve("test_modules");
        Path outDir = workDir.resolve("mods");
        for (Map.Entry<Path, String> sourceFile : testCase.sources().entrySet()) {
            Path filePath = localTestModules.resolve(sourceFile.getKey());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, sourceFile.getValue(), CREATE_NEW);
        }

        Path libPath = localTestModules.resolve(LIB_PATH);
        Files.createDirectories(libPath.getParent());
        Files.writeString(libPath, ALT_LIB_INT, CREATE_NEW);

        List<String> javacCommand = new ArrayList<>(List.of(
            "-d", outDir.toString(),
            "--module-source-path=" + localTestModules,
            "--module", String.join(",", testCase.modules())
        ));
        for (Map.Entry<String, String> addReads : testCase.addReadsEdges().entrySet()) {
            String reader = addReads.getKey();
            String read = addReads.getValue();
            javacCommand.add(String.format("--add-reads=%s=%s", reader, read));
        }
        // compile both modules
        compile(javacCommand);

        String mainClass = testCase.mainModule() + ".app.Main";
        invokeMainMethod(outDir, testCase.mainModule(), mainClass, testCase.addReadsEdges());

        // modify sources. Dep is not modified
        Files.writeString(libPath, ALT_LIB_LONG, TRUNCATE_EXISTING);

        // recompile. Any dependency on the changed file should be recompiled as well
        compile(javacCommand);

        // should work
        // if this fails because of incremental compilation issues, we can expect to see a NoSuchMethodError
        invokeMainMethod(outDir, testCase.mainModule(), mainClass, testCase.addReadsEdges());
    }

    private static void invokeMainMethod(Path modulePath, String moduleName, String mainClassName,
                                         Map<String, String> addReadsEdges)
            throws ReflectiveOperationException {
        // define module layer
        // note that we need to explicitly add any read module to the set of roots
        ModuleLayer boot = ModuleLayer.boot();
        Set<String> allRoots = Stream.concat(Stream.of(moduleName), addReadsEdges.values().stream())
                .collect(Collectors.toSet());
        Configuration config = boot.configuration()
                .resolve(ModuleFinder.of(modulePath), ModuleFinder.of(), allRoots);
        ModuleLayer.Controller controller = ModuleLayer.defineModulesWithOneLoader(
                config, List.of(boot), ClassLoader.getSystemClassLoader());

        // add extra reads edges
        for (Map.Entry<String, String> addReads : addReadsEdges.entrySet()) {
            Module reader = controller.layer().findModule(addReads.getKey()).orElseThrow();
            Module read = controller.layer().findModule(addReads.getValue()).orElseThrow();
            controller.addReads(reader, read);
        }

        // invoke main
        Class<?> main1 = controller.layer().findLoader(moduleName).loadClass(mainClassName);
        Method m = main1.getMethod("main", String[].class);
        m.invoke(null, new Object[]{ new String[0] });
    }

    private static void compile(List<String> args) {
        System.err.println("compile: " + args);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        int rc = JAVAC.run(pw, pw, args.toArray(String[]::new));
        pw.close();
        System.err.println(sw);
        assertEquals(0, rc);
    }

    private static final Path LIB_PATH = Path.of("org.moda/org/moda/lib/Lib.java");

    private static final String ALT_LIB_INT = """
            package org.moda.lib;
            public class Lib {
                public static int getVal() {
                    return 42;
                }
            }
            """;

    private static final String ALT_LIB_LONG = """
            package org.moda.lib;
            public class Lib {
                public static long getVal() {
                    return 42;
                }
            }
            """;

    static Stream<TestCase> cases() {
        return Stream.of(
                new TestCase("single", Map.of(
                    Path.of("org.moda/module-info.java"),
                    """
                    module org.moda {
                        // for reflective access
                        exports org.moda.app;
                    }
                    """,
                    Path.of("org.moda/org/moda/lib/Dep.java"),
                    """
                    package org.moda.lib;

                    public class Dep {
                        public static long getVal() {
                            return Lib.getVal();
                        }
                    }
                    """,
                    Path.of("org.moda/org/moda/app/Main.java"),
                    """
                    package org.moda.app;

                    import org.moda.lib.Dep;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println(Dep.getVal());
                        }
                    }
                    """
                ), Set.of("org.moda"), "org.moda"),
                new TestCase("multi", Map.of(
                    Path.of("org.moda/module-info.java"),
                    """
                    module org.moda {
                        exports org.moda.lib;
                    }
                    """,
                    Path.of("org.modb/module-info.java"),
                    """
                    module org.modb {
                        requires org.moda;

                        // for reflective access
                        exports org.modb.app;
                    }
                    """,
                    Path.of("org.modb/org/modb/app/Main.java"),
                    """
                    package org.modb.app;

                    import org.moda.lib.Lib;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println(Lib.getVal());
                        }
                    }
                    """
                ), Set.of("org.moda", "org.modb"), "org.modb"),
                new TestCase("transitive", Map.of(
                    Path.of("org.moda/module-info.java"),
                    """
                    module org.moda {
                        exports org.moda.lib;
                    }

                    """,
                    Path.of("org.modb/module-info.java"),
                    """
                    module org.modb {
                        // for org.modc
                        requires transitive org.moda;
                    }
                    """,
                    Path.of("org.modc/module-info.java"),
                    """
                    module org.modc {
                        requires org.modb;

                        // for reflective access
                        exports org.modc.app;
                    }
                    """,
                    Path.of("org.modc/org/modc/app/Main.java"),
                    """
                    package org.modc.app;

                    import org.moda.lib.Lib;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println(Lib.getVal());
                        }
                    }
                    """
                ), Set.of("org.moda", "org.modb", "org.modc"), "org.modc"),
                new TestCase("add_reads", Map.of(
                    Path.of("org.moda/module-info.java"),
                    """
                    module org.moda {
                        exports org.moda.lib;
                    }
                    """,
                    Path.of("org.modb/module-info.java"),
                    """
                    module org.modb {
                        // no explicit requires

                        // for reflective access
                        exports org.modb.app;
                    }
                    """,
                    Path.of("org.modb/org/modb/app/Main.java"),
                    """
                    package org.modb.app;

                    import org.moda.lib.Lib;

                    public class Main {
                        public static void main(String[] args) {
                            System.out.println(Lib.getVal());
                        }
                    }
                    """
                ), Set.of("org.moda", "org.modb"), "org.modb", Map.of("org.modb", "org.moda"))
            );
        }
    }
