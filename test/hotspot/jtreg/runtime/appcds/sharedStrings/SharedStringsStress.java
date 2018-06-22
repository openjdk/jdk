/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @requires vm.cds.archived.java.heap
 * @library /test/hotspot/jtreg/runtime/appcds /test/lib
 * @modules jdk.jartool/sun.tools.jar
 * @build HelloString
 * @run main SharedStringsStress
 */
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class SharedStringsStress {
    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.build("SharedStringsStress", "HelloString");

        String sharedArchiveConfigFile = System.getProperty("user.dir") + File.separator + "SharedStringsStress_gen.txt";
        try (FileOutputStream fos = new FileOutputStream(sharedArchiveConfigFile)) {
            PrintWriter out = new PrintWriter(new OutputStreamWriter(fos));
            out.println("VERSION: 1.0");
            out.println("@SECTION: String");
            out.println("31: shared_test_string_unique_14325");
            for (int i=0; i<100000; i++) {
                String s = "generated_string " + i;
                out.println(s.length() + ": " + s);
            }
            out.close();
        }

        OutputAnalyzer dumpOutput = TestCommon.dump(appJar, TestCommon.list("HelloString"),
                                                    "-XX:SharedArchiveConfigFile=" + sharedArchiveConfigFile);
        TestCommon.checkDump(dumpOutput);
        OutputAnalyzer execOutput = TestCommon.exec(appJar, "HelloString");
        TestCommon.checkExec(execOutput);
    }
}
