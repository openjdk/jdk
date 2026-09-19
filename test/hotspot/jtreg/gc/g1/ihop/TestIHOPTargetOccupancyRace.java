/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test TestIHOPTargetOccupancyRace
 * @bug 8385866
 * @summary Stress IHOP threshold evaluation during humongous allocation while
 *          other mutators expand the heap and update the IHOP target occupancy.
 * @requires vm.gc.G1
 * @run main/othervm -XX:+UseG1GC -XX:G1HeapRegionSize=1m -Xms16m -Xmx1g
 *                   gc.g1.ihop.TestIHOPTargetOccupancyRace
 */
package gc.g1.ihop;

public class TestIHOPTargetOccupancyRace {
    // Each humongous allocation evaluates the IHOP threshold without holding the
    // Heap_lock, while concurrent humongous allocations expand the heap, which
    // updates the target occupancy.
    public static void main(String[] args) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        Thread[] threads = new Thread[8];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                Object[] keep = new Object[16];
                for (int k = 0; System.nanoTime() < deadline; k++) {
                    keep[k & 15] = new byte[(1 + (k & 1)) * 1024 * 1024];
                }
            });
            threads[i].start();
        }
        for (Thread t : threads) {
            t.join();
        }
    }
}
