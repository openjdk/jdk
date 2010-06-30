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
 * @bug     4892507
 * @summary Basic Test for MemoryPool.resetPeakUsage()
 * @author  Mandy Chung
 *
 * @build ResetPeakMemoryUsage MemoryUtil
 * @run main/othervm ResetPeakMemoryUsage
 */

import java.lang.management.*;
import java.util.*;

public class ResetPeakMemoryUsage {
    private static MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
    private static List pools = ManagementFactory.getMemoryPoolMXBeans();
    private static MemoryPoolMXBean mpool = null;

    public static void main(String[] argv) {
        ListIterator iter = pools.listIterator();
        while (iter.hasNext()) {
            MemoryPoolMXBean p = (MemoryPoolMXBean) iter.next();
            if (p.getType() == MemoryType.HEAP &&
                    p.isUsageThresholdSupported()) {
                mpool = p;
                System.out.println("Selected memory pool: ");
                MemoryUtil.printMemoryPool(mpool);
                break;
            }
        }
        if (mpool == null) {
            throw new RuntimeException("No heap pool found with threshold != -1");
        }

        MemoryUsage usage0 = mpool.getUsage();
        MemoryUsage peak0 = mpool.getPeakUsage();
        final long largeArraySize = (usage0.getMax() - usage0.getUsed()) / 10;

        System.out.println("Before big object is allocated: ");
        printMemoryUsage();

        // Allocate a big array - need to allocate from the old gen
        Object[][][] obj = new Object[1][1][(int) largeArraySize];

        System.out.println("After the object is allocated: ");
        printMemoryUsage();

        MemoryUsage usage1 = mpool.getUsage();
        MemoryUsage peak1 = mpool.getPeakUsage();

        if (usage1.getUsed() <= usage0.getUsed()) {
            throw new RuntimeException(
                formatSize("Before allocation: used", usage0.getUsed()) +
                " expected to be > " +
                formatSize("After allocation: used", usage1.getUsed()));
        }

        if (peak1.getUsed() <= peak0.getUsed()) {
            throw new RuntimeException(
                formatSize("Before allocation: peak", peak0.getUsed()) +
                " expected to be > " +
                formatSize("After allocation: peak", peak1.getUsed()));
        }


        // The object is now garbage and do a GC
        // memory usage should drop
        obj = null;
        mbean.gc();

        System.out.println("After GC: ");
        printMemoryUsage();

        MemoryUsage usage2 = mpool.getUsage();
        MemoryUsage peak2 = mpool.getPeakUsage();

        if (usage2.getUsed() >= usage1.getUsed()) {
            throw new RuntimeException(
                formatSize("Before GC: used", usage1.getUsed()) + " " +
                " expected to be > " +
                formatSize("After GC: used", usage2.getUsed()));
        }

        if (peak2.getUsed() != peak1.getUsed()) {
            throw new RuntimeException(
                formatSize("Before GC: peak", peak1.getUsed()) + " " +
                " expected to be equal to " +
                formatSize("After GC: peak", peak2.getUsed()));
        }

        mpool.resetPeakUsage();

        System.out.println("After resetPeakUsage: ");
        printMemoryUsage();

        MemoryUsage usage3 = mpool.getUsage();
        MemoryUsage peak3 = mpool.getPeakUsage();

        if (peak3.getUsed() != usage3.getUsed()) {
            throw new RuntimeException(
                formatSize("After resetting peak: peak", peak3.getUsed()) + " " +
                " expected to be equal to " +
                formatSize("current used", usage3.getUsed()));
        }

        if (peak3.getUsed() >= peak2.getUsed()) {
            throw new RuntimeException(
                formatSize("After resetting peak: peak", peak3.getUsed()) + " " +
                " expected to be < " +
                formatSize("previous peak", peak2.getUsed()));
        }

        System.out.println("Test passed.");
    }

    private static String INDENT = "    ";
    private static void printMemoryUsage() {
        MemoryUsage current = mpool.getUsage();
        MemoryUsage peak = mpool.getPeakUsage();
        System.out.println("Current Usage: ");
        MemoryUtil.printMemoryUsage(current);
        System.out.println("Peak Usage: ");
        MemoryUtil.printMemoryUsage(peak);

    }
    private static String formatSize(String name, long value) {
        StringBuffer buf = new StringBuffer(name + " = " + value);
        if (value > 0) {
            buf.append(" (" + (value >> 10) + "K)");
        }
        return buf.toString();
    }
}
