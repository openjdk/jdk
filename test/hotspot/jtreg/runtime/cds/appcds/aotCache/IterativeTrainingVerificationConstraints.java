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
 * @summary Iterative AOT training must preserve verifier constraints for copied custom-loader classes.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib
 * @build IterativeTrainingVerificationConstraints
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *        IterativeTrainingVerificationConstraintsApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar cust.jar
 *        BaseOnlyConstraint
 *        ConstraintBase
 *        ConstraintChild
 *        SecondOnlyConstraint
 * @run driver IterativeTrainingVerificationConstraints AOT-Retrain2 --two-step-training
 */

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class IterativeTrainingVerificationConstraints {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = IterativeTrainingVerificationConstraintsApp.class.getName();

    public static void main(String... args) throws Exception {
        Tester tester = new Tester();
        tester.run(args);
    }

    static class Tester extends CDSAppTester {
        Tester() {
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
                "-Xlog:aot+verification=trace",
                "-Xlog:class+load",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            String which = "all";
            if (runMode == RunMode.TRAINING) {
                which = isAOTRetraining() ? "second" : "first";
            }

            return new String[] {
                mainClass,
                which,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.TRAINING && isAOTRetraining()) {
                out.shouldMatch("add verification constraint: BaseOnlyConstraint: " +
                                "ConstraintChild must be subclass of ConstraintBase.*");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldMatch("class,load.*BaseOnlyConstraint source: shared objects file");
                out.shouldMatch("class,load.*SecondOnlyConstraint source: shared objects file");
            }
        }
    }
}

class IterativeTrainingVerificationConstraintsApp {
    static URLClassLoader loader;

    public static void main(String[] args) throws Exception {
        String mode = args[0];

        File custJar = new File("cust.jar");
        URL[] urls = new URL[] { custJar.toURI().toURL() };
        loader = new URLClassLoader(urls, IterativeTrainingVerificationConstraintsApp.class.getClassLoader());

        if (mode.equals("first") || mode.equals("all")) {
            loadCust("BaseOnlyConstraint");
        }
        if (mode.equals("second") || mode.equals("all")) {
            loadCust("SecondOnlyConstraint");
        }
    }

    static void loadCust(String className) throws Exception {
        Class<?> c = Class.forName(className, true, loader);
        Constructor<?> constructor = c.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object o = constructor.newInstance();
        Method m = c.getDeclaredMethod("run");
        m.setAccessible(true);
        System.out.println(m.invoke(o));
    }
}

class BaseOnlyConstraint {
    public ConstraintBase run() {
        return new ConstraintChild();
    }
}

class ConstraintBase {
}

class ConstraintChild extends ConstraintBase {
}

class SecondOnlyConstraint {
    public String run() {
        return "second";
    }
}
