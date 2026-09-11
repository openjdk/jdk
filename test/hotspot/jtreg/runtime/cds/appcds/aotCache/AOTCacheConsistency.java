/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @summary AOTCacheConsistency This test checks that there is a CRC validation of the AOT Cache regions.
 * @bug 8382166
 * @requires vm.cds.supports.aot.class.linking
 * @library /test/lib /test/setup_aot
 * @build jdk.test.whitebox.WhiteBox AOTCacheConsistency HelloWorld
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar app.jar HelloWorld
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI AOTCacheConsistency
 */

import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.cds.SimpleCDSAppTester;
import jdk.test.lib.process.OutputAnalyzer;
import java.io.File;

public class AOTCacheConsistency {
    public static void main(String args[]) throws Exception {
        // Train and run the app
        SimpleCDSAppTester tester = SimpleCDSAppTester.of("AOTCacheConsistency")
            .classpath("app.jar")
            .appCommandLine("HelloWorld")
            .setProductionChecker((OutputAnalyzer out) -> {
                    out.shouldContain("HelloWorld");
                })
            .runAOTWorkflow();

        String aotCache = tester.aotCacheFile();

        String[] regions = CDSArchiveUtils.getRegions();
        String orig = aotCache + ".orig";
        CDSArchiveUtils.copyArchiveFile(new File(aotCache), orig); // save original copy

        // Modify each of the region individually. The production should fail to run
        // with these args;
        String extraVMArgs[] = {"-XX:+VerifySharedSpaces", "-XX:AOTMode=on"};
        tester.setCheckExitValue(false);

        for (int i = 0; i < regions.length; i++) {
            File f = CDSArchiveUtils.copyArchiveFile(new File(orig), aotCache);
            System.out.println("\n=======\nTesting region " + i + " = " + regions[i]);
            if (CDSArchiveUtils.modifyRegionContent(i, f)) {
                tester.setProductionChecker((OutputAnalyzer out) -> {
                        out.shouldContain("Checksum verification failed.");
                    });
                tester.rerunProduction(extraVMArgs);
            }
        }
    }
}