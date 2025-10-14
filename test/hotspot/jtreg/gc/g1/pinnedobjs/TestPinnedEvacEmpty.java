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

/* @test
 * @bug 8351921 8352508
 * @summary Test that pinned regions with no Java references into them
 *          do not make the garbage collector reclaim that region.
 *          This test simulates this behavior using Whitebox methods
 *          to pin a Java object in a region with no other pinnable objects and
*           lose the reference to it before the garbage collection.
 * @requires vm.gc.G1
 * @requires vm.debug
 * @library /test/lib /
 * @modules java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC
 *      -Xbootclasspath/a:. -Xlog:gc=debug,gc+ergo+cset=trace,gc+phases=debug -XX:G1HeapRegionSize=1m -Xms30m  gc.g1.pinnedobjs.TestPinnedEvacEmpty regular
 *
 * @run main/othervm -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:+UseG1GC
 *      -Xbootclasspath/a:. -Xlog:gc=debug,gc+ergo+cset=trace,gc+phases=debug -XX:G1HeapRegionSize=1m -Xms30m  gc.g1.pinnedobjs.TestPinnedEvacEmpty humongous
 */

package gc.g1.pinnedobjs;

import gc.testlibrary.Helpers;

import jdk.test.lib.Asserts;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.whitebox.WhiteBox;

public class TestPinnedEvacEmpty {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    private static final int objSize = (int)wb.getObjectSize(new Object());

    private static Object allocHumongous() {
        final int M = 1024 * 1024;
        // The target object to pin. Since it is humongous, it will always be in its
        // own regions.
        return new int[M];
    }

    private static Object allocRegular() {
        // How many j.l.Object should we allocate when creating garbage.
        final int numAllocations = 1024 * 1024 * 3 / objSize;

        // Allocate garbage so that the target object will be in a new region.
        for (int i = 0; i < numAllocations; i++) {
          Object z = new Object();
        }
        int[] o = new int[100];  // The target object to pin.
        // Further allocate garbage so that any additional allocations of potentially
        // pinned objects can not be allocated in the same region as the target object.
        for (int i = 0; i < numAllocations; i++) {
          Object z = new Object();
        }

        Asserts.assertTrue(!wb.isObjectInOldGen(o), "should not be in old gen already");
        return o;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Testing " + args[0] + " objects...");

        // Remove garbage from VM initialization.
        wb.fullGC();

        // Allocate target object according to arguments.
        Object o = args[0].equals("regular") ? allocRegular() : allocHumongous();

        // Pin the object.
        wb.pinObject(o);

        // And forget the (Java) reference to the int array. After this, the garbage
        // collection should find a completely empty pinned region. The collector
        // must not collect/free it.
        o = null;

        // Full collection should not crash the VM in case of "empty" pinned regions.
        wb.fullGC();

        // Do a young garbage collection to zap the data in the pinned region. This test
        // achieves that by executing a concurrent cycle that both performs both a young
        // garbage collection as well as checks that errorneous reclamation does not occur
        // in the Remark pause.
        wb.g1RunConcurrentGC();
        System.out.println("Done");
    }
}
