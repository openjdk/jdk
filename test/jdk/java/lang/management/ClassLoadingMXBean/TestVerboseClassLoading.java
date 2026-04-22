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
 * @summary Basic unit test of ClassLoadingMXBean.set/isVerbose() when
 *          related unified logging is enabled.
 *
 * @run main/othervm -Xlog:class+load=trace:file=vm.log TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=debug:file=vm.log TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=info:file=vm.log TestVerboseClassLoading false
 *
 * @run main/othervm -Xlog:class+load=trace TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=debug TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=info TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=warning TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=error TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load=off TestVerboseClassLoading false
 *
 * @run main/othervm -Xlog:class+load*=trace TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=debug TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=info TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=warning TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load*=error TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load*=off TestVerboseClassLoading false
 *
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=trace TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=debug TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=info TestVerboseClassLoading true
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=warning TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=error TestVerboseClassLoading false
 * @run main/othervm -Xlog:class+load*=info,class+load+cause=off TestVerboseClassLoading false
 *
 * @run main/othervm -Xlog:all=trace:file=vm.log TestVerboseClassLoading false
 */

import java.lang.management.ManagementFactory;
import java.lang.management.ClassLoadingMXBean;

public class TestVerboseClassLoading {

    public static void main(String[] args) throws Exception {
        ClassLoadingMXBean mxBean = ManagementFactory.getClassLoadingMXBean();
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
