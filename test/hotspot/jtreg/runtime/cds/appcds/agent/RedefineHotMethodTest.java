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
 * @summary The agent is loaded in production run. It redefines RedefineHotMethodApp::increment() to return 34.
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/hotspot/jtreg/serviceability/jvmti/RedefineClasses
 * @build RedefineHotMethodTest RedefineClassHelper
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar RedefineHotMethodApp RedefineClassHelper
 * @run main RedefineClassHelper
 * @run driver RedefineHotMethodTest AOT
 */

import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class RedefineHotMethodTest {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = RedefineHotMethodApp.class.getName();

    public static String agentClasses[] = {
        "RedefineHotMethodAgent",
    };
    static String agentJar = "redefineagent.jar";

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
            if (runMode == RunMode.PRODUCTION) {
                return new String[] {
                    "-javaagent:" + agentJar,
                };
            } else {
                return new String[] {
                    // This is needed for using the agent in production run.
                    "--add-modules=java.instrument",
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
                runMode.toString(),
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) throws Exception {
            if (runMode == RunMode.TRAINING) {
                out.shouldContain("counter = 120000001");
            } else if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("counter = 340000001");
            }
        }
    }
}

class RedefineHotMethodApp {
    volatile static long counter;

    public static void main(String args[]) throws Exception {
        if (args[0].equals("PRODUCTION")) {
            redefineIncrementMethod();
        }
        doLoop();
        counter ++;
        System.out.println("counter = " + counter);
    }

    static void doLoop() {
        for (int i = 0; i < 1000 * 1000; i++) {
            for (int j = 0; j < 10; j++) {
                counter += increment();
            }
        }
    }

    static void redefineIncrementMethod() throws Exception {
        RedefineClassHelper.redefineMethodBodies(RedefineHotMethodApp.class,
                                                 (MethodModel method) -> method.methodName().equalsString("increment"),
                                                 (CodeBuilder builder, CodeElement element) -> {
                                                     builder.loadConstant(34);
                                                     builder.ireturn();
                                                 });
    }

    // This method will be redefined in redefineIncrementMethod() to return 34 instead.
    //
    // Any AOT-compiled methods that use the original version of this method
    // must not be used.
    static int increment() {
        return 12;
    }
}
