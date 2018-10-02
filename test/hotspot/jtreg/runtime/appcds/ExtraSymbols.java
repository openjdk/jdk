/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Adding extra symbols into CDS archive using -XX:SharedArchiveConfigFile
 * @requires vm.cds
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.jartool/sun.tools.jar
 * @compile test-classes/Hello.java
 * @run driver ExtraSymbols
 */

import java.io.*;
import jdk.test.lib.process.OutputAnalyzer;

public class ExtraSymbols {
    static final String CDS_LOGGING = "-Xlog:cds,cds+hashtables";
    public static void main(String[] args) throws Exception {
        String appJar = JarBuilder.getOrCreateHelloJar();

        // 1. Dump without extra symbols.
        OutputAnalyzer output = TestCommon.dump(appJar, TestCommon.list("Hello"),
                                                CDS_LOGGING);
        checkOutput(output);
        int numEntries1 = numOfEntries(output);

        // 2. Dump an archive with extra symbols. All symbols in
        // ExtraSymbols.symbols.txt are valid. Dumping should succeed.
        output = TestCommon.dump(appJar, TestCommon.list("Hello"), CDS_LOGGING,
            "-XX:SharedArchiveConfigFile=" + TestCommon.getSourceFile("ExtraSymbols.symbols.txt"));
        checkOutput(output);
        int numEntries2 = numOfEntries(output);
        if (numEntries2 <= numEntries1) {
            throw new RuntimeException("No extra symbols added to archive");
        }
        output = TestCommon.exec(appJar, "Hello");
        TestCommon.checkExec(output);

        // 3. Dump with invalid symbol files. Dumping should fail.
        String invalid_symbol_files[] = {"ExtraSymbols.invalid_1.txt",
                                         "ExtraSymbols.invalid_2.txt",
                                         "ExtraSymbols.invalid_3.txt"};
        String err_msgs[] = {"Corrupted at line",
                             "wrong version of hashtable dump file",
                             "Corrupted at line"};
        for (int i = 0; i < invalid_symbol_files.length; i++) {
            output = TestCommon.dump(appJar, TestCommon.list("Hello"),
                                     "-XX:SharedArchiveConfigFile=" +
                                     TestCommon.getSourceFile(invalid_symbol_files[i]));
            output.shouldContain("Error occurred during initialization of VM");
            output.shouldContain(err_msgs[i]);
        }
    }

    static int numOfEntries(OutputAnalyzer output) {
        String s = output.firstMatch("Number of entries       : .*");
        String subs[] = s.split("[:]");
        int numEntries = Integer.parseInt(subs[1].trim());
        return numEntries;
    }

    static void checkOutput(OutputAnalyzer output) throws Exception {
        output.shouldContain("Loading classes to share");
        output.shouldContain("Shared symbol table stats -------- base:");
        output.shouldHaveExitValue(0);
    }
}
