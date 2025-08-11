/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @test id=static Lambda expressions in excluded classes shouldn't be resolved during the assembly phase.
 * @bug 8349888
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.gc.Epsilon
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build LambdaInExcludedClass
 * @run driver jdk.test.lib.helpers.ClassFileInstaller LambdaInExcludedClassApp
 * @run driver LambdaInExcludedClass STATIC
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class LambdaInExcludedClass {
    static final String mainClass = "LambdaInExcludedClassApp";

    public static void main(String[] args) throws Exception {
        Tester t = new Tester();
        t.run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String classpath(RunMode runMode) {
            // Using "." as the classpath allows the LambdaInExcludedClassApp to be loaded in
            // the assembly phase, but this class will be excluded from the AOT cache.
            return ".";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            return new String[] {
                "-Xmx128m",
                "-XX:+AOTClassLinking",
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseEpsilonGC",
            };
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.DUMP_STATIC) {
                out.shouldContain("Skipping LambdaInExcludedClassApp: Unsupported location");
                out.shouldContain("Cannot aot-resolve constants for LambdaInExcludedClassApp because it is excluded");
            } else {
                out.shouldContain("Hello LambdaInExcludedClassApp");
            }
        }
    }
}

class LambdaInExcludedClassApp {
    public static void main(String args[]) throws Exception {
        // LambdaInExcludedClassApp is excluded, so aot-linking of lambda call sites
        // should not happen for this class. Otherwise Epsilon GC may crash due
        // to 8349888.
        Runnable r = LambdaInExcludedClassApp::doit;
        r.run();
    }

    static void doit() {
        System.out.println("Hello LambdaInExcludedClassApp");
    }
}
