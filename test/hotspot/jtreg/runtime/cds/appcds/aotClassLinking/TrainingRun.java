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
 * @summary -XX:AOTMode=record should not interfere with app execution: (1) thread creation; (2) exit code
 * @bug 8351327
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/jdk/lib/testlibrary /test/lib
 * @build TrainingRun
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar MyTestApp
 * @run driver TrainingRun AOT
 */

import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.lib.process.OutputAnalyzer;

public class TrainingRun {
    static final String appJar = ClassFileInstaller.getJarPath("app.jar");
    static final String mainClass = "MyTestApp";

    public static void main(String[] args) throws Exception {
        (new Tester()).run(args);
    }

    static class Tester extends CDSAppTester {
        public Tester() {
            super(mainClass);

            // CDSAppTester usually wants the app to return exit value 0, but this test
            // checks whether the training run can return 2.
            setCheckExitValue(false);
        }

        @Override
        public String classpath(RunMode runMode) {
            return appJar;
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            return new String[] {
                mainClass,
            };
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode.isApplicationExecuted()) {
                out.shouldHaveExitValue(2);
                out.shouldContain("Hello: x is 1");
            }
        }
    }
}

class MyTestApp {
    volatile static int x = 0;

    public static void main(String args[]) throws Exception {
        Thread t = new Thread(() -> {
                x = 1;
        });
        t.start();
        t.join();

        if (x != 1) {
            throw new RuntimeException("x should be 1 but is " + x);
        }
        System.out.println("Hello: x is " + x);
        System.out.println("I am calling System.exit(2)");
        System.exit(2);
    }
}
