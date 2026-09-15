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
 *
 */

/*
 * @test
 * @summary Iterative training -- re-run AOT training while using an AOT cache.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build IterativeTraining Hello HelloMore Hi Hey
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar IterativeTrainingApp First Second
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar Hello HelloMore Hi Hey Greet
 * @run driver IterativeTraining  AOT-Retrain2
 */

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class IterativeTraining {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String custJar = ClassFileInstaller.getJarPath("cust.jar");
    static final String mainClass = IterativeTrainingApp.class.getName();

    public static void main(String... args) throws Exception {
        Tester t = new Tester();
        t.run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "-Xlog:aot",
                "-Xlog:aot,aot+class=debug",
                "-Xlog:class+load",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            String which = "all";

            if (runMode == RunMode.TRAINING) {
                if (!isAOTRetraining()) {
                    which = "first";
                } else {
                    which = "second";
                }
            }

            return new String[] {
                mainClass,
                which,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.PRODUCTION) {
                // "First" is loaded only on the first training run
                // "Second" is loaded only on the second training run
                // Both classes should be in the AOT archive.
                out.shouldMatch("class,load.*First source: shared objects file");
                out.shouldMatch("class,load.*Second source: shared objects file");

                // "Hello" is loaded only on the first training run (custom loader)
                // "HelloMore" is loaded only on the second training run (custom loader)
                // Both classes should be in the AOT archive.
                out.shouldMatch("class,load.*Hello source: shared objects file");
                out.shouldMatch("class,load.*HelloMore source: shared objects file");

                // Hi and Hey have the same super class. All 3 classes should be cached.
                out.shouldMatch("class,load.*Greet source: shared objects file");
                out.shouldMatch("class,load.*Hi source: shared objects file");
                out.shouldMatch("class,load.*Hey source: shared objects file");
            }
        }
    }
}

class IterativeTrainingApp {
    static URLClassLoader loader;
    public static void main(String[] args) throws Exception {
        String mode = args[0];

        File custJar = new File("cust.jar");
        URL[] urls = new URL[] {custJar.toURI().toURL()};
        loader = new URLClassLoader(urls, IterativeTrainingApp.class.getClassLoader());

        if (mode.equals("first") || mode.equals("all")) {
            First.func();
            loadCust("Hello");
            loadCust("Hi");
        }
        if (mode.equals("second") || mode.equals("all")) {
            Second.func();
            loadCust("HelloMore");
            loadCust("Hey");
        }
    }

    static void loadCust(String className) throws Exception {
        Class<?> c = loader.loadClass(className);
        System.out.println(c.newInstance());
    }
}

class First {
    static void func() {}
}

class Second {
    static void func() {}
}

class FirstCust {}
class SecondCust {}
