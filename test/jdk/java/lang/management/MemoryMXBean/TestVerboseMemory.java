/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8338139
 * @summary Basic unit test of TestVerboseMemory.set/isVerbose() when
 *          related unified logging is enabled.
 *
 * @run main/othervm -Xlog:gc=trace:file=vm.log TestVerboseMemory false
 * @run main/othervm -Xlog:gc=debug:file=vm.log TestVerboseMemory false
 * @run main/othervm -Xlog:gc=info:file=vm.log TestVerboseMemory false
 *
 * @run main/othervm -Xlog:gc=off TestVerboseMemory false
 * @run main/othervm -Xlog:gc=error TestVerboseMemory false
 * @run main/othervm -Xlog:gc=warning TestVerboseMemory false
 *
 * @run main/othervm -Xlog:gc=info TestVerboseMemory true
 * @run main/othervm -Xlog:gc=trace TestVerboseMemory true
 * @run main/othervm -Xlog:gc=debug TestVerboseMemory true
 *
 * @run main/othervm -Xlog:gc*=info TestVerboseMemory true
 * @run main/othervm -Xlog:gc*=debug TestVerboseMemory true
 * @run main/othervm -Xlog:gc*=trace TestVerboseMemory true
 *
 * @run main/othervm -Xlog:gc=info,gc+init=off TestVerboseMemory true
 * @run main/othervm -Xlog:gc=off,gc+init=info TestVerboseMemory false
 * @run main/othervm -Xlog:gc,gc+init TestVerboseMemory true
 *
 * @run main/othervm -Xlog:all=trace:file=vm.log TestVerboseMemory false
 */

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

public class TestVerboseMemory {

    public static void main(String[] args) throws Exception {
        MemoryMXBean mxBean = ManagementFactory.getMemoryMXBean();
        boolean expected = Boolean.parseBoolean(args[0]);
        boolean initial = mxBean.isVerbose();
        if (expected != initial) {
            throw new Error("Initial verbosity setting was unexpectedly " + initial);
        }
        mxBean.setVerbose(false);
        if (mxBean.isVerbose()) {
            throw new Error("Verbosity was still enabled");
        }
        mxBean.setVerbose(true);
        if (!mxBean.isVerbose()) {
            throw new Error("Verbosity was still disabled");
        }
        // Turn off again as a double-check and also to avoid excessive logging
        mxBean.setVerbose(false);
        if (mxBean.isVerbose()) {
            throw new Error("Verbosity was still enabled");
        }
    }
}
