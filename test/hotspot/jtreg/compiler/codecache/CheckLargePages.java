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
 * @summary Test checks that if using large pages and code cache gets above the limit it tries to revert to smaller pages instead of failing
 * @requires vm.gc != "Z"
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseLargePages -XX:LargePageSizeInBytes=1g compiler.codecache.CheckLargePages
 */

package compiler.codecache;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class CheckLargePages {
    private final static WhiteBox WHITE_BOX = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {
        final boolean largePages = WHITE_BOX.getBooleanVMFlag("UseLargePages");
        final long largePageSize = WHITE_BOX.getVMLargePageSize();
        if (largePages && (largePageSize == 1024 * 1024 * 1024)) {
            ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(
                    "-XX:+UseLargePages",
                    "-XX:+SegmentedCodeCache",
                    "-XX:InitialCodeCacheSize=2g",
                    "-XX:ReservedCodeCacheSize=2g",
                    "-XX:LargePageSizeInBytes=1g",
                    "-Xlog:pagesize*=debug",
                    "-version");
            OutputAnalyzer out = new OutputAnalyzer(pb.start());
            out.shouldContain("Failed to reserve large page memory for code cache");
            out.shouldHaveExitValue(0);
        } else {
            System.out.println("1GB large pages not supported: UseLargePages=" + largePages +
                    (largePages ? ", largePageSize=" + largePageSize : "") + ". Skipping");
        }
    }
}
