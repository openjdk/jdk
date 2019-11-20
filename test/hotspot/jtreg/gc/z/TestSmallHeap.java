/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package gc.z;

/*
 * @test TestSmallHeap
 * @requires vm.gc.Z & !vm.graal.enabled
 * @summary Test ZGC with small heaps
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx8M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx16M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx32M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx64M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx128M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx256M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx512M gc.z.TestSmallHeap
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -Xlog:gc,gc+init,gc+heap -Xmx1024M gc.z.TestSmallHeap
 */

import java.lang.ref.Reference;

public class TestSmallHeap {
    public static void main(String[] args) throws Exception {
        final long maxCapacity = Runtime.getRuntime().maxMemory();
        System.out.println("Max Capacity " + maxCapacity + " bytes");

        // Allocate byte arrays of increasing length, so that
        // all allocaion paths (small/medium/large) are tested.
        for (int length = 16; length <= maxCapacity / 16; length *= 2) {
            System.out.println("Allocating " + length + " bytes");
            Reference.reachabilityFence(new byte[length]);
        }

        System.out.println("Success");
    }
}
