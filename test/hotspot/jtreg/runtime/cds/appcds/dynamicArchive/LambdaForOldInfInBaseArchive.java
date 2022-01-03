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
 * @bug 8276184
 * @summary Archive an old interface in the base archive and an app class which
 *          uses the old interface via a lambda expression in the dynamic archive.
 *          The lambda proxy class of the app class should be in the dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build LambdaContainsOldInfApp sun.hotspot.WhiteBox OldProvider
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar old-inf-base-archive.jar LambdaContainsOldInfApp OldProvider
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. LambdaForOldInfInBaseArchive
 */

import java.io.File;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class LambdaForOldInfInBaseArchive extends DynamicArchiveTestBase {
    static final String classList = CDSTestUtils.getOutputFileName("classlist");
    static final String appClass = "LambdaContainsOldInfApp";
    static final String baseArchiveClass = "OldProvider";

    public static void main(String[] args) throws Exception {
        runTest(LambdaForOldInfInBaseArchive::testCustomBase);
    }

    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTestCustomBase(topArchiveName);
    }

    private static void doTestCustomBase(String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("old-inf-base-archive.jar");

        // create a custom base archive containing and old interface
        OutputAnalyzer output = TestCommon.dump(appJar,
            TestCommon.list("OldProvider"), "-Xlog:class+load,cds+class=debug");
        TestCommon.checkDump(output);
        // Check that the OldProvider is being dumped into the base archive.
        output.shouldMatch(".cds,class.*klass.*0x.*app.*OldProvider.*unlinked");

        String baseArchiveName = TestCommon.getCurrentArchiveName();

        // create a dynamic archive with the custom base archive.
        // The old interface is in the base archive and will be
        // accessed using a lambda expression of LambdaContainsOldInfApp.
        // The lambda proxy class and the app class will be archived in the dynamic archive.
        dump2(baseArchiveName, topArchiveName,
              "-Xlog:cds,cds+dynamic,class+load,cds+class=debug",
              "-cp", appJar,
              appClass)
            .assertNormalExit(out -> {
                    out.shouldContain("OldProvider source: shared objects file")
                       .shouldMatch("Archiving hidden LambdaContainsOldInfApp[$][$]Lambda[$][\\d+]*");
                });

        // Run with both base and dynamic archives. The OldProvider class
        // should be loaded from the base archive. The LambdaContainsOldInfApp
        // and its lambda proxy class should be loaded from the dynamic archive.
        run2(baseArchiveName, topArchiveName,
              "-Xlog:cds,cds+dynamic,class+load",
              "-cp", appJar,
              appClass)
            .assertNormalExit(out -> {
                    out.shouldContain("OldProvider source: shared objects file")
                       .shouldContain("LambdaContainsOldInfApp source: shared objects file (top)")
                       .shouldMatch(".class.load. LambdaContainsOldInfApp[$][$]Lambda[$].*/0x.*source:.*shared.*objects.*file.*(top)");
                });
    }
}
