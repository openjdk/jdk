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
 * @summary The agent is loaded in production run. It redefines all classes, including those already loaded from the AOT cache.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/setup_aot
 * @build RedefineAllTest RedefineAllAgent JavacBenchApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar
 *                 JavacBenchApp
 *                 JavacBenchApp$ClassFile
 *                 JavacBenchApp$FileManager
 *                 JavacBenchApp$SourceFile
 * @run driver/timeout=240 RedefineAllTest AOT
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class RedefineAllTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "JavacBenchApp";

    public static String agentClasses[] = {
        "RedefineAllAgent",
    };
    static String agentJar;

    public static void main(String... args) throws Exception {
        agentJar = ClassFileInstaller.writeJar("agent.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("RedefineAllAgent.mf"),
                                        agentClasses);
        run(args, false);
        run(args, true);
    }

    static void run(String[] args, boolean compressedOops) throws Exception {
        Tester t = new Tester(compressedOops);
        t.run(args);
    }

    static class Tester extends CDSAppTester {
        boolean compressedOops;

        public Tester(boolean compressedOops) {
            super(mainClass);
            this.compressedOops = compressedOops;
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] vmArgs(RunMode runMode) {
            String mode = "-XX:" + (compressedOops ? "+" : "-") + "UseCompressedOops";

            if (runMode == RunMode.PRODUCTION) {
                return new String[] {
                    mode,
                    "-javaagent:" + agentJar,
                };
            } else {
                return new String[] {
                    mode,
                    // This is needed for using the agent in production run.
                    "--add-modules=java.instrument",
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                "2",
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode.isApplicationExecuted()) {
                out.shouldMatch("Generated source code for [0-9]+ classes and compiled them");
            }
        }
    }
}
