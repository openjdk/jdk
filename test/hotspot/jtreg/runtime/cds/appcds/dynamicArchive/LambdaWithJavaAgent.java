/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test dumping lambda proxy class with java agent transforming
 *          its interface.
 * @requires vm.cds
 * @requires vm.jvmti
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/dynamicArchive/test-classes
 * @build LambdaContainsOldInfApp sun.hotspot.WhiteBox OldProvider LambdaVerification ClassFileVersionTransformer
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar lambda_contains_old_inf.jar LambdaVerification
 *             LambdaContainsOldInfApp OldProvider
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LambdaWithJavaAgent
 */

import jdk.test.lib.helpers.ClassFileInstaller;

public class LambdaWithJavaAgent extends DynamicArchiveTestBase {
    public static String agentClasses[] = {
        ClassFileVersionTransformer.class.getName(),
    };

    public static void main(String[] args) throws Exception {
        runTest(LambdaWithJavaAgent::test);
    }

    static void test() throws Exception {
        String topArchiveName = getNewArchiveName();
        String appJar = ClassFileInstaller.getJarPath("lambda_contains_old_inf.jar");
        String mainClass = "LambdaContainsOldInfApp";
        String wbJar = ClassFileInstaller.getJarPath("WhiteBox.jar");
        String use_whitebox_jar = "-Xbootclasspath/a:" + wbJar;

        String agentJar =
            ClassFileInstaller.writeJar("ClassFileVersionTransformer.jar",
                                        ClassFileInstaller.Manifest.fromSourceFile("../test-classes/ClassFileVersionTransformer.mf"),
                                        agentClasses);
        String useJavaAgent = "-javaagent:" + agentJar + "=OldProvider";

        dump(topArchiveName,
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-Xlog:class+load=debug,cds=debug,cds+dynamic=info",
            use_whitebox_jar,
            "-XX:+AllowArchivingWithJavaAgent",
            useJavaAgent,
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                // Since the java agent has updated the version of the OldProvider class
                // to 50, the OldProvider and the lambda proxy class will not be
                // excluded from the archive.
                output.shouldNotContain("Skipping OldProvider: Old class has been linked")
                      .shouldNotMatch("Skipping.LambdaContainsOldInfApp[$][$]Lambda[$].*0x.*:.*Old.class.has.been.linked")
                      .shouldHaveExitValue(0);
            });

        run(topArchiveName,
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            use_whitebox_jar,
            "-Xlog:class+load=debug",
            "-XX:+AllowArchivingWithJavaAgent",
            useJavaAgent,
            "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                output.shouldContain("[class,load] LambdaContainsOldInfApp source: shared objects file")
                      // Transformed classes during runtime won't be loaded from the archive.
                      .shouldMatch(".class.load. OldProvider.source:.*lambda_contains_old_inf.jar")
                      .shouldMatch(".class.load. LambdaContainsOldInfApp[$][$]Lambda[$].*/0x.*source:.*LambdaContainsOldInf")
                      .shouldHaveExitValue(0);
            });
    }
}
