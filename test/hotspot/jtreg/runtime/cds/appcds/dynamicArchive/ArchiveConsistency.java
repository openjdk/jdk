/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Corrupt the header CRC fields of the top archive. VM should exit with an error.
 * @requires vm.cds
 * @library /test/lib /test/hotspot/jtreg/runtime/cds/appcds /test/hotspot/jtreg/runtime/cds/appcds/test-classes
 * @build Hello sun.hotspot.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller -jar hello.jar Hello
 * @run driver jdk.test.lib.helpers.ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ArchiveConsistency
 */

import java.io.File;
import java.io.IOException;
import jdk.test.lib.cds.CDSArchiveUtils;
import jdk.test.lib.helpers.ClassFileInstaller;

public class ArchiveConsistency extends DynamicArchiveTestBase {

    public static void main(String[] args) throws Exception {
        runTest(ArchiveConsistency::testCustomBase);
    }

    // Test with custom base archive + top archive
    static void testCustomBase() throws Exception {
        String topArchiveName = getNewArchiveName("top2");
        String baseArchiveName = getNewArchiveName("base");
        TestCommon.dumpBaseArchive(baseArchiveName);
        doTest(baseArchiveName, topArchiveName);
    }

    private static void doTest(String baseArchiveName, String topArchiveName) throws Exception {
        String appJar = ClassFileInstaller.getJarPath("hello.jar");
        String mainClass = "Hello";
        dump2(baseArchiveName, topArchiveName,
             "-Xlog:cds",
             "-Xlog:cds+dynamic=debug",
             "-cp", appJar, mainClass)
            .assertNormalExit(output -> {
                    output.shouldContain("Written dynamic archive 0x");
                });

        File jsa = new File(topArchiveName);
        if (!jsa.exists()) {
            throw new IOException(jsa + " does not exist!");
        }

        // Modify the CRC values in the header of the top archive.
        String modTop = getNewArchiveName("modTopRegionsCrc");
        File copiedJsa = CDSArchiveUtils.copyArchiveFile(jsa, modTop);
        CDSArchiveUtils.modifyAllRegionsCrc(copiedJsa);

        run2(baseArchiveName, modTop,
            "-Xlog:class+load",
            "-Xlog:cds+dynamic=debug,cds=debug",
            "-XX:+VerifySharedSpaces",
            "-cp", appJar, mainClass)
            .assertAbnormalExit(output -> {
                    output.shouldContain("Header checksum verification failed")
                          .shouldContain("Unable to use shared archive")
                          .shouldHaveExitValue(1);
                });
    }
}
