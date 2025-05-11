/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test how various AOT optimizations handle classes that are excluded from the AOT cache.
 * @requires vm.cds.write.archived.java.heap
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/jdk/lib/testlibrary /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds/aotCache/test-classes
 * @build ExcludedClasses CustyWithLoop
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 TestApp
 *                 TestApp$Foo
 *                 TestApp$Foo$Bar
 *                 TestApp$Foo$ShouldBeExcluded
 *                 TestApp$MyInvocationHandler
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *                 CustyWithLoop
 * @run driver ExcludedClasses
 */

import java.io.File;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;
import java.util.Map;

import jdk.jfr.Event;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class ExcludedClasses {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "TestApp";

    public static void main(String[] args) throws Exception {
        {
          Tester tester = new Tester();
          tester.run(new String[] {"AOT"} );
        }
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "-Xlog:cds+resolve=trace",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass, runMode.name()
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (isDumping(runMode)) {
                out.shouldNotMatch("cds,resolve.*archived field.*TestApp.Foo => TestApp.Foo.ShouldBeExcluded.f:I");
            }
        }
    }
}

class TestApp {
    static volatile Object custInstance;
    static volatile Object custArrayInstance;

    public static void main(String args[]) throws Exception {
        // In new workflow, classes from custom loaders are passed from the preimage
        // to the final image. See ClassPrelinker::record_unregistered_klasses().
        custInstance = initFromCustomLoader();
        custArrayInstance = java.lang.reflect.Array.newInstance(custInstance.getClass(), 0);
        System.out.println(custArrayInstance);
        System.out.println("Counter = " + Foo.hotSpot());
    }

    static Object initFromCustomLoader() throws Exception {
        String path = "cust.jar";
        URL url = new File(path).toURI().toURL();
        URL[] urls = new URL[] {url};
        URLClassLoader urlClassLoader =
            new URLClassLoader("MyLoader", urls, null);
        Class c = Class.forName("CustyWithLoop", true, urlClassLoader);
        return c.newInstance();
    }

    static class MyInvocationHandler implements InvocationHandler {
        volatile static int cnt;

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 20) {
                cnt += 2;
                for (int i = 0; i < 1000; i++) {
                    int n = cnt - 2;
                    if (n < 2) {
                        n = 2;
                    }
                    cnt += (i + cnt) % n + cnt % 2;
                }
            }
            return Integer.valueOf(cnt);
        }
    }

    static class Foo {
        volatile static int counter;
        static Class c = ShouldBeExcluded.class;

        static Map mapProxy = (Map) Proxy.newProxyInstance(
            Foo.class.getClassLoader(), 
            new Class[] { Map.class },
            new MyInvocationHandler());

        static int hotSpot() {
            ShouldBeExcluded s = new ShouldBeExcluded();
            Bar b = new Bar();

            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 1000) {
                lambdaHotSpot();
                s.hotSpot2();
                b.hotSpot3();

                // In JDK mainline, generated proxy classes are excluded from the AOT cache.
                // In Leyden/premain, generated proxy classes included. The following code should
                // work with either repos.
                Integer i = (Integer)mapProxy.get(null);
                counter += i.intValue();

                if (custInstance != null) {
                    // Classes loaded by custom loaders are included included in the AOT cache
                    // but their array classes are excluded.
                    counter += custInstance.equals(null) ? 1 : 2;
                }

                if (custArrayInstance != null) {
                    if ((counter % 3) == 0) {
                        counter += (custArrayInstance instanceof String) ? 0 : 1;
                    } else {
                        counter += (custArrayInstance instanceof Object) ? 0 : 1;
                    }
                }
            }

            return counter + s.m() + s.f + b.m() + b.f;
        }

        static void f() {
            if (counter % 2 == 1) {
                counter ++;
            }
        }

        // Generated Lambda classes should be excluded from CDS preimage.
        static void lambdaHotSpot() {
            long start = System.currentTimeMillis();
            while (System.currentTimeMillis() - start < 20) {
                doit(() -> {
                        counter ++;
                    });
            }
        }

        static void doit(Runnable r) {
            r.run();
        }

        // All subclasses of jdk.jfr.Event are excluded from the CDS archive.
        static class ShouldBeExcluded extends jdk.jfr.Event {
            int f = (int)(System.currentTimeMillis()) + 123;
            int m() {
                return f + 456;
            }

            void hotSpot2() {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 20) {
                    for (int i = 0; i < 50000; i++) {
                        counter += i;
                    }
                    f();
                }
            }
        }

        static class Bar {
            int f = (int)(System.currentTimeMillis()) + 123;
            int m() {
                return f + 456;
            }

            void hotSpot3() {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 20) {
                    for (int i = 0; i < 50000; i++) {
                        counter += i;
                    }
                    f();
                }
            }
        }
    }
}

