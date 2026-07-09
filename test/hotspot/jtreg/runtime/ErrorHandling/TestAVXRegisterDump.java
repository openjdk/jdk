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
 */

/*
 * @test
 * @summary Test that YMM and ZMM registers are correctly dumped in hs_err for different UseAVX settings
 * @library /test/lib
 * @requires os.family == "linux" & os.arch == "amd64"
 * @requires vm.debug == true
 * @modules java.base/jdk.internal.misc
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run driver TestAVXRegisterDump
 */

// Note: this test can only run on debug since it relies on VMError::controlled_crash() which
// only exists in debug builds.

import java.io.File;
import java.util.regex.Pattern;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestAVXRegisterDump {

    public static void main(String[] args) throws Exception {

        if (args.length > 0 && args[0].equals("crash")) {
            WhiteBox.getWhiteBox().controlledCrash(2);
            throw new RuntimeException("Still alive?");
        }

        // Test UseAVX=1 (XMM only)
        testWithUseAVX(1);

        // Test UseAVX=2 (YMM)
        testWithUseAVX(2);

        // Test UseAVX=3 (ZMM + K masks if available)
        testWithUseAVX(3);
    }

    static void testWithUseAVX(int useAVX) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
            "-Xbootclasspath/a:.",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+WhiteBoxAPI",
            "-XX:UseAVX=" + useAVX,
            "-XX:-CreateCoredumpOnCrash",
            "-Xmx100M",
            TestAVXRegisterDump.class.getName(), "crash");

        OutputAnalyzer output = new OutputAnalyzer(pb.start());
        output.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");

        File hsErrFile = HsErrFileUtils.openHsErrFileFromOutput(output);
        validateRegisterContent(hsErrFile, useAVX);
    }

    static Pattern[] createRegisterPatterns(String regType, int count) {
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            // Create regex pattern to match entire register line (e.g., "XMM[0]=0xHEX 0xHEX")
            // Used with Matcher.matches() which requires matching the entire line
            patterns[i] = Pattern.compile(regType + "\\[" + i + "\\]=.*");
        }
        return patterns;
    }

    static void validateRegisterContent(File hsErrFile, int useAVX) throws Exception {
        if (useAVX == 1) {
            validateRegistersUseAVX1(hsErrFile);
        } else if (useAVX == 2) {
            validateRegistersUseAVX2(hsErrFile);
        } else if (useAVX == 3) {
            validateRegistersUseAVX3(hsErrFile);
        }
    }

    static void validateRegistersUseAVX1(File hsErrFile) throws Exception {
        // UseAVX=1: XMM registers only (0-15)
        Pattern[] positivePatterns = createRegisterPatterns("XMM", 16);
        Pattern[] negativePatterns = new Pattern[] {
            Pattern.compile("YMM\\[.*\\]=.*"),
            Pattern.compile("ZMM\\[.*\\]=.*"),
        };
        HsErrFileUtils.checkHsErrFileContent(hsErrFile, positivePatterns, negativePatterns, false, false);
    }

    static void validateRegistersUseAVX2(File hsErrFile) throws Exception {
        // UseAVX=2: YMM registers only (0-15)
        Pattern[] positivePatterns = createRegisterPatterns("YMM", 16);
        Pattern[] negativePatterns = new Pattern[] {
            Pattern.compile("XMM\\[.*\\]=.*"),
            Pattern.compile("ZMM\\[.*\\]=.*"),
        };
        HsErrFileUtils.checkHsErrFileContent(hsErrFile, positivePatterns, negativePatterns, false, false);
    }

    static void validateRegistersUseAVX3(File hsErrFile) throws Exception {
        // UseAVX=3: ZMM + K masks (if available) or fallback to YMM
        // Try ZMM first, then fallback to YMM if CPU doesn't support AVX-512
        try {
            Pattern[] zmmPatterns = createRegisterPatterns("ZMM", 32);
            Pattern[] zmmNegativePatterns = new Pattern[] {
                Pattern.compile("XMM\\[.*\\]=.*"),
            };
            HsErrFileUtils.checkHsErrFileContent(hsErrFile, zmmPatterns, zmmNegativePatterns, false, false);

            Pattern[] kPatterns = createRegisterPatterns("K", 8);
            HsErrFileUtils.checkHsErrFileContent(hsErrFile, kPatterns, null, false, false);
        } catch (RuntimeException e) {
            // If ZMM not found, try YMM
            Pattern[] ymmPatterns = createRegisterPatterns("YMM", 16);
            Pattern[] ymmNegativePatterns = new Pattern[] {
                Pattern.compile("XMM\\[.*\\]=.*"),
            };
            HsErrFileUtils.checkHsErrFileContent(hsErrFile, ymmPatterns, ymmNegativePatterns, false, false);
        }
    }
}
