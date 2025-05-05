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

/**
 * @test
 * @summary Sanity test of combinations of the AOT Code Caching diagnostic flags
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.flagless
 * @comment work around JDK-8345635
 * @requires !vm.jvmci.enabled
 * @library /test/lib /test/setup_aot
 * @build AOTCodeFlags JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver AOTCodeFlags
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;

public class AOTCodeFlags {
    public static int flag_sign = 0;
    public static void main(String... args) throws Exception {
        Tester t = new Tester();
        for (int i = 0; i < 2; i++) {
            flag_sign = i;
            t.run(new String[] {"AOT"});
        }
    }
    static class Tester extends CDSAppTester {
        public Tester() {
            super("AOTCodeFlags" + flag_sign);
        }

        @Override
        public String classpath(RunMode runMode) {
            return "app.jar";
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            switch (runMode) {
            case RunMode.ASSEMBLY:
            case RunMode.PRODUCTION:
                return new String[] {
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:" + (flag_sign == 0 ? "-" : "+") + "AOTAdapterCaching",
                    "-Xlog:aot+codecache+init=debug",
                    "-Xlog:aot+codecache+exit=debug",
                };
            }
            return new String[] {};
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                "JavacBenchApp", "10"
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (flag_sign == 0) {
                switch (runMode) {
                case RunMode.ASSEMBLY:
                case RunMode.PRODUCTION:
                    out.shouldNotContain("Adapters:  total");
                    break;
                }

            } else {
                switch (runMode) {
                case RunMode.ASSEMBLY:
                case RunMode.PRODUCTION:
                    out.shouldContain("Adapters:  total");
                    break;
                }
            }
        }

    }
}
