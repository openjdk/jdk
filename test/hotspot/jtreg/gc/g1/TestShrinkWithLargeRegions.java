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

package gc.g1;

/*
 * @test TestShrinkWithLargeRegions.java
 * @bug 8392840
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Shrinking the heap fails with an assert/guarantee in the uncommit task in presence of large regions.
 * @run main/othervm -XX:+UseG1GC -XX:G1HeapRegionSize=256m -XX:MinHeapSize=512m -XX:InitialHeapSize=4g -XX:MaxHeapSize=4g -XX:MaxHeapFreeRatio=1 -Xlog:gc+heap=trace,gc+ergo+heap=debug,gc+task=debug gc.g1.TestShrinkWithLargeRegions
 */

/*
 * @test TestShrinkWithLargeRegions.java
 * @bug 8392840
 * @requires vm.gc.G1
 * @requires vm.bits != "32"
 * @summary Shrinking the heap fails with a assert/guarantee in the uncommit task in presence of large regions.
 * @run main/othervm -XX:+UseG1GC -XX:G1HeapRegionSize=512m -XX:MinHeapSize=512m -XX:InitialHeapSize=4g -XX:MaxHeapSize=4g -XX:MaxHeapFreeRatio=1 -Xlog:gc+heap=debug,gc+ergo+heap=debug,gc+task=debug gc.g1.TestShrinkWithLargeRegions
 */

import java.lang.Thread;

public class TestShrinkWithLargeRegions {
    public static void main(String[] args) throws Exception {
        System.gc();
        // Let the uncommit thread do its work.
        Thread.sleep(1000);
    }
}
