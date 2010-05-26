/*
 * Copyright (c) 2003, 2004, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4858522 6191542
 * @summary Basic unit test of OperatingSystemMXBean.getCommittedVirtualMemorySize()
 * @author  Steve Bohne
 */

/*
 * This test is just a sanity check and does not check for the correct
 * value.  The correct value should be checked manually:
 * Solaris:
 *   1. In a shell, enter the command: "ps -efly"
 *   2. Find your process, and look in the "SZ" column.  Reported in Kbytes.
 * Linux:
 *   1. In a shell, enter the command: "ps -efly"
 *   2. Find your process, and look in the "SZ" column.  Reported in Kbytes.
 * Windows NT/XP/2000:
 *   1. Hit Ctrl-Alt-Delete, select Task Manager, go to Processes tab.
 *   2. Find your process and look in the "Mem Usage" column.  Reported in
 *      Kbytes.
 * Windows 98/ME:
 *   Not supported.
 */

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.*;

public class GetCommittedVirtualMemorySize {

    private static OperatingSystemMXBean mbean =
        (com.sun.management.OperatingSystemMXBean)
        ManagementFactory.getOperatingSystemMXBean();

    // Careful with these values.
    private static final long MIN_SIZE_FOR_PASS = 1;
    // Max size for pass dynamically determined below
    private static long       max_size_for_pass = Long.MAX_VALUE;

    private static boolean trace = false;

    public static void main(String args[]) throws Exception {
        if (args.length > 0 && args[0].equals("trace")) {
            trace = true;
        }

        // 4934082: On Linux, VM size *can* be larger than total swap
        // size.  Linux might not reserve swap memory immediately when
        // a page is mmaped.  This means that the reported committed
        // memory size can grow beyond the swap limit.
        long max_size = mbean.getTotalSwapSpaceSize() +
                        mbean.getTotalPhysicalMemorySize();

        if (max_size > 0) {
            max_size_for_pass = max_size;
        }
        long size = mbean.getCommittedVirtualMemorySize();
        if (size == -1) {
            System.out.println("getCommittedVirtualMemorySize() is not supported");
            return;
        }

        if (trace) {
            System.out.println("Committed virtual memory size in bytes: " +
                               size);
        }

        if (size < MIN_SIZE_FOR_PASS || size > max_size_for_pass) {
            throw new RuntimeException("Committed virtual memory size " +
                                       "illegal value: " + size + " bytes " +
                                       "(MIN = " + MIN_SIZE_FOR_PASS + "; " +
                                       "MAX = " + max_size_for_pass + ")");
        }

        System.out.println("Test passed.");
    }
}
