/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary Test AOT cache support for array classes in custom class loaders.
 * @bug 8353298 8356838
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build ReturnIntegerAsString
 * @build AOTCacheSupportForCustomLoaders
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AppWithCustomLoaders AppWithCustomLoaders$MyLoader
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *                 AppWithCustomLoaders$MyLoadeeA AppWithCustomLoaders$MyLoadeeB
 *                 AppWithCustomLoaders$MyLoadeeC AppWithCustomLoaders$MyLoadeeD
 *                 ReturnIntegerAsString
 * @run driver AOTCacheSupportForCustomLoaders AOT
 */

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import jdk.test.lib.cds.CDSJarUtils;
import jdk.test.lib.cds.CDSModulePackager;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCacheSupportForCustomLoaders {
    static final Path SRC = Paths.get(System.getProperty("test.src")).resolve("modules");

    public static void main(String... args) throws Exception {
        CDSModulePackager modulePackager = new CDSModulePackager(SRC);
        String modulePath = modulePackager.getOutputDir().toString();
        modulePackager.createModularJar("com.test");

        SimpleCDSAppTester.of("AOTCacheSupportForCustomLoaders")
            .classpath("app.jar")
            .addVmArgs("-Xlog:aot+class=debug", "-Xlog:aot", "-Xlog:cds",
                       "--module-path=" + modulePath,
                       "--add-modules=com.test")
            .appCommandLine("AppWithCustomLoaders", modulePath)
            .setTrainingChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Skipping AppWithCustomLoaders$MyLoadeeC: Not loaded from \"file:\" code source")
                       .shouldContain("Skipping AppWithCustomLoaders$MyLoadeeD: super AppWithCustomLoaders$MyLoadeeC is excluded")
                       .shouldContain("Skipping ReturnIntegerAsString: Failed verification");
                })
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    out.shouldMatch(",class.*unreg AppWithCustomLoaders[$]MyLoadeeA")
                       .shouldMatch(",class.*unreg com.test.Foo")
                       .shouldMatch(",class.*array \\[LAppWithCustomLoaders[$]MyLoadeeA;")
                       .shouldNotMatch("class.*unreg.*MyLoadeeC") // not from "file:" code source
                       .shouldNotMatch("class.*unreg.*MyLoadeeD") // parent is not from "file:" code source
                       .shouldNotMatch(",class.* ReturnIntegerAsString");
                })
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Using AOT-linked classes: true");
                })
            .runAOTWorkflow();
    }
}

class AppWithCustomLoaders {
    static MyLoader loader; // keep alive

    public static void main(String args[]) throws Exception {
        File custJar = new File("cust.jar");
        URL[] urls = new URL[] {custJar.toURI().toURL()};
        loader = new MyLoader(urls, AppWithCustomLoaders.class.getClassLoader());

        test1(loader);
        test2(loader);
        test3(args[0]);
        test4(loader);

        // TODO: more test cases JDK-8354557
    }

    // Test 1: array class of MyLoadeeA (JDK-8353298)
    static void test1(MyLoader loader) throws Exception {
        Class klass = loader.loadClass("AppWithCustomLoaders$MyLoadeeA");
        klass.newInstance();
    }

    // Test 2: VerificationType::is_reference_assignable_from() cannot be skipped (JDK-8356407)
    static void test2(MyLoader loader) throws Exception {
        try {
            Class bad = loader.loadClass("ReturnIntegerAsString");
            Object o = bad.newInstance(); // force verification
            System.out.println("Expected String but got: " + o.toString().getClass());
            throw new RuntimeException("VerifyError expected but not thrown");
        } catch (VerifyError ve) {
            System.out.println("Expected: " + ve);
        }
    }

    // Test 3: custom loader defines a class from the exact location as a class defined in the boot layer.
    static Class test3_c1_keepalive;
    static void test3(String modulePath) throws Exception {
        Class<?> c0 = Class.forName("com.test.Foo");
        System.out.println(c0);
        System.out.println(System.identityHashCode(c0.getModule()));
        System.out.println(c0.getModule().getName());
        System.out.println(c0.getClassLoader());

        // Regression test for JDK-8356838
        //
        // We create a new layer that loads the com.test module from the modulePath into
        // a different class loader.
        ModuleFinder finder = ModuleFinder.of(Paths.get(modulePath));
        ModuleLayer parent = ModuleLayer.boot();
        Configuration cf = parent.configuration().resolve(finder, ModuleFinder.of(), Set.of("com.test"));
        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ModuleLayer layer = parent.defineModulesWithOneLoader(cf, scl);
        Class<?> c1 = layer.findLoader("com.test").loadClass("com.test.Foo");

        System.out.println(c1);
        System.out.println(System.identityHashCode(c1.getModule()));
        System.out.println(c1.getModule().getName());
        System.out.println(c1.getClassLoader());

        // Keep c1 alive so it can be cached.
        test3_c1_keepalive = c1;

        if (!c1.getModule().getName().equals("com.test")) {
            throw new RuntimeException("Unexpected module: " + c1.getModule());
        }
        if (c1.getModule() == c0.getModule()) {
            throw new RuntimeException("Unexpected module: " + c1.getModule());
        }
        if (c1.getClassLoader() == c0.getClassLoader()) {
            throw new RuntimeException("Unexpected class loader: " + c1.getClassLoader());
        }
    }

    // Test 4: classes that don't use file: code source should be excluded
    static void test4(MyLoader loader) throws Exception {
        Class c = loader.loadLoadeeC();
        Class d = loader.loadClass("AppWithCustomLoaders$MyLoadeeD");

        URL ccs = c.getProtectionDomain().getCodeSource().getLocation();

        if (ccs != null) {
            throw new RuntimeException("MyLoadeeC should have null CodeSource but got: " + ccs);
        }

        if (d.getSuperclass() != c) {
            throw new RuntimeException("MyLoadeeC should be super class of MyLoadeeD");
        }
    }

    public static class MyLoader extends URLClassLoader {
        public MyLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        public Class<?> loadLoadeeC() throws Exception {
            try (InputStream in = getResourceAsStream("AppWithCustomLoaders$MyLoadeeC.class")) {
                byte[] b = in.readAllBytes();
                // Define MyLoadeeC without specifying a ProtectionDomain. As a result, this
                // class will get an empty ProtectionDomain whose CodeSource location will be null.
                //
                // This class should be excluded from the AOT cache. See JDK-8380291
                return defineClass(b, 0, b.length);
            }
        }
    }

    public static class MyLoadeeA {
        static Object[] array1;

        public MyLoadeeA() {
            if (array1 == null) {
                test();
                Object o = array1[0];
                System.out.println("array1[0] is of class: " + o.getClass());
                if (!(o instanceof MyLoadeeA)) {
                    throw new RuntimeException("array1[0] should be an instanceof MyLoadeeA");
                }
            }
        }

        static void test() {
            array1 = new MyLoadeeA[10];
            for (int i = 0; i < 10; i++) {
                if ((i % 2) == 0) {
                    array1[i] = new MyLoadeeB();
                } else {
                    array1[i] = new MyLoadeeA();
                }
            }
        }
    }

    public static class MyLoadeeB extends MyLoadeeA {}


    public static class MyLoadeeC {}

    public static class MyLoadeeD extends MyLoadeeC {}
}
