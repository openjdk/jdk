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
 * @summary Failed NMT detail baseline allocation falls back to summary reporting
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -Xmx64m -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail -XX:MallocLimit=nmt:32m:oom -XX:+PrintNMTStatistics DetailBaselineAllocationFailure
 */

import jdk.test.whitebox.WhiteBox;

public class DetailBaselineAllocationFailure {
    public static void main(String[] args) throws Exception {
        WhiteBox wb = WhiteBox.getWhiteBox();
        // Empty malloc sites remain in the table and count towards the initial baseline array size.
        // These entries occupy about 21 MB, while the baseline array needs another 17 MB, exceeding the 32 MB NMT limit.
        int sites = 250_000;
        for (int i = 1; i <= sites; i++) {
            long p = wb.NMTMallocWithPseudoStack(1, i);
            if (p == 0) {
                throw new RuntimeException("Allocation failed at site " + i);
            }
            wb.NMTFree(p);
        }

        NMTTestUtils.startJcmdVMNativeMemory("detail")
                .shouldHaveExitValue(0)
                .shouldContain("Detailed collection failed. Falling back to summary output")
                .shouldContain("Total: reserved=")
                .shouldNotContain("Virtual memory map:");

        // Repeat to exercise cleanup and reuse after a failed collection.
        for (int i = 0; i < 2; i++) {
            NMTTestUtils.startJcmdVMNativeMemory("baseline")
                    .shouldHaveExitValue(0)
                    .shouldContain("Detail baseline collection failed. Summary baseline taken");
            NMTTestUtils.startJcmdVMNativeMemory("summary.diff")
                    .shouldHaveExitValue(0)
                    .shouldContain("Total: reserved=")
                    .shouldNotContain("No baseline for comparison");
            NMTTestUtils.startJcmdVMNativeMemory("detail.diff")
                    .shouldHaveExitValue(0)
                    .shouldContain("No detail baseline for comparison");
        }
    }
}
