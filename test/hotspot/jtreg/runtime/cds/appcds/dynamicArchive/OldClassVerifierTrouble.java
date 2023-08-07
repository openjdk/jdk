/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8312181
 * @summary Archive an old class, which fails verification and is a supertype of another
 *          class, in the base archive. Perform a dynamic dump with the base archive
 *          containing the old class and its subclass in "unlinked" state. VM should
 *          not crash in this scenario.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds/test-classes /test/hotspot/jtreg/runtime/cds/appcds
 * @compile test-classes/VerifierTroublev49.jasm
 * @compile test-classes/ChildOldSuper.java
 * @compile test-classes/VerifierTroubleApp.java
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar oldsuper-fail-verifier.jar ChildOldSuper VerifierTroublev49 VerifierTroubleApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. OldClassVerifierTrouble
 */

import java.io.File;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class OldClassVerifierTrouble extends DynamicArchiveTestBase {
    static final String appClass = "VerifierTroubleApp";
    static final String baseArchiveClass = "OldSuperVerifierTrouble";

    public static void main(String[] args) throws Exception {
        runTest(OldClassVerifierTrouble::testCustomBase);
    }

    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTestCustomBase(topArchiveName);
    }

    private static void doTestCustomBase(String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("oldsuper-fail-verifier.jar");

        // create a custom base archive containing an old class
        OutputAnalyzer output = TestCommon.dump(appJar,
            TestCommon.list("VerifierTroubleApp", "VerifierTroublev49", "ChildOldSuper"),
            "-Xlog:class+load,cds+class=debug");
        TestCommon.checkDump(output);
        // Check the ChildOldSuper and VerifierTroublev49 are being dumped into the base archive.
        output.shouldMatch(".cds.class.*klass.*0x.*app.*ChildOldSuper.*unlinked")
              .shouldMatch(".cds.class.*klass.*0x.*app.*VerifierTroublev49.*unlinked");

        String baseArchiveName = TestCommon.getCurrentArchiveName();

        // create a dynamic archive with the custom base archive.
        // The old class is in the base archive and will be
        // accessed from VerifierTroubleApp.
        // Linking VerifierTroublev49 would result in java.lang.VerifyError.
        dump2(baseArchiveName, topArchiveName,
              "-Xlog:cds,cds+dynamic,class+load,cds+class=debug",
              "-cp", appJar,
              appClass)
            .assertAbnormalExit(out -> {
                    out.shouldContain("VerifierTroublev49 source: shared objects file")
                       .shouldContain("ChildOldSuper source: shared objects file")
                       .shouldContain("java.lang.VerifyError: " +
                                      "(class: VerifierTroublev49, method: doit signature: ()Ljava/lang/String;)" +
                                      " Wrong return type in function");
                });
    }
}
