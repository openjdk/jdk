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
 *
 */

/*
 * @test Do not cache classes that are loaded from a fake location.
 * @bug 8352001
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build FakeCodeLocation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar FakeCodeLocationApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller ClassNotInJar1 ClassNotInJar2
 * @run driver FakeCodeLocation
 */

import java.lang.invoke.MethodHandles;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.StringArrayUtils;

public class FakeCodeLocation {
    static final String appJar = "app.jar";
    static final String mainClass = FakeCodeLocationApp.class.getName();

    public static void main(String[] args) throws Exception {
        (new Tester(false)).run(new String[] {"STATIC"});
        (new Tester(true )).run(new String[] {"STATIC"});
        (new Tester(false)).run(new String[] {"AOT"});
        (new Tester(true )).run(new String[] {"AOT"});
    }

    static class Tester extends CDSAppTester {
        boolean addOpen;;
        public Tester(boolean addOpen) {
            super(mainClass);
            this.addOpen = addOpen;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            String[] args = new String[] {
                "-Xlog:aot",
                "-Xlog:cds",
                "-Xlog:aot+class=debug",
                "-Xlog:cds+class=debug",
                "-Xlog:class+load",
            };
            if (addOpen) {
                args = StringArrayUtils.concat(args, "--add-opens", "java.base/java.lang=ALL-UNNAMED", "-XX:-AOTClassLinking");
            }
            return args;
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                addOpen ? "hasAddedOpen" : "hasNotAddedOpen",
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (isDumping(runMode)) {
                out.shouldMatch(",class.* FakeCodeLocationApp");
                out.shouldNotMatch(",class.* ClassNotInJar1");
                out.shouldNotMatch(",class.* ClassNotInJar2");
            }

            if (runMode.isProductionRun()) {
                out.shouldMatch("class,load.* FakeCodeLocationApp .*source: shared objects file");
                out.shouldNotMatch("class,load.* ClassNotInJar1 .*source: shared objects file");
                out.shouldNotMatch("class,load.* ClassNotInJar2 .*source: shared objects file");
            }
        }
    }
}

class FakeCodeLocationApp {
    static boolean hasAddedOpen;

    public static void main(String args[]) throws Exception {
        hasAddedOpen = args[0].equals("hasAddedOpen");
        testWithLookup();
        testWithSetAccessible();
    }

    // Define a class using Lookup.defineClass(). The ClassFileParser should see "__JVM_DefineClass__"
    // as the source location, so this class will be excluded, as the location is not supported.
    static void testWithLookup() throws Exception {
        byte[] data = Files.readAllBytes(Paths.get("ClassNotInJar1.class"));
        Class c = MethodHandles.lookup().defineClass(data);
        System.out.println(c.getProtectionDomain());
        System.out.println(c.getProtectionDomain().getCodeSource());
    }

    // Use setAccessible to call into ClassLoader.defineClass(). In this case, the ClassFileParser
    // sees "app.jar" as the source location, but the app.jar doesn't contain this class file, so we
    // should exclude this class.
    static void testWithSetAccessible() throws Exception {
        Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class, ProtectionDomain.class);
        System.out.println(m);
        try {
            m.setAccessible(true);
            if (!hasAddedOpen) {
                throw new RuntimeException("setAccessible() should have failed because '--add-opens java.base/java.lang=ALL-UNNAMED' was not specified");
            }
        } catch (InaccessibleObjectException t) {
            if (hasAddedOpen) {
                throw new RuntimeException("setAccessible() failed even though '--add-opens java.base/java.lang=ALL-UNNAMED' was specified");
            } else {
                System.out.println("\n\nExpected: " + t);
                t.printStackTrace(System.out);
                return;
            }
        }

        ProtectionDomain pd = FakeCodeLocationApp.class.getProtectionDomain();
        ClassLoader appLoader = FakeCodeLocationApp.class.getClassLoader();
        byte[] data = Files.readAllBytes(Paths.get("ClassNotInJar2.class"));
        Class c = null;
        try {
            c = (Class)m.invoke(appLoader, "ClassNotInJar2", data, 0, data.length, pd);
        } catch (Throwable t) {
            System.out.println(t);
            t.printStackTrace(System.out);
            return;
        }

        System.out.println(c);
        System.out.println(c.getProtectionDomain());
        System.out.println(c.getProtectionDomain().getCodeSource());
    }
}

class ClassNotInJar1 {}

class ClassNotInJar2 {}
