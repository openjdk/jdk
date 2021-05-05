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
 * @bug 8265465
 * @summary Test jcmd to dump static shared archive.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @modules jdk.jcmd/sun.tools.common:+open
 * @compile ../test-classes/Hello.java JCmdTestDumpBase.java
 * @build sun.hotspot.WhiteBox
 * @build JCmdTestLingeredApp JCmdTestFileSecurity
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=480 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JCmdTestFileSecurity
 */

import java.io.File;
import java.io.IOException;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.apps.LingeredApp;
import jdk.test.lib.Platform;

public class JCmdTestFileSecurity extends JCmdTestDumpBase {

    static void test() throws Exception {
        buildJars();

        LingeredApp app = null;
        long pid;

        int  test_count = 1;
        // Set target dir not accessible, do static dump
        setIsStatic(true);
        print2ln(test_count++ + " Set target dir not accessible, do static dump");
        setKeepArchive(true);
        app = createLingeredApp("-cp", allJars);
        pid = app.getPid();
        String localFileName = "MyStaticDump.jsa";
        test(localFileName, pid, noBoot,  EXPECT_PASS);
        File targetFile = CDSTestUtils.getOutputDirAsFile();
        targetFile.setWritable(false);
        test(localFileName, pid, noBoot,  EXPECT_FAIL);
        targetFile.setWritable(true);
        app.stopApp();
        // MyStaticDump.jsa should exist
        checkFileExistence(localFileName, true);

        // test dynamic versoin
        setIsStatic(false);
        print2ln(test_count++ + " Set target dir not accessible, do dynamic dump");
        setKeepArchive(true);
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        localFileName = "MyDynamicDump.jsa";
        test(localFileName, pid, noBoot,  EXPECT_PASS);
        app.stopApp();
        // cannot dynamically dump twice, restart
        app = createLingeredApp("-cp", allJars, "-XX:+RecordDynamicDumpInfo");
        pid = app.getPid();
        targetFile.setWritable(false);
        test(localFileName, pid, noBoot,  EXPECT_FAIL);
        targetFile.setWritable(true);
        app.stopApp();
        // MyDynamicDump.jsa should exist
        checkFileExistence(localFileName, true);
    }

    public static void main(String... args) throws Exception {
        if (Platform.isWindows()) {
            // ON windows, File operation resulted difference from other OS.
            throw new jtreg.SkippedException("Test skipped on Windows");
        }
        runTest(JCmdTestFileSecurity::test);
    }
}
