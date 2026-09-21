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
 * @summary Sanity test for -XX:+AOTCompatibleOopCompression
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.bits == 64 & vm.opt.final.UseCompressedOops == true
 * @library /test/lib
 * @build AOTCompatibleOopCompression
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar AOTCompatibleOopCompressionApp
 * @run driver AOTCompatibleOopCompression AOT
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCompatibleOopCompression {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = AOTCompatibleOopCompressionApp.class.getName();

    public static void main(String[] args) throws Exception {
        Tester tester = new Tester();
        tester.run(args);

        // Since the AOT cache has been assembled with -XX:+AOTCompatibleOopCompression,
        // production runs will always run with -XX:+AOTCompatibleOopCompression. The value
        // specified in the command-line is ignored.
        tester.productionRun(new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:-AOTCompatibleOopCompression"});
        tester.productionRun(new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:+AOTCompatibleOopCompression"});
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            if (runMode == RunMode.ASSEMBLY) {
                return new String[] {"-XX:+UnlockDiagnosticVMOptions", "-XX:+AOTCompatibleOopCompression"};
            } else if (runMode == RunMode.PRODUCTION) {
                return new String[] {"-Xlog:aot", "-XX:AOTMode=on", "-XX:HeapBaseMinAddress=0x800000000"};
            } else {
                return new String[0];
            }
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] { mainClass };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("HelloWorld");
                out.shouldContain("AOTCompatibleOopCompression = true");
                out.shouldNotContain("AOTCompatibleOopCompression = false");
            }
        }
    }
}

class AOTCompatibleOopCompressionApp {
    public static void main(String[] args) {
        System.out.println("HelloWorld");
    }
}
