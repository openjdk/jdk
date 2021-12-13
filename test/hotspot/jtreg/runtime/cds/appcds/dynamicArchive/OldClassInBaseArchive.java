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
 * @summary Archive an old class in the base archive and an app class which
 *          uses the old class in the dynamic archive.
 *          The old class should be loaded from the base archive. The app class
 *          should be loaded from the dynamic archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build OldSuperApp sun.hotspot.WhiteBox OldSuper ChildOldSuper GChild
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar old-class-base-archive.jar OldSuperApp OldSuper ChildOldSuper GChild
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. OldClassInBaseArchive
 */

import java.io.File;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.helpers.ClassFileInstaller;

public class OldClassInBaseArchive extends DynamicArchiveTestBase {
    static final String classList = CDSTestUtils.getOutputFileName("classlist");
    static final String appClass = "OldSuperApp";
    static final String baseArchiveClass = "OldSuper";

    public static void main(String[] args) throws Exception {
        runTest(OldClassInBaseArchive::testCustomBase);
    }

    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTestCustomBase(topArchiveName);
    }

    private static void doTestCustomBase(String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("old-class-base-archive.jar");

        // create a custom base archive containing and old class
        OutputAnalyzer output = TestCommon.dump(appJar,
            TestCommon.list("OldSuper"), "-Xlog:class+load,cds+class=debug");
        TestCommon.checkDump(output);
        // Check the OldSuper is being dumped into the base archive.
        output.shouldMatch(".cds.class.*klass.*0x.*app.*OldSuper.*unlinked");

        String baseArchiveName = TestCommon.getCurrentArchiveName();

        // create a dynamic archive with the custom base archive.
        // The old class is in the base archive and will be
        // accessed from OldSuperApp.
        // The OldSuperApp, ChildOldSuper, and GChild classes will be archived
        // in the dynamic archive.
        dump2(baseArchiveName, topArchiveName,
              "-Xlog:cds,cds+dynamic,class+load,cds+class=debug",
              "-cp", appJar,
              appClass)
            .assertNormalExit(out -> {
                    out.shouldContain("OldSuper source: shared objects file")
                       // Check the following classes are being dumped into the dynamic archive.
                       .shouldMatch(".cds,class.*klass.*0x.*app.*OldSuperApp")
                       .shouldMatch(".cds,class.*klass.*0x.*app.*ChildOldSuper")
                       .shouldMatch(".cds,class.*klass.*0x.*app.*GChild");
                });

        // Run with both base and dynamic archives. The OldSuper class
        // should be loaded from the base archive. The OldSuperApp
        // and related classes should be loaded from the dynamic archive.
        run2(baseArchiveName, topArchiveName,
              "-Xlog:cds,cds+dynamic,class+load",
              "-cp", appJar,
              appClass)
            .assertNormalExit(out -> {
                    out.shouldContain("OldSuper source: shared objects file")
                       .shouldContain("OldSuperApp source: shared objects file (top)")
                       .shouldContain("ChildOldSuper source: shared objects file (top)")
                       .shouldContain("GChild source: shared objects file (top)");
                });
    }
}
