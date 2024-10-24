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
 * @summary Test loading of shared old class when another class has been redefined.
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes /test/hotspot/jtreg/runtime/cds/appcds/jvmti
 * @requires vm.cds.write.archived.java.heap
 * @requires vm.jvmti
 * @build jdk.test.whitebox.WhiteBox
 *        OldClassAndRedefineClassApp
 *        InstrumentationClassFileTransformer
 *        InstrumentationRegisterClassFileTransformer
 * @compile ../../test-classes/OldSuper.jasm
 *          ../../test-classes/ChildOldSuper.java
 *          ../../test-classes/Hello.java
 * @run driver OldClassAndRedefineClass
 */

import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class OldClassAndRedefineClass {
    public static String bootClasses[] = {
        "jdk.test.whitebox.WhiteBox",
    };
    public static String appClasses[] = {
        "OldClassAndRedefineClassApp",
        "OldSuper",
        "ChildOldSuper",
        "Hello",
    };
    public static String sharedClasses[] = TestCommon.concat(bootClasses, appClasses);

    public static String agentClasses[] = {
        "InstrumentationClassFileTransformer",
        "InstrumentationRegisterClassFileTransformer",
    };

    public static void main(String[] args) throws Throwable {
        runTest();
    }

    public static void runTest() throws Throwable {
        String bootJar =
            ClassFileInstaller.writeJar("OldClassAndRedefineClassBoot.jar", bootClasses);
        String appJar =
            ClassFileInstaller.writeJar("OldClassAndRedefineClassApp.jar", appClasses);
        String agentJar =
            ClassFileInstaller.writeJar("InstrumentationAgent.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("../InstrumentationAgent.mf"),
                                        agentClasses);

        String bootCP = "-Xbootclasspath/a:" + bootJar;

        String agentCmdArg = "-javaagent:" + agentJar;

        OutputAnalyzer out = TestCommon.testDump(appJar, sharedClasses, bootCP, "-Xlog:cds,cds+class=debug");
        out.shouldMatch("klasses.*OldSuper.[*][*].unlinked")
           .shouldMatch("klasses.*ChildOldSuper.[*][*].unlinked");

        out = TestCommon.exec(
                appJar,
                bootCP,
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+AllowArchivingWithJavaAgent",
                "-XX:+WhiteBoxAPI",
                "-Xlog:cds,class+load",
                agentCmdArg,
               "OldClassAndRedefineClassApp");
        out.shouldContain("[class,load] OldSuper source: shared objects file")
           .shouldContain("[class,load] ChildOldSuper source: shared objects file")
           .shouldContain("[class,load] Hello source: __VM_RedefineClasses__");
    }
}
