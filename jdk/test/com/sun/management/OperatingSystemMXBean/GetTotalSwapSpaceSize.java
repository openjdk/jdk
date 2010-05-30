/*
 * Copyright (c) 2003, 2005, Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * @bug     4858522
 * @summary Basic unit test of OperatingSystemMXBean.getTotalSwapSpaceSize()
 * @author  Steve Bohne
 */

/*
 * This test tests the actual swap size on linux and solaris.
 * The correct value should be checked manually:
 * Solaris:
 *   1. In a shell, enter the command: "swap -l"
 *   2. The value (reported in blocks) is in the "blocks" column.
 * Linux:
 *   1. In a shell, enter the command: "cat /proc/meminfo"
 *   2. The value (reported in bytes) is in "Swap" entry, "total" column.
 * Windows NT/XP/2000:
 *   1. Run Start->Accessories->System Tools->System Information.
 *   2. The value (reported in Kbytes) is in the "Page File Space" entry
 * Windows 98/ME:
 *   Unknown.
 *
 * Usage: GetTotalSwapSpaceSize <expected swap size | "sanity-only"> [trace]
 */

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.*;

public class GetTotalSwapSpaceSize {

    private static OperatingSystemMXBean mbean =
        (com.sun.management.OperatingSystemMXBean)
        ManagementFactory.getOperatingSystemMXBean();

    // Careful with these values.
    // Min size for pass dynamically determined below.
    // zero if no swap space is configured.
    private static long       min_size_for_pass = 0;
    private static final long MAX_SIZE_FOR_PASS = Long.MAX_VALUE;

    private static boolean trace = false;

    public static void main(String args[]) throws Exception {
        if (args.length > 1 && args[1].equals("trace")) {
            trace = true;
        }

        long expected_swap_size = 0;

        if (args.length < 1 || args.length > 2) {
           throw new IllegalArgumentException("Unexpected number of args " + args.length);
        }


        long min_size = mbean.getFreeSwapSpaceSize();
        if (min_size > 0) {
            min_size_for_pass = min_size;
        }

        long size = mbean.getTotalSwapSpaceSize();

        if (trace) {
            System.out.println("Total swap space size in bytes: " + size);
        }

        if (!args[0].matches("sanity-only")) {
            expected_swap_size = Long.parseLong(args[0]);
            if (size != expected_swap_size) {
                throw new RuntimeException("Expected total swap size      : " +
                                           expected_swap_size +
                                           " but getTotalSwapSpaceSize returned: " +
                                           size);
            }
        }

        if (size < min_size_for_pass || size > MAX_SIZE_FOR_PASS) {
            throw new RuntimeException("Total swap space size " +
                                       "illegal value: " + size + " bytes " +
                                       "(MIN = " + min_size_for_pass + "; " +
                                       "MAX = " + MAX_SIZE_FOR_PASS + ")");
        }

        System.out.println("Test passed.");
    }
}
