/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
    static enum Region {
        RO, RW, MD, MC
    }

    private static class SharedSizeTestData {
        public String optionName;
        public String optionValue;
        public String expectedErrorMsg;

        public SharedSizeTestData(Region region, String value, String msg) {
            optionName = getName(region);
            optionValue = value;
            expectedErrorMsg = msg;
        }

        public SharedSizeTestData(Region region, String msg) {
            optionName = getName(region);
            optionValue = getValue(region);
            expectedErrorMsg = msg;
        }

        private String getName(Region region) {
            String name;
            switch (region) {
                case RO:
                    name = "-XX:SharedReadOnlySize";
                    break;
                case RW:
                    name = "-XX:SharedReadWriteSize";
                    break;
                case MD:
                    name = "-XX:SharedMiscDataSize";
                    break;
                case MC:
                    name = "-XX:SharedMiscCodeSize";
                    break;
                default:
                    name = "Unknown";
                    break;
            }
            return name;
        }

        private String getValue(Region region) {
            String value;
            switch (region) {
                case RO:
                    value = Platform.is64bit() ? "9M" : "8M";
                    break;
                case RW:
                    value = Platform.is64bit() ? "12M" : "7M";
                    break;
                case MD:
                    value = Platform.is64bit() ? "4M" : "2M";
                    break;
                case MC:
                    value = "120k";
                    break;
                default:
                    value = "0M";
                    break;
            }
            return value;
        }
    }

    private static final SharedSizeTestData[] testTable = {
        // Too small of a region size should not cause a vm crash.
        // It should result in an error message like the following:
        // The shared miscellaneous code space is not large enough
        // to preload requested classes. Use -XX:SharedMiscCodeSize=
        // to increase the initial size of shared miscellaneous code space.
        new SharedSizeTestData(Region.RO, "4M",   "read only"),
        new SharedSizeTestData(Region.RW, "4M",   "read write"),
        new SharedSizeTestData(Region.MD, "50k",  "miscellaneous data"),
        new SharedSizeTestData(Region.MC, "20k",  "miscellaneous code"),

        // these values are larger than default ones, but should
        // be acceptable and not cause failure
        new SharedSizeTestData(Region.RO, "20M", null),
        new SharedSizeTestData(Region.RW, "20M", null),
        new SharedSizeTestData(Region.MD, "20M", null),
        new SharedSizeTestData(Region.MC, "20M", null),

        // test with sizes which just meet the minimum required sizes
        // the following tests also attempt to use the shared archive
        new SharedSizeTestData(Region.RO, "UseArchive"),
        new SharedSizeTestData(Region.RW, "UseArchive"),
        new SharedSizeTestData(Region.MD, "UseArchive"),
        new SharedSizeTestData(Region.MC, "UseArchive")
    };

    public static void main(String[] args) throws Exception {
        int counter = 0;
        for (SharedSizeTestData td : testTable) {
            String fileName = "LimitSharedSizes" + counter + ".jsa";
            counter++;

            String option = td.optionName + "=" + td.optionValue;
            System.out.println("testing option <" + option + ">");

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:SharedArchiveFile=./" + fileName,
               option,
               "-Xshare:dump");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());

            if (td.expectedErrorMsg != null) {
                if (!td.expectedErrorMsg.equals("UseArchive")) {
                    output.shouldContain("The shared " + td.expectedErrorMsg
                        + " space is not large enough");

                    output.shouldHaveExitValue(2);
                } else {
                    output.shouldNotContain("space is not large enough");
                    output.shouldHaveExitValue(0);

                    // try to use the archive
                    pb = ProcessTools.createJavaProcessBuilder(
                       "-XX:+UnlockDiagnosticVMOptions",
                       "-XX:SharedArchiveFile=./" + fileName,
                       "-XX:+PrintSharedArchiveAndExit",
                       "-version");

                    try {
                        output = new OutputAnalyzer(pb.start());
                        output.shouldContain("archive is valid");
                    } catch (RuntimeException e) {
                        // if sharing failed due to ASLR or similar reasons,
                        // check whether sharing was attempted at all (UseSharedSpaces)
                        if ((output.getOutput().contains("Unable to use shared archive") ||
                             output.getOutput().contains("Unable to map ReadOnly shared space at required address.") ||
                             output.getOutput().contains("Unable to map ReadWrite shared space at required address.") ||
                             output.getOutput().contains("Unable to reserve shared space at required address")) &&
                             output.getExitValue() == 1) {
                             System.out.println("Unable to use shared archive: test not executed; assumed passed");
                             return;
                        }
                    }
                    output.shouldHaveExitValue(0);
                }
            } else {
                output.shouldNotContain("space is not large enough");
                output.shouldHaveExitValue(0);
            }
        }
    }
}
