/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test jvmti class file loader hook interaction with AppCDS
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @requires vm.cds
 * @requires vm.cds.supports.aot.class.linking
 * @requires vm.jvmti
 * @build ClassFileLoadHook
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/native ClassFileLoadHookTest
 */


import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.cds.CDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ClassFileLoadHookTest {
    public static String sharedClasses[] = {
        "ClassFileLoadHook",
        "ClassFileLoadHook$TestCaseId",
        "LoadMe",
        "java/sql/SQLException"
    };

    static final String mainClass = "ClassFileLoadHook";
    static String wbJar;
    static String appJar;
    static String useWb;

    public static void main(String[] args) throws Exception {
        wbJar = ClassFileInstaller.writeJar("WhiteBox.jar", "jdk.test.whitebox.WhiteBox");
        appJar = ClassFileInstaller.writeJar("ClassFileLoadHook.jar", sharedClasses);
        useWb = "-Xbootclasspath/a:" + wbJar;

        // First, run the test class directly, w/o sharing, as a baseline reference
        CDSOptions opts = (new CDSOptions())
            .setUseVersion(false)
            .setXShareMode("off")
            .addSuffix("-XX:+UnlockDiagnosticVMOptions",
                       "-XX:+WhiteBoxAPI",
                       useWb,
                       "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                       mainClass,
                       "" + ClassFileLoadHook.TestCaseId.SHARING_OFF_CFLH_ON);
        CDSTestUtils.run(opts)
                    .assertNormalExit();

        // Run with AppCDS, but w/o CFLH - second baseline
        TestCommon.testDump(appJar, sharedClasses, useWb);
        OutputAnalyzer out = TestCommon.exec(appJar,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI", useWb,
                mainClass,
                "" + ClassFileLoadHook.TestCaseId.SHARING_ON_CFLH_OFF);

        TestCommon.checkExec(out);


        // Now, run with AppCDS with -Xshare:auto and CFLH
        out = TestCommon.execAuto("-cp", appJar,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI", useWb,
                "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                mainClass,
                "" + ClassFileLoadHook.TestCaseId.SHARING_AUTO_CFLH_ON);

        opts = (new CDSOptions()).setXShareMode("auto");
        TestCommon.checkExec(out, opts);

        // Now, run with AppCDS -Xshare:on and CFLH
        out = TestCommon.exec(appJar,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+WhiteBoxAPI", useWb,
                "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                mainClass,
                "" + ClassFileLoadHook.TestCaseId.SHARING_ON_CFLH_ON);
        TestCommon.checkExec(out);

        // JEP 483: if dumped with -XX:+AOTClassLinking, cannot use archive when CFLH is enabled
        Tester t = new Tester();
        t.setCheckExitValue(false);
        t.runAOTWorkflow();
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
            if (runMode == RunMode.TRAINING) {
                return new String[] {
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+WhiteBoxAPI", useWb,
                    "-XX:+AOTClassLinking",
                    "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                    "-Xlog:aot,cds"
                };
            } else if (runMode == RunMode.ASSEMBLY) {
                return new String[] {
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+WhiteBoxAPI", useWb,
                    "-XX:+AOTClassLinking",
                    "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                    "-Xlog:aot,cds"
                };
            } else {
                return new String[] {
                    "-XX:+UnlockDiagnosticVMOptions",
                    "-XX:+WhiteBoxAPI", useWb,
                    "-agentlib:SimpleClassFileLoadHook=LoadMe,beforeHook,after_Hook",
                    "-Xlog:aot,cds"
                };
            }
        }

        @Override
        public String[] appCommandLine(RunMode runMode) {
            if (runMode == RunMode.TRAINING) {
                return new String[] { mainClass, "" + ClassFileLoadHook.TestCaseId.SHARING_OFF_CFLH_ON };
            } else {
                return new String[] { mainClass, "" + ClassFileLoadHook.TestCaseId.SHARING_ON_CFLH_ON };
            }
        }

        @Override
        public void checkExecution(OutputAnalyzer out, RunMode runMode) {
            if (runMode == RunMode.PRODUCTION) {
                out.shouldContain("AOT cache has aot-linked classes. It cannot be used when JVMTI ClassFileLoadHook is in use.");
                out.shouldNotHaveExitValue(0);
            }
        }
    }
}
