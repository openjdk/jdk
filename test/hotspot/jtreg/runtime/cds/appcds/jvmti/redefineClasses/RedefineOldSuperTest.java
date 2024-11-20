/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8342303
 * @summary Redefine a shared old super class loaded by the app loader. The vtable of its archived child class must be updated
 * @library /test/lib
 *          /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/jvmti
 * @requires vm.jvmti
 * @compile ../../test-classes/OldSuper.jasm
 * @build RedefineOldSuperTest
 *        RedefineOldSuperApp
 *        NewChild
 * @run driver RedefineClassHelper
 * @run driver RedefineOldSuperTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class RedefineOldSuperTest {
    public static String appClasses[] = {
        "OldSuper",
        "NewChild",
        "RedefineOldSuperApp",
        "Util",
    };
    public static String sharedClasses[] = TestCommon.concat(appClasses);

    public static void main(String[] args) throws Throwable {
        runTest();
    }

    public static void runTest() throws Throwable {
        String appJar =
            ClassFileInstaller.writeJar("RedefineClassApp.jar", appClasses);

        String agentCmdArg = "-javaagent:redefineagent.jar";

        TestCommon.testDump(appJar, sharedClasses, "-Xlog:cds,cds+class=debug");

        OutputAnalyzer out = TestCommon.execAuto("-cp", appJar,
                "-XX:+UnlockDiagnosticVMOptions",
                "-Xlog:cds=info,class+load",
                agentCmdArg,
               "RedefineOldSuperApp", appJar);
        out.reportDiagnosticSummary();
        TestCommon.checkExec(out);
    }
}
