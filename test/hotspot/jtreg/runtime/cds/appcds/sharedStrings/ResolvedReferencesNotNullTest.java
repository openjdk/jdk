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
 */

/**
 * @test ResolvedReferencesNotNullTest
 * @bug 8313638
 * @summary Testing resolved references array to ensure elements are non-null
 * @requires vm.cds.write.archived.java.heap
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds
 * @build jdk.test.whitebox.WhiteBox ResolvedReferencesWb ResolvedReferencesTestApp
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver ResolvedReferencesNotNullTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class ResolvedReferencesNotNullTest {
    public static void main(String[] args) throws Exception {
        SharedStringsUtils.buildJarAndWhiteBox("ResolvedReferencesWb", "ResolvedReferencesTestApp");
        String appJar = TestCommon.getTestJar(SharedStringsUtils.TEST_JAR_NAME_FULL);
        String whiteboxParam = SharedStringsUtils.getWbParam();

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder("-cp",
                                                                  appJar,
                                                                  whiteboxParam,
                                                                  "-XX:+UnlockDiagnosticVMOptions",
                                                                  "-XX:+WhiteBoxAPI",
                                                                  "ResolvedReferencesWb",
                                                                  "false" // ResolvedReferencesTestApp is not archived
                                                                  );
        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldHaveExitValue(0);

        TestCommon.dump(appJar,
                        TestCommon.list("ResolvedReferencesWb", "ResolvedReferencesTestApp"),
                        TestCommon.concat("-XX:SharedArchiveFile=ResolvedRef.jsa",
                                          "-XX:+UnlockDiagnosticVMOptions",
                                          "-XX:+WhiteBoxAPI",
                                          whiteboxParam));

        // Since ResolvedReferencesTestApp is now archived, all of the strings should be in the resolved
        // references array
        TestCommon.run("-cp",
                       appJar,
                       whiteboxParam,
                       "-XX:SharedArchiveFile=ResolvedRef.jsa",
                       "-XX:+UnlockDiagnosticVMOptions",
                       "-XX:+WhiteBoxAPI",
                       "ResolvedReferencesWb",
                       "true" // ResolvedReferencesTestApp is archived
                       ).assertNormalExit();
    }
}
