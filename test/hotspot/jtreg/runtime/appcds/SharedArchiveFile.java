/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary The diagnostic option, -XX:SharedArchiveFile can be unlocked using -XX:+UseAppCDS
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/Hello.java
 * @run main SharedArchiveFile
 */

import jdk.test.lib.Platform;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import java.util.Properties;

public class SharedArchiveFile {
    public static void main(String[] args) throws Exception {
        boolean isProduct = !Platform.isDebugBuild();
        String appJar = JarBuilder.getOrCreateHelloJar();

        // 1) Using -XX:SharedArchiveFile without -XX:+UseAppCDS should fail
        //    on product binary without -XX:+UnlockDiagnosticVMOptions.
        if (isProduct) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(true,
                "-XX:SharedArchiveFile=./SharedArchiveFile.jsa", "-Xshare:dump");
            OutputAnalyzer out = CDSTestUtils.executeAndLog(pb, "dump");
            out.shouldContain("Error: VM option 'SharedArchiveFile' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
        }

        // 2) Dumping with -XX:+UnlockDiagnosticVMOptions -XX:SharedArchiveFile
        //    should always succeed.
        CDSTestUtils.createArchive("-XX:+UnlockDiagnosticVMOptions")
            .shouldContain("Dumping");

        // 3) Using -XX:SharedArchiveFile with -XX:+UseAppCDS should work
        //    on product binary by default.
        OutputAnalyzer output3 = TestCommon.dump(appJar, TestCommon.list("Hello"));
        output3.shouldContain("Dumping");
        output3 = TestCommon.exec(appJar, "Hello");
        TestCommon.checkExec(output3, "Hello World");

        // 4) Using -XX:+UseAppCDS should not affect other diagnostic flags,
        //    such as LogEvents
        OutputAnalyzer output4 = TestCommon.exec(appJar, "-XX:+LogEvents", "Hello");
        if (isProduct) {
            output4.shouldContain("Error: VM option 'LogEvents' is diagnostic and must be enabled via -XX:+UnlockDiagnosticVMOptions.");
        } else {
            TestCommon.checkExec(output4, "Hello World");
        }

        // 5) 8066921 - Extra -XX:+UseAppCDS
        TestCommon.testDump(appJar, TestCommon.list("Hello"), "-XX:+UseAppCDS");
        OutputAnalyzer output5 = TestCommon.exec(appJar, "-XX:+UseAppCDS", "Hello");
        TestCommon.checkExec(output5);
    }
}
