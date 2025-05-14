/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Write a lots of shared strings.
 * @requires vm.cds.write.archived.java.heap
 * @library /test/hotspot/jtreg/runtime/cds/appcds /test/lib
 * @build HelloString
 * @run driver/timeout=650 SharedStringsStress
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import jdk.test.lib.cds.CDSOptions;
import jdk.test.lib.cds.CDSTestUtils;
import jdk.test.lib.process.OutputAnalyzer;

public class SharedStringsStress {
    static {
        // EpsilonGC will run out of memory.
        CDSOptions.disableRuntimePrefixForEpsilonGC();
    }
    static String sharedArchiveConfigFile = CDSTestUtils.getOutputDir() + File.separator + "SharedStringsStress_gen.txt";

    public static void main(String[] args) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(sharedArchiveConfigFile)) {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(fos));
            out.println("VERSION: 1.0");
            out.println("@SECTION: String");
            out.println("31: shared_test_string_unique_14325");
            // Create enough entries to require the shared string
            // table to split into two levels of Object arrays. See
            // StringTable::allocate_shared_table() in HotSpot.
            for (int i=0; i<260000; i++) {
                String s = "generated_string " + i;
                out.println(s.length() + ": " + s);
            }
            out.close();
        }

        SharedStringsUtils.run(args, SharedStringsStress::test);
    }

    public static void test(String[] args) throws Exception {
        String vmOptionsPrefix[] = SharedStringsUtils.getChildVMOptionsPrefix();
        String appJar = JarBuilder.build("SharedStringsStress", "HelloString");

        OutputAnalyzer dumpOutput = TestCommon.dump(appJar, TestCommon.list("HelloString"),
            TestCommon.concat(vmOptionsPrefix,
                "-XX:SharedArchiveConfigFile=" + sharedArchiveConfigFile,
                "-Xlog:aot",
                "-Xlog:gc+region+cds",
                "-Xlog:gc+region=trace"));
        TestCommon.checkDump(dumpOutput);
        dumpOutput.shouldContain("string table array (primary)");
        dumpOutput.shouldContain("string table array (secondary)");

        OutputAnalyzer execOutput = TestCommon.exec(appJar,
            TestCommon.concat(vmOptionsPrefix, "-Xlog:aot,cds", "HelloString"));
        TestCommon.checkExec(execOutput);
    }
}
