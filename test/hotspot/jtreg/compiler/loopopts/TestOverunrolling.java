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
 * @bug 8159016
 * @summary Tests correct dominator information after over-unrolling a loop.
 * @requires vm.gc == "Parallel" | vm.gc == "null"
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -Xcomp -XX:-TieredCompilation
 *                   -XX:-UseG1GC -XX:+UseParallelGC
 *                   compiler.loopopts.TestOverunrolling
 */

package compiler.loopopts;

public class TestOverunrolling {

    public static Object test(int arg) {
        Object arr[] = new Object[3];
        int lim = (arg & 3);
        // The pre loop is executed for one iteration, initializing p[0].
        // The main loop is unrolled twice, initializing p[1], p[2], p[3] and p[4].
        // The p[3] and p[4] stores are always out of bounds and removed. However,
        // C2 is unable to remove the "over-unrolled", dead main loop. As a result,
        // there is a control path from the main loop to the post loop without a
        // memory path (because the last store was replaced by TOP). We crash
        // because we use a memory edge from a non-dominating region.
        for (int i = 0; i < lim; ++i) {
            arr[i] = new Object();
        }
        // Avoid EA
        return arr;
    }

    public static void main(String args[]) {
        for (int i = 0; i < 42; ++i) {
            test(i);
        }
    }
}

