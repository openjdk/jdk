/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests jdeps -m and -mp options on named modules and unnamed modules
 * @library ..
 * @build CompilerUtils
 * @modules jdk.jdeps/com.sun.tools.jdeps
 * @run testng ModuleTest
 */

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleDescriptor.Requires.Modifier;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import java.util.stream.Collectors;

import org.testng.annotations.DataProvider;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class ModuleTest {
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String TEST_CLASSES = System.getProperty("test.classes");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");

    // the names of the modules in this test
    private static final String UNSUPPORTED = "unsupported";
    private static String[] modules = new String[] {"m1", "m2", "m3", "m4", UNSUPPORTED};
    /**
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Exception {
        CompilerUtils.cleanDir(MODS_DIR);
        assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, UNSUPPORTED,
                                               "-XaddExports:java.base/jdk.internal.perf=" + UNSUPPORTED));
        // m4 is not referenced
        Arrays.asList("m1", "m2", "m3", "m4")
              .forEach(mn -> assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, mn)));
    }

    @DataProvider(name = "modules")
    public Object[][] expected() {
        return new Object[][]{
                { "m3", new Data("m3").requiresPublic("java.sql")
                                      .requiresPublic("m2")
                                      .requires("java.logging")
                                      .requiresPublic("m1")
                                      .reference("p3", "java.lang", "java.base")
                                      .reference("p3", "java.sql", "java.sql")
                                      .reference("p3", "java.util.logging", "java.logging")
                                      .reference("p3", "p1", "m1")
                                      .reference("p3", "p2", "m2")
                                      .qualified("p3", "p2.internal", "m2")
                },
                { "m2", new Data("m2").requiresPublic("m1")
                                      .reference("p2", "java.lang", "java.base")
                                      .reference("p2", "p1", "m1")
                                      .reference("p2.internal", "java.lang", "java.base")
                                      .reference("p2.internal", "java.io", "java.base")
                },
                { "m1", new Data("m1").requires("unsupported")
                                      .reference("p1", "java.lang", "java.base")
                                      .reference("p1.internal", "java.lang", "java.base")
                                      .reference("p1.internal", "p1", "m1")
                                      .reference("p1.internal", "q", "unsupported")
                },
                { "unsupported", new Data("unsupported")
                                      .reference("q", "java.lang", "java.base")
                                      .jdkInternal("q", "jdk.internal.perf", "(java.base)")
                },
        };
    }

    @Test(dataProvider = "modules")
    public void modularTest(String name, Data data) {
        // print only the specified module
        String excludes = Arrays.stream(modules)
                                .filter(mn -> !mn.endsWith(name))
                                .collect(Collectors.joining(","));
        String[] result = jdeps("-exclude-modules", excludes,
                                "-mp", MODS_DIR.toString(),
                                "-m", name);
        assertTrue(data.check(result));
    }

    @DataProvider(name = "unnamed")
    public Object[][] unnamed() {
        return new Object[][]{
                { "m3", new Data("m3", false)
                            .depends("java.sql")
                            .depends("java.logging")
                            .depends("m1")
                            .depends("m2")
                            .reference("p3", "java.lang", "java.base")
                            .reference("p3", "java.sql", "java.sql")
                            .reference("p3", "java.util.logging", "java.logging")
                            .reference("p3", "p1", "m1")
                            .reference("p3", "p2", "m2")
                            .internal("p3", "p2.internal", "m2")
                },
                { "unsupported", new Data("unsupported", false)
                            .reference("q", "java.lang", "java.base")
                            .jdkInternal("q", "jdk.internal.perf", "(java.base)")
                },
        };
    }

    @Test(dataProvider = "unnamed")
    public void unnamedTest(String name, Data data) {
        String[] result = jdeps("-mp", MODS_DIR.toString(), MODS_DIR.resolve(name).toString());
        assertTrue(data.check(result));
    }

    /*
     * Runs jdeps with the given arguments
     */
    public static String[] jdeps(String... args) {
        String lineSep =     System.getProperty("line.separator");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        System.err.println("jdeps " + Arrays.toString(args));
        int rc = com.sun.tools.jdeps.Main.run(args, pw);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        if (rc != 0)
            throw new Error("jdeps failed: rc=" + rc);
        return out.split(lineSep);
    }

    static class Data {
        static final String INTERNAL = "(internal)";
        static final String QUALIFIED = "(qualified)";
        static final String JDK_INTERNAL = "JDK internal API";

        final String moduleName;
        final boolean isNamed;
        final Map<String, ModuleRequires> requires = new LinkedHashMap<>();
        final Map<String, Dependence> references = new LinkedHashMap<>();
        Data(String name) {
            this(name, true);
        }
        Data(String name, boolean isNamed) {
            this.moduleName = name;
            this.isNamed = isNamed;
            requires("java.base");  // implicit requires
        }

        Data requires(String name) {
            requires.put(name, new ModuleRequires(name));
            return this;
        }
        Data requiresPublic(String name) {
            requires.put(name, new ModuleRequires(name, PUBLIC));
            return this;
        }
        // for unnamed module
        Data depends(String name) {
            requires.put(name, new ModuleRequires(name));
            return this;
        }
        Data reference(String origin, String target, String module) {
            return dependence(origin, target, module, "");
        }
        Data internal(String origin, String target, String module) {
            return dependence(origin, target, module, INTERNAL);
        }
        Data qualified(String origin, String target, String module) {
            return dependence(origin, target, module, QUALIFIED);
        }
        Data jdkInternal(String origin, String target, String module) {
            return dependence(origin, target, module, JDK_INTERNAL);
        }
        private Data dependence(String origin, String target, String module, String access) {
            references.put(key(origin, target), new Dependence(origin, target, module, access));
            return this;
        }

        String key(String origin, String target) {
            return origin+":"+target;
        }
        boolean check(String[] lines) {
            System.out.format("verifying module %s%s%n", moduleName, isNamed ? "" : " (unnamed module)");
            for (String l : lines) {
                String[] tokens = l.trim().split("\\s+");
                System.out.println("  " + Arrays.stream(tokens).collect(Collectors.joining(" ")));
                switch (tokens[0]) {
                    case "module":
                        assertEquals(tokens.length, 2);
                        assertEquals(moduleName, tokens[1]);
                        break;
                    case "requires":
                        String name = tokens.length == 2 ? tokens[1] : tokens[2];
                        Modifier modifier = null;
                        if (tokens.length == 3) {
                            assertEquals("public", tokens[1]);
                            modifier = PUBLIC;
                        }
                        checkRequires(name, modifier);
                        break;
                    default:
                        if (tokens.length == 3) {
                            // unnamed module requires
                            assertFalse(isNamed);
                            assertEquals(moduleName, tokens[0]);
                            String mn = tokens[2];
                            checkRequires(mn, null);
                        } else {
                            checkDependence(tokens);
                        }
                }
            }
            return true;
        }

        private void checkRequires(String name, Modifier modifier) {
            assertTrue(requires.containsKey(name));
            ModuleRequires req = requires.get(name);
            assertEquals(req.mod, modifier);
        }

        private void checkDependence(String[] tokens) {
            assertTrue(tokens.length >= 4);
            String origin = tokens[0];
            String target = tokens[2];
            String module = tokens[3];
            String key = key(origin, target);
            assertTrue(references.containsKey(key));
            Dependence dep = references.get(key);
            if (tokens.length == 4) {
                assertEquals(dep.access, "");
            } else if (tokens.length == 5) {
                assertEquals(dep.access, tokens[4]);
            } else {
                // JDK internal API
                module = tokens[6];
                assertEquals(tokens.length, 7);
                assertEquals(tokens[3], "JDK");
                assertEquals(tokens[4], "internal");
                assertEquals(tokens[5], "API");
            }
            assertEquals(dep.module, module);
        }

        public static class ModuleRequires {
            final String name;
            final ModuleDescriptor.Requires.Modifier mod;

            ModuleRequires(String name) {
                this.name = name;
                this.mod = null;
            }

            ModuleRequires(String name, ModuleDescriptor.Requires.Modifier mod) {
                this.name = name;
                this.mod = mod;
            }
        }

        public static class Dependence {
            final String origin;
            final String target;
            final String module;
            final String access;

            Dependence(String origin, String target, String module, String access) {
                this.origin = origin;
                this.target = target;
                this.module = module;
                this.access = access;
            }
        }
    }
}
