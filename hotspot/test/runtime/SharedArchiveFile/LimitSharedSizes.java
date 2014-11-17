/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

/* @test LimitSharedSizes
 * @summary Test handling of limits on shared space size
 * @library /testlibrary
 * @run main LimitSharedSizes
 */

import com.oracle.java.testlibrary.*;

public class LimitSharedSizes {
    private static class SharedSizeTestData {
        public String optionName;
        public String optionValue;
        public String expectedErrorMsg;

        public SharedSizeTestData(String name, String value, String msg) {
            optionName = name;
            optionValue = value;
            expectedErrorMsg = msg;
        }
    }

    private static final SharedSizeTestData[] testTable = {
        // values in this part of the test table should cause failure
        // (shared space sizes are deliberately too small)
        new SharedSizeTestData("-XX:SharedReadOnlySize", "4M",      "read only"),
        new SharedSizeTestData("-XX:SharedReadWriteSize","4M",      "read write"),

        // Known issue, JDK-8038422 (assert() on Windows)
        // new SharedSizeTestData("-XX:SharedMiscDataSize", "500k",    "miscellaneous data"),

        // Too small of a misc code size should not cause a vm crash.
        // It should result in the following error message:
        // The shared miscellaneous code space is not large enough
        // to preload requested classes. Use -XX:SharedMiscCodeSize=
        // to increase the initial size of shared miscellaneous code space.
        new SharedSizeTestData("-XX:SharedMiscCodeSize", "20k",     "miscellaneous code"),

        // these values are larger than default ones, but should
        // be acceptable and not cause failure
        new SharedSizeTestData("-XX:SharedReadOnlySize",    "20M", null),
        new SharedSizeTestData("-XX:SharedReadWriteSize",   "20M", null),
        new SharedSizeTestData("-XX:SharedMiscDataSize",    "20M", null),
        new SharedSizeTestData("-XX:SharedMiscCodeSize",    "20M", null)
    };

    public static void main(String[] args) throws Exception {
        String fileName = "test.jsa";

        for (SharedSizeTestData td : testTable) {
            String option = td.optionName + "=" + td.optionValue;
            System.out.println("testing option <" + option + ">");

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:SharedArchiveFile=./" + fileName,
               option,
               "-Xshare:dump");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());

            if (td.expectedErrorMsg != null) {
                output.shouldContain("The shared " + td.expectedErrorMsg
                    + " space is not large enough");

                output.shouldHaveExitValue(2);
            } else {
                output.shouldNotContain("space is not large enough");
                output.shouldHaveExitValue(0);
            }
        }
    }
}
