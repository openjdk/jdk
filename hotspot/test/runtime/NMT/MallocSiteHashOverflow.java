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

/*
 * @key stress
 * @test
 * @summary Test corner case that overflows malloc site hashtable bucket
 * @key nmt jcmd
 * @library /testlibrary /testlibrary/whitebox
 * @ignore - This test is disabled since it will stress NMT and timeout during normal testing
 * @build MallocSiteHashOverflow
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 * @run main/othervm/timeout=480 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail MallocSiteHashOverflow
 */

import com.oracle.java.testlibrary.*;
import sun.hotspot.WhiteBox;

public class MallocSiteHashOverflow {
    private static long K = 1024;
    public static void main(String args[]) throws Exception {
        String vm_name = System.getProperty("java.vm.name");

        // For 32-bit systems, create 257 malloc sites with the same hash bucket to overflow a hash bucket
        // For 64-bit systems, create 64K + 1 malloc sites with the same hash bucket to overflow a hash bucket
        long entries = 257;
        if (Platform.is64bit()) {
            entries = 64 * K + 1;
        }

        OutputAnalyzer output;
        WhiteBox wb = WhiteBox.getWhiteBox();

        // Grab my own PID
        String pid = Integer.toString(ProcessTools.getProcessId());
        ProcessBuilder pb = new ProcessBuilder();

        wb.NMTOverflowHashBucket(entries);

        // Run 'jcmd <pid> VM.native_memory summary'
        pb.command(new String[] { JDKToolFinder.getJDKTool("jcmd"), pid, "VM.native_memory", "statistics"});
        output = new OutputAnalyzer(pb.start());
        output.shouldContain("Tracking level has been downgraded due to lack of resources");
    }
}
