/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8765432
 * @summary Basic test for SocketFlow API
 * @run testng SocketFlowBasic
 */

import jdk.net.SocketFlow;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static jdk.net.SocketFlow.*;
import static org.testng.Assert.*;

public class SocketFlowBasic {

    @DataProvider
    public Object[][] validPriorities() {
        return new Object[][] { {HIGH_PRIORITY}, {NORMAL_PRIORITY} };
    }

    @Test(dataProvider = "validPriorities")
    public void priority(long validPriority) {
        SocketFlow flow = SocketFlow.create();
        flow.bandwidth(validPriority);
        long bandwidth = flow.bandwidth();
        assertTrue(bandwidth == validPriority, "Expected " + validPriority + ", got" + bandwidth);
    }

    @DataProvider
    public Object[][] invalidPriorities() {
        return new Object[][] { {HIGH_PRIORITY+10}, {NORMAL_PRIORITY-10000} };
    }

    @Test(dataProvider = "invalidPriorities", expectedExceptions = IllegalArgumentException.class)
    public void priority(int invalidPriority) {
        SocketFlow flow = SocketFlow.create();
        flow.priority(invalidPriority);
    }

    @DataProvider
    public Object[][] positiveBandwidth() {
        return new Object[][] { {0}, {100}, {Integer.MAX_VALUE}, {Long.MAX_VALUE} };
    }

    @Test(dataProvider = "positiveBandwidth")
    public void bandwidth(long posBandwidth) {
        SocketFlow flow = SocketFlow.create();
        flow.bandwidth(posBandwidth);
        long bandwidth = flow.bandwidth();
        assertTrue(bandwidth == posBandwidth, "Expected " + posBandwidth + ", got" + bandwidth);
    }


    @DataProvider
    public Object[][] negativeBandwidth() {
        return new Object[][] { {-1}, {-100}, {Integer.MIN_VALUE}, {Long.MIN_VALUE} };
    }

    @Test(dataProvider = "negativeBandwidth", expectedExceptions = IllegalArgumentException.class)
    public void invalidBandwidth(long negBandwidth) {
        SocketFlow flow = SocketFlow.create();
        flow.bandwidth(negBandwidth);
    }

    @Test
    public void status() {
        SocketFlow flow = SocketFlow.create();
        assertTrue(flow.status() == Status.NO_STATUS);
    }
}
