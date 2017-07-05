/*
 * Copyright (c) 2003, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     4858522
 * @summary Basic unit test of HotspotRuntimeMBean.getSafepointSyncTime()
 * @author  Steve Bohne
 */

/*
 * This test is just a sanity check and does not check for the correct value.
 */

import sun.management.*;

public class GetSafepointSyncTime {

    private static HotspotRuntimeMBean mbean =
        (HotspotRuntimeMBean)ManagementFactoryHelper.getHotspotRuntimeMBean();

    private static final long NUM_THREAD_DUMPS = 300;

    // Careful with these values.
    private static final long MIN_VALUE_FOR_PASS = 1;
    private static final long MAX_VALUE_FOR_PASS = Long.MAX_VALUE;

    public static void main(String args[]) throws Exception {
        long count = mbean.getSafepointCount();
        long value = mbean.getSafepointSyncTime();

        // Thread.getAllStackTraces() should cause safepoints.
        // If this test is failing because it doesn't,
        // MIN_VALUE_FOR_PASS should be reset to 0
        for (int i = 0; i < NUM_THREAD_DUMPS; i++) {
            Thread.getAllStackTraces();
        }

        long count1 = mbean.getSafepointCount();
        long value1 = mbean.getSafepointSyncTime();

        System.out.format("Safepoint count=%d (diff=%d), sync time=%d ms (diff=%d)%n",
                          count1, count1-count, value1, value1-value);

        if (value1 < MIN_VALUE_FOR_PASS || value1 > MAX_VALUE_FOR_PASS) {
            throw new RuntimeException("Safepoint sync time " +
                                       "illegal value: " + value1 + " ms " +
                                       "(MIN = " + MIN_VALUE_FOR_PASS + "; " +
                                       "MAX = " + MAX_VALUE_FOR_PASS + ")");
        }

        for (int i = 0; i < NUM_THREAD_DUMPS; i++) {
            Thread.getAllStackTraces();
        }

        long count2 = mbean.getSafepointCount();
        long value2 = mbean.getSafepointSyncTime();

        System.out.format("Safepoint count=%d (diff=%d), sync time=%d ms (diff=%d)%n",
                          count2, count2-count1, value2, value2-value1);

        if (value2 <= value1) {
            throw new RuntimeException("Safepoint sync time " +
                                       "did not increase " +
                                       "(value1 = " + value1 + "; " +
                                       "value2 = " + value2 + ")");
        }

        System.out.println("Test passed.");
    }
}
