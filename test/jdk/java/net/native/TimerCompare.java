/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @test
 * @bug 8370655
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TimerCompare
 * @summary Check EINTR handling InetAddress implementation and NET_ThrowNew
 */

/**
 * This only exercises the new timerMillisExpired() function added to net_util_md.c
 */
public class TimerCompare {

    private static SegmentAllocator autoAllocator =
            (byteSize, byteAlignment) -> Arena.ofAuto().allocate(byteSize, byteAlignment);

    @DataProvider(name = "testArgs")
    private static Object[][] targs() {
        return new Object[][] {
		// timeout is added to start timeval. If end timeval lies before this
 		// time then timer should be expired. If after it, timer should not be.
		//
                //  start timeval       end timeval        
                // s_sec,   s_usec,   e_sec,    e_usec,   timeout(ms),    expired(int)
                  {10,      0,        10,       110,      1000,           0},
                  {1,       0,        1,        3000,     1,              1},
                  {1,       900000,   2,        300,      200,            0},
                  {1,       900000,   2,        200000,   101,            1},
                  {1,       900000,   2,        200000,   301,            0},
                  {1,       900000,   2,        200000,   199,            1}
        };
    }

    static int count = 1;

    @Test(dataProvider = "testArgs")
    public static void test(long s_sec, int s_usec, long e_sec, int e_usec, 
                            int timeout_ms, int expect) {
        System.out.println("Iteration: " + count++);
        MemorySegment start = timeval.allocate(autoAllocator);
        timeval.tv_sec(start, s_sec);
        timeval.tv_usec(start, s_usec);

        MemorySegment end = timeval.allocate(autoAllocator);
        timeval.tv_sec(end, e_sec);
        timeval.tv_usec(end, e_usec);

        int result = timetest_h.timerMillisExpired(start, end, timeout_ms);
        Assert.assertEquals(result, expect);
    }
}
