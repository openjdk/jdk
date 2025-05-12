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
 * @test
 * @summary Test AOT cache support for array classes in custom class loaders.
 * @bug 8353298
 * @requires vm.cds.supports.aot.class.linking
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build ReturnIntegerAsString
 * @build AOTCacheSupportForCustomLoaders
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AppWithCustomLoaders AppWithCustomLoaders$MyLoader
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar AppWithCustomLoaders$MyLoadeeA AppWithCustomLoaders$MyLoadeeB ReturnIntegerAsString
 * @run driver AOTCacheSupportForCustomLoaders AOT
 */

import java.net.URL;
import java.net.URLClassLoader;
import java.io.File;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCacheSupportForCustomLoaders {
    public static void main(String... args) throws Exception {
        SimpleCDSAppTester.of("AOTCacheSupportForCustomLoaders")
            .classpath("app.jar")
            .addVmArgs("-Xlog:cds+class=debug", "-Xlog:cds")
            .appCommandLine("AppWithCustomLoaders")
            .setAssemblyChecker((OutputAnalyzer out) -> {
                    out.shouldMatch("cds,class.*unreg AppWithCustomLoaders[$]MyLoadeeA")
                       .shouldMatch("cds,class.*array \\[LAppWithCustomLoaders[$]MyLoadeeA;")
                       .shouldNotMatch("cds,class.* ReturnIntegerAsString");
                })
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("Using AOT-linked classes: true");
                })
            .runAOTWorkflow();
    }
}

class AppWithCustomLoaders {
    public static void main(String args[]) throws Exception {
        File custJar = new File("cust.jar");
        URL[] urls = new URL[] {custJar.toURI().toURL()};
        MyLoader loader = new MyLoader(urls, AppWithCustomLoaders.class.getClassLoader());

        // Test 1: array class of MyLoadeeA (JDK-8353298)
        Class klass = loader.loadClass("AppWithCustomLoaders$MyLoadeeA");
        klass.newInstance();

        // Test 2: VerificationType::is_reference_assignable_from() cannot be skipped (JDK-8356407)
        try {
            Class bad = loader.loadClass("ReturnIntegerAsString");
            Object o = bad.newInstance(); // force verification
            System.out.println("Expected String but got: " + o.toString().getClass());
            throw new RuntimeException("VerifyError expected but not thrown");
        } catch (VerifyError ve) {
            System.out.println("Expected: " + ve);
        }

        // TODO: more test cases JDK-8354557
    }

    public static class MyLoader extends URLClassLoader {
        public MyLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
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
}
