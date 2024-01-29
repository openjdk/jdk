/*

 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2016, 2018, Red Hat, Inc. All rights reserved.
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
 *
 */

 /*
 * @test id=g1
 * @summary Make sure G1 can handle humongous allocation fragmentation with region pinning in the mix,
 *          i.e. moving humongous objects around other pinned humongous objects even in a last resort
 *          full gc.
 *          Adapted from gc/TestAllocHumongousFragment.java
 * @key randomness
 * @requires vm.gc.G1
 * @library /test/lib
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xlog:gc+region=trace -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -Xmx1g -Xms1g
 *      -XX:VerifyGCType=full -XX:+VerifyDuringGC -XX:+VerifyAfterGC -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *      gc.g1.pinnedobjs.TestPinnedHumongousFragmentation
 */

package gc.g1.pinnedobjs;

import java.util.*;

import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

import jdk.test.whitebox.WhiteBox;

public class TestPinnedHumongousFragmentation {

    private static final WhiteBox wb = WhiteBox.getWhiteBox();

    static final long TARGET_MB = 30_000; // 30 Gb allocations
    static final long LIVE_MB   = 700;    // 700 Mb alive
    static final int  PINNED_PERCENT = 5; // 5% of objects pinned

    static volatile Object sink;

    class PinInformation {
        int[] object;
        long address;

        PinInformation(int[] object) {
            this.object = object;
            wb.pinObject(object);
            this.address = wb.getObjectAddress(object);
        }

        void release() {
            long newAddress = wb.getObjectAddress(object);
            if (address != newAddress) {
                Asserts.fail("Object at " + address + " moved to " + newAddress);
            }
            wb.unpinObject(object);
            object = null;
        }
    }

    static List<int[]> objects;
    static List<PinInformation> pinnedObjects;

    public static void main(String[] args) throws Exception {
        (new TestPinnedHumongousFragmentation()).run();
    }

    void run() throws Exception {
        final int min = 128 * 1024;
        final int max = 16 * 1024 * 1024;
        final long count = TARGET_MB * 1024 * 1024 / (16 + 4 * (min + (max - min) / 2));

        objects = new ArrayList<int[]>();
        pinnedObjects = new ArrayList<PinInformation>();
        long current = 0;

        Random rng = Utils.getRandomInstance();
        for (long c = 0; c < count; c++) {
            while (current > LIVE_MB * 1024 * 1024) {
                int idx = rng.nextInt(objects.size());
                int[] remove = objects.remove(idx);
                current -= remove.length * 4 + 16;
            }

            // Pin random objects before the allocation that is (likely) going to
            // cause full gcs. Remember them for unpinning.
            for (int i = 0; i < objects.size() * PINNED_PERCENT / 100; i++) {
                int[] target = objects.get(rng.nextInt(objects.size()));
                pinnedObjects.add(new PinInformation(target));
            }

            int[] newObj = new int[min + rng.nextInt(max - min)];
            current += newObj.length * 4 + 16;
            objects.add(newObj);
            sink = new Object();

            // Unpin and clear remembered objects afterwards.
            for (int i = 0; i < pinnedObjects.size(); i++) {
                pinnedObjects.get(i).release();
            }
            pinnedObjects.clear();

            System.out.println("Allocated: " + (current / 1024 / 1024) + " Mb");
        }
    }

}
