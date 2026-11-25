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
 * @bug 8354315
 * @summary Test restrictions when using an alternate system class loader
 * @requires vm.cds
 * @library /test/lib
 * @build AlternateSystemLoaderTest
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar ExcludedClass$AlternateSystemLoader ExcludedClass
 * @run driver AlternateSystemLoaderTest
 */

import java.net.URL;
import java.net.URLClassLoader;

import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AlternateSystemLoaderTest {

    public static void main(String[] args) throws Exception {
        final String appName = "ExcludedClass";
        String appJar = ClassFileInstaller.getJarPath("app.jar");
        String logArgs = "-Xlog:cds=debug,aot=debug,class+load,aot+class=debug";

        SimpleCDSAppTester tester = SimpleCDSAppTester.of("AlternateSystemLoader");

        // Train with alternate system loader
        tester.addVmArgs("-XX:-AOTClassLinking",
                         logArgs,
                         "-Djava.system.class.loader=ExcludedClass$AlternateSystemLoader")
            .classpath(appJar)
            .appCommandLine(appName)
            .setTrainingChecker((OutputAnalyzer out) -> {
                out.shouldContain("AOT optimizations are curtailed because -Djava.system.class.loader=ExcludedClass$AlternateSystemLoader is specified");
                out.shouldContain("Skipping ExcludedClass: Defined in built-in app class loader");
            }).runAOTTraining();

        // Assemble without alternate loader
        tester.setVmArgs("-XX:-AOTClassLinking",
                         logArgs)
            .setAssemblyChecker((OutputAnalyzer out) -> {
                out.shouldNotContain("AOT optimizations are curtailed because -Djava.system.class.loader=ExcludedClass$AlternateSystemLoader is specified");
                out.shouldNotContain("ExcludedClass source: shared objects file");
                out.shouldNotContain("app   ExcludedClass");
            }).runAOTAssembly();

        tester.setProductionChecker((OutputAnalyzer out) -> {
            // Do not load any classes defined in the system loader
            out.shouldNotContain("ExcludedClass source: shared objects file");
        }).productionRun();

        // Train without alternate loader
        tester.setVmArgs("-XX:-AOTClassLinking",
                         logArgs)
            .classpath(appJar)
            .appCommandLine(appName)
            .setTrainingChecker((OutputAnalyzer out) -> {
                out.shouldNotContain("Skipping ExcludedClass: Defined in built-in app class loader");
            }).runAOTTraining();

        // Assemble with alternate loader
        tester.setVmArgs("-XX:-AOTClassLinking",
                         logArgs,
                         "-Djava.system.class.loader=ExcludedClass$AlternateSystemLoader")
            .setAssemblyChecker((OutputAnalyzer out) -> {
                out.shouldContain("AOT optimizations are curtailed because -Djava.system.class.loader=ExcludedClass$AlternateSystemLoader is specified");
                out.shouldNotContain("ExcludedClass source: shared objects file");
                out.shouldNotContain("app   ExcludedClass");
            }).runAOTAssembly();
        tester.setVmArgs("-XX:-AOTClassLinking",
                         logArgs)
            .setProductionChecker((OutputAnalyzer out) -> {
            out.shouldNotContain("ExcludedClass source: shared objects file");
        }).productionRun();

        // Train and assemble without alternate loader
        tester.setVmArgs("-XX:-AOTClassLinking",
                         logArgs)
            .classpath(appJar)
            .appCommandLine(appName)
            .setTrainingChecker((OutputAnalyzer out) -> {
                out.shouldNotContain("AOT optimizations are curtailed because -Djava.system.class.loader=ExcludedClass$AlternateSystemLoader is specified");
                out.shouldNotContain("Skipping ExcludedClass: Defined in built-in app class loader");
            })
            .setAssemblyChecker((OutputAnalyzer out) -> {
                out.shouldNotContain("AOT optimizations are curtailed because -Djava.system.class.loader=ExcludedClass$AlternateSystemLoader is specified");
                out.shouldContain("ExcludedClass source: shared objects file");
                out.shouldContain("app   ExcludedClass");
            }).runAOTTrainingAndAssemblyWorkflow();

        // Production run with alternate loader
        tester.setVmArgs(logArgs, "-Djava.system.class.loader=ExcludedClass$AlternateSystemLoader")
            .setProductionChecker((OutputAnalyzer out) -> {
                // Do not load any unregistered classes or any classes defined in platform and system loaders
                out.shouldNotContain("ExcludedClass source: shared objects file");
            }).productionRun();
    }
}

class ExcludedClass {
    public static void main(String[] args) {
        System.out.println("Hello");
    }

    public static class AlternateSystemLoader extends URLClassLoader {
        public AlternateSystemLoader(ClassLoader parent) {
            super(new URL[] {
                ExcludedClass.class.getProtectionDomain().getCodeSource().getLocation()
            }, parent);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.equals("ExcludedClass")) {
                return super.loadClass(name, resolve);
            }
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                c = findClass(name);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }
}
