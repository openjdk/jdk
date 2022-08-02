/*
 * Copyright (C) 2020, 2022 THL A29 Limited, a Tencent company. All rights reserved.
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

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

// Usage:
//   GetFreeSwapSpaceSize <memoryAlloc> <expectedMemory> <memorySwapAlloc> <expectedSwap>
public class GetFreeSwapSpaceSize {
    public static void main(String[] args) {
        if (args.length != 4) {
            throw new RuntimeException("Unexpected arguments. Expected 4, got " + args.length);
        }
        String memoryAlloc = args[0];
        long expectedMemory = Long.parseLong(args[1]);
        String memorySwapAlloc = args[2];
        long expectedSwap = Long.parseLong(args[3]);
        System.out.println("TestGetFreeSwapSpaceSize (memory=" + memoryAlloc + ", memorySwap=" + memorySwapAlloc + ")");
        if (expectedSwap != 0) {
            throw new RuntimeException("Precondition of test not met: Expected swap size of 0, got: " + expectedSwap);
        }
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long osBeanTotalSwap = osBean.getTotalSwapSpaceSize();
        // Premise of this test is to test on a system where --memory and --memory-swap are set to
        // the same amount via the container engine (i.e. no swap). In that case the OSBean must
        // not report negative values for free swap space. Assert this precondition.
        if (osBeanTotalSwap != expectedSwap) {
            throw new RuntimeException("OperatingSystemMXBean.getTotalSwapSpaceSize() reported " + osBeanTotalSwap + " expected " + expectedSwap);
        }
        System.out.println("TestGetFreeSwapSpaceSize precondition met, osBeanTotalSwap = " + expectedSwap + ". Running test... ");
        for (int i = 0; i < 100; i++) {
            long size = osBean.getFreeSwapSpaceSize();
            if (size < 0) {
                throw new RuntimeException("Test failed! getFreeSwapSpaceSize returns " + size);
            }
        }
        System.out.println("TestGetFreeSwapSpaceSize PASSED." );
    }
}
