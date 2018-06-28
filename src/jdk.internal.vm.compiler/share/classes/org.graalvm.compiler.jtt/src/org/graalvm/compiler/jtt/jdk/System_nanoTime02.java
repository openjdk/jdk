/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.jtt.jdk;

import org.junit.Test;

import org.graalvm.compiler.jtt.JTTTest;

/*
 */
public class System_nanoTime02 extends JTTTest {

    public static boolean test() {
        long minDelta = Long.MAX_VALUE;

        // the first call to System.nanoTime might take a long time due to call resolution
        for (int c = 0; c < 10; c++) {
            long start = System.nanoTime();
            long delta = 0;
            int i;
            for (i = 0; delta == 0 && i < 50000; i++) {
                delta = System.nanoTime() - start;
                // do nothing.
            }
            if (delta < minDelta) {
                minDelta = delta;
            }
        }

        // better get at least 30 microsecond resolution.
        return minDelta > 1 && minDelta < 30000;
    }

    @Test
    public void run0() throws Throwable {
        runTest("test");
    }

}
