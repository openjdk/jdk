/*
 * Copyright (c) 2025 Red Hat, Inc.
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
 * @bug 8350596
 * @key cgroups
 * @requires os.family == "linux"
 * @requires vm.flagless
 * @library /testlibrary /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI MaxRAMPercentage
 */

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jdk.test.whitebox.WhiteBox;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class MaxRAMPercentage {

    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        if (wb.isContainerized()) {
            throw new RuntimeException("Test failed! Expected not containerized on plain Linux.");
        }
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder("-XX:+PrintFlagsFinal", "-version");
        OutputAnalyzer out = new OutputAnalyzer(pb.start());
        out.shouldHaveExitValue(0);

        String maxRamPercentage = getFlagValue("MaxRAMPercentage", out.getStdout());
        // expect a default of 25%
        if (!maxRamPercentage.startsWith("25")) {
            throw new RuntimeException("Test failed! Expected MaxRAMPercentage to be 25% but got: " + maxRamPercentage);
        }
        System.out.println("PASS. Got expected MaxRAMPercentage=" + maxRamPercentage);
    }

    private static String getFlagValue(String flag, String where) {
        Matcher m = Pattern.compile(flag + "\\s+:?= (\\d+\\.\\d+)").matcher(where);
        if (!m.find()) {
            throw new RuntimeException("Could not find value for flag " + flag + " in output string");
        }
        return m.group(1);
    }

}
