/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /test/lib /runtime/CommandLine/OptionsValidation/common
 * @modules java.base/jdk.internal.misc
 *          java.management
 *          jdk.attach/sun.tools.attach
 * @run main LimitSharedSizes
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import optionsvalidation.JVMOptionsUtils;

public class LimitSharedSizes {
    static enum Result {
        OUT_OF_RANGE,
        TOO_SMALL,
        VALID,
        VALID_ARCHIVE
    }

    static enum Region {
        RO, RW, MD, MC
    }

    private static final boolean fitsRange(String name, String value) throws RuntimeException {
        boolean fits = true;
        try {
            fits = JVMOptionsUtils.fitsRange(name, value);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return fits;
    }

    private static class SharedSizeTestData {
        public String optionName;
        public String optionValue;
        public Result optionResult;

        public SharedSizeTestData(Region region, String value) {
            optionName = "-XX:"+getName(region);
            optionValue = value;
            if (fitsRange(getName(region), value) == false) {
                optionResult = Result.OUT_OF_RANGE;
            } else {
                optionResult = Result.TOO_SMALL;
            }
        }

        public SharedSizeTestData(Region region, String value, Result result) {
            optionName = "-XX:"+getName(region);
            optionValue = value;
            optionResult = result;
        }

        private String getName(Region region) {
            String name;
            switch (region) {
                case RO:
                    name = "SharedReadOnlySize";
                    break;
                case RW:
                    name = "SharedReadWriteSize";
                    break;
                case MD:
                    name = "SharedMiscDataSize";
                    break;
                case MC:
                    name = "SharedMiscCodeSize";
                    break;
                default:
                    name = "Unknown";
                    break;
            }
            return name;
        }

        public Result getResult() {
            return optionResult;
        }
    }

    private static final SharedSizeTestData[] testTable = {
        // Too small of a region size should not cause a vm crash.
        // It should result in an error message either like the following #1:
        // The shared miscellaneous code space is not large enough
        // to preload requested classes. Use -XX:SharedMiscCodeSize=
        // to increase the initial size of shared miscellaneous code space.
        // or #2:
        // The shared miscellaneous code space is outside the allowed range
        new SharedSizeTestData(Region.RO, "4M"),
        new SharedSizeTestData(Region.RW, "4M"),
        new SharedSizeTestData(Region.MD, "50k"),
        new SharedSizeTestData(Region.MC, "20k"),

        // these values are larger than default ones, and should
        // be acceptable and not cause failure
        new SharedSizeTestData(Region.RO, "20M", Result.VALID),
        new SharedSizeTestData(Region.RW, "20M", Result.VALID),
        new SharedSizeTestData(Region.MD, "20M", Result.VALID),
        new SharedSizeTestData(Region.MC, "20M", Result.VALID),

        // test with sizes which just meet the minimum required sizes
        // the following tests also attempt to use the shared archive
        new SharedSizeTestData(Region.RO, Platform.is64bit() ? "10M":"9M", Result.VALID_ARCHIVE),
        new SharedSizeTestData(Region.RW, Platform.is64bit() ? "12M":"7M", Result.VALID_ARCHIVE),
        new SharedSizeTestData(Region.MD, Platform.is64bit() ? "4M":"2M", Result.VALID_ARCHIVE),
        new SharedSizeTestData(Region.MC, "120k", Result.VALID_ARCHIVE),
    };

    public static void main(String[] args) throws Exception {
        int counter = 0;
        for (SharedSizeTestData td : testTable) {
            String fileName = "LimitSharedSizes" + counter + ".jsa";
            counter++;

            String option = td.optionName + "=" + td.optionValue;
            System.out.println("testing option number <" + counter + ">");
            System.out.println("testing option <" + option + ">");

            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
               "-XX:+UnlockDiagnosticVMOptions",
               "-XX:SharedArchiveFile=./" + fileName,
               option,
               "-Xshare:dump");

            OutputAnalyzer output = new OutputAnalyzer(pb.start());

            switch (td.getResult()) {
                case VALID:
                case VALID_ARCHIVE:
                {
                  output.shouldNotContain("space is not large enough");
                  output.shouldHaveExitValue(0);

                  if (td.getResult() == Result.VALID_ARCHIVE) {
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
                               continue;
                          }
                      }
                      output.shouldHaveExitValue(0);
                  }
                }
                break;
                case TOO_SMALL:
                {
                    output.shouldContain("space is not large enough");
                    output.shouldHaveExitValue(2);
                }
                break;
                case OUT_OF_RANGE:
                {
                    output.shouldContain("outside the allowed range");
                    output.shouldHaveExitValue(1);
                }
                break;
            }
        }
    }
}
