/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8304954
 * @summary Code cache reservation should gracefully downgrade to using smaller pages if the code cache size is too small to host the requested page size.
 * @requires os.family == "linux"
 * @requires vm.gc != "Z"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseLargePages -XX:LargePageSizeInBytes=1g compiler.codecache.CheckLargePages
 */

package compiler.codecache;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

import java.util.Arrays;
import java.util.List;

public class CheckLargePages {
    private final static WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        final boolean largePages = WHITE_BOX.getBooleanVMFlag("UseLargePages");
        final long largePageSize = WHITE_BOX.getVMLargePageSize();
        if (largePages && (largePageSize == 1024 * 1024 * 1024)) {
            ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                    "-XX:+UseLargePages",
                    "-XX:+SegmentedCodeCache",
                    "-XX:InitialCodeCacheSize=2g",
                    "-XX:ReservedCodeCacheSize=2g",
                    "-XX:LargePageSizeInBytes=1g",
                    "-Xlog:pagesize=info",
                    "-version");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldMatch("Code cache size too small for \\S* pages\\. Reverting to smaller page size \\((\\S*)\\)\\.");
            out.shouldHaveExitValue(0);
            // Parse page sizes to find next biggest page
            String sizes = out.firstMatch("Usable page sizes:([^.]+)", 1);
            List<Long> sizeList = Arrays.stream(sizes.trim().split("\\s*,\\s*")).map(CheckLargePages::parseMemoryString).sorted().toList();
            final int smallerPageSizeIndex = sizeList.indexOf(largePageSize) - 1;
            Asserts.assertGreaterThanOrEqual(smallerPageSizeIndex, 0);
            final long smallerPageSize = sizeList.get(smallerPageSizeIndex);
            // Retrieve reverted page size from code cache warning
            String revertedSizeString = out.firstMatch("Code cache size too small for (\\S*) pages. Reverting to smaller page size \\((\\S*)\\)\\.", 2);
            Asserts.assertEquals(parseMemoryString(revertedSizeString), smallerPageSize);
        } else {
            System.out.println("1GB large pages not supported: UseLargePages=" + largePages +
                    (largePages ? ", largePageSize=" + largePageSize : "") + ". Skipping");
        }
    }

    public static long parseMemoryString(String value) {
        value = value.toUpperCase();
        long multiplier = 1;
        if (value.endsWith("B")) {
            multiplier = 1;
        } else if (value.endsWith("K")) {
            multiplier = 1024;
        } else if (value.endsWith("M")) {
            multiplier = 1024 * 1024;
        } else if (value.endsWith("G")) {
            multiplier = 1024 * 1024 * 1024;
        } else {
            throw new IllegalArgumentException("Expected memory string '" + value + "'to end with either of: B, K, M, G");
        }

        long longValue = Long.parseUnsignedLong(value.substring(0, value.length() - 1));

        return longValue * multiplier;
    }
}
