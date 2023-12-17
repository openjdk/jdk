/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jtreg.SkippedException;

/*
 * @test
 * @bug 8213445
 * @summary Test dumping of dynamic shared symbols using jcmd
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 *          /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 *          /test/hotspot/jtreg/runtime/cds/appcds/jcmd
 * @build JCmdTestLingeredApp Hello jdk.test.lib.apps.LingeredApp
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar loadclass.jar JCmdTestLingeredApp
 *             jdk.test.lib.apps.LingeredApp jdk.test.lib.apps.LingeredApp$1 jdk.test.lib.apps.LingeredApp$SteadyStateLock
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar WhiteBox.jar jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=500 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:./WhiteBox.jar DynamicSharedSymbols
 */

import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.helpers.ClassFileInstaller;
import jdk.test.whitebox.WhiteBox;

public class DynamicSharedSymbols extends DynamicArchiveTestBase {

    public static void main(String[] args) throws Exception {
        runTest(DynamicSharedSymbols::testDefaultBase);
    }

    static void testDefaultBase() throws Exception {
        String topArchiveName = getNewArchiveName("top");
        doTest(topArchiveName);
    }

    private static void doTest(String topArchiveName) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        if (!wb.isSharingEnabled()) {
            throw new SkippedException("Sharing is not enabled, test is skipped.");
        }

        String appJar = ClassFileInstaller.getJarPath("loadclass.jar");

        // Create a dynamic archive.
        String dynamicDumpArg = "-XX:ArchiveClassesAtExit=" + topArchiveName;
        JCmdTestLingeredApp theApp = new JCmdTestLingeredApp();
        LingeredApp.startApp(theApp, dynamicDumpArg,
                             "-Xlog:cds,cds+dynamic=info",
                             "-cp", appJar);
        long pid = theApp.getPid();
        LingeredApp.stopApp(theApp);

        // Run with dynamic archive.
        String sharedArchiveArg = "-XX:SharedArchiveFile=" + topArchiveName;
        theApp = new JCmdTestLingeredApp();
        LingeredApp.startApp(theApp,
                             "-Xshare:on", sharedArchiveArg,
                             "-Xlog:cds,class+load",
                             "-cp", appJar);
        pid = theApp.getPid();


        // Use jcmd to dump the symbols of the above process running
        // with dynamic shared archive.
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(new String[] {JDKToolFinder.getJDKTool("jcmd"), Long.toString(pid), "VM.symboltable", "-verbose"});
        OutputAnalyzer output = CDSTestUtils.executeAndLog(pb, "jcmd-symboltable");
        output.shouldContain("17 3: jdk/test/lib/apps\n");  // 3 because a TempSymbol will be found in the TempSymbolCleanupDelayer queue.
                   // Note: we might want to drain the queue before CDS dumps but this is correct for now, unless the queue length changes.
        output.shouldContain("Dynamic shared symbols:\n");
        output.shouldContain("5 65535: Hello\n");

        LingeredApp.stopApp(theApp);
    }
}
