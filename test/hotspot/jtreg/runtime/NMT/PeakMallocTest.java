/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Red Hat Inc.
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
 * @bug 8332122
 * @summary Test to verify correctness of peak malloc tracking
 * @key randomness
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=summary -Xms8m -Xmx8m -Xint PeakMallocTest
 *
 */

import jdk.test.lib.process.OutputAnalyzer;

import jdk.test.whitebox.WhiteBox;

public class PeakMallocTest {

    public static WhiteBox wb = WhiteBox.getWhiteBox();

    public static void main(String[] args) throws Exception {

        // Measure early malloc total and peak
        OutputAnalyzer output = NMTTestUtils.startJcmdVMNativeMemory();
        long earlyTotal = getMallocTotal(output);
        long earlyPeak = getMallocPeak(output);
        System.out.println("Early malloc total: " + earlyTotal);
        System.out.println("Early malloc peak: " + earlyPeak);


        // Allocate a large amount of memory and then free
        long allocSize = Math.max(8 * earlyPeak, 1000000000); // MAX(earlyPeak * 8, 1GB)
        long addr = wb.NMTMalloc(allocSize);
        System.out.println("Allocation size: " + allocSize);
        wb.NMTFree(addr);

        // Measure again
        output = NMTTestUtils.startJcmdVMNativeMemory();
        long currTotal = getMallocTotal(output);
        long currPeak = getMallocPeak(output);
        System.out.println("Current malloc total: " + currTotal);
        System.out.println("Current malloc peak: " + currPeak);

        // Verify total global malloc is similar
        // Fudge factor of 10% to account for noise
        if ((currTotal < earlyTotal * 0.9) || (currTotal > earlyTotal * 1.1)) {
            throw new Exception("Global malloc measurement is incorrect");
        }

        // Verify global malloc peak reflects large allocation
        long peakDiff = (currPeak - earlyPeak) * 1000; // Note, KB to Bytes conversion
        System.out.println("Peak diff: " + peakDiff);
        if ((peakDiff < allocSize * 0.9) || (peakDiff > allocSize * 1.1)) {
            throw new Exception("Global malloc peak measurement is incorrect");
        }
    }

    private static long getMallocPeak(OutputAnalyzer output) {
        // First match should correspond to global malloc peak
        String global = output.firstMatch("peak=\\d*");
        return Long.parseLong(global.substring(global.indexOf("=") + 1));
    }

    private static long getMallocTotal(OutputAnalyzer output) {
        // First match should correspond to global malloc total
        String global = output.firstMatch("malloc: \\d*");
        return Long.parseLong(global.substring(global.indexOf(" ") + 1));
    }
}
