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

package compiler.c2;

/*
 * @test
 * @bug 8370405
 * @summary Test case where we had escape analysis tell us that we can possibly eliminate
 *          the array allocation, then MergeStores introduces a mismatched store, which
 *          the actual elimination does not verify for. That led to wrong results.
 * @run main/othervm -XX:CompileCommand=compileonly,compiler.c2.TestMergeStoresAndAllocationElimination::test
 *                   -XX:CompileCommand=exclude,compiler.c2.TestMergeStoresAndAllocationElimination::dontinline
 *                   -XX:-TieredCompilation -Xbatch
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-CICompileOSR
 *                   compiler.c2.TestMergeStoresAndAllocationElimination
 * @run main compiler.c2.TestMergeStoresAndAllocationElimination
 */

public class TestMergeStoresAndAllocationElimination {
    static void dontinline() {}

    static int test(boolean flag) {
        int[] arr = new int[4];
        // The values below will be caputured as "raw stores" in the Initialize
        // of the array allocation above.
        // These stores are for cosmetics only, we set the "1" bits so that it is
        // simple to track where values are coming from.
        arr[0] = 0x0001_0000;
        arr[1] = 0x0010_0000;
        arr[2] = 0x0000_0100;
        arr[3] = 0x0100_0000;
        // So far, the result should be:
        // 0x421_0300

        // The call below prevents further assignments from being captured into
        // the Initialize above.
        dontinline();
        // The follwoing stores are eventually optimized by MergeStores, and create
        // a mismatched StoreL.
        arr[0] = 0x0000_0001;
        arr[1] = 0x0000_0010;
        // Now, the result should be:
        // 0x400_0321

        // We create an uncommon trap because of an "unstable if".
        // If Escape Analysis were to work, it would try to capture the values
        // from the StoreL above. But because it is mismatched, it should fail.
        // What happened before that verification: we would take the ConL, and
        // insert it in a list of ConI. That meant that we eventually applied
        // that value wrong if the deopt was taken (flag = true).
        //
        // What happened when the deopt got the wrong values: It got these values:
        // [0]=68719476737 = 0x10_0000_0001 -> long value, not correct
        // [1]=1048576     =      0x10_0000 -> this entry is not updated!
        // [2]=256         =          0x100
        // [3]=16777216    =     0x100_0000
        //
        // This is serialized as a long and 3 ints, and that looks like 5 ints.
        // This creates an array of 5 elements (and not 4):
        // [0]             =            0x1
        // [1]             =           0x10
        // [2]             =      0x10_0000 -> this entry is "inserted"
        // [3]             =          0x100
        // [4]             =     0x100_0000
        //
        // This creates the wrong state:
        // 0x30_0421
        // And we can actually read that the arr.length is 5, below.
        if (flag) { System.out.println("unstable if: " + arr.length); }

        // Delay the allocation elimination until after loop opts, so that it
        // happens after MergeStores. Without this, we would immediately
        // eliminate the allocation during Escape Analysis, and then MergeStores
        // would not find the stores that would be removed with the allocation.
        for (int i = 0; i < 10_000; i++) {
            arr[3] = 0x0000_1000;
        }
        // Coming from the correct value, we should have transition of state:
        // 0x400_0321 -> 0x4321
        // But coming from the bad (rematerialized) state, we transition:
        // 0x30_0421 -> 0x30_4021

        // Tag each entry with an index number
        // We expect: 0x4321
        return 1 * arr[0] + 2 * arr[1] + 3 * arr[2] + 4 * arr[3];
    }

    public static void main(String[] args) {
        // Capture interpreter result.
        int gold = test(false);
        // Repeat until we get compilation.
        for (int i = 0; i < 10_000; i++) {
            test(false);
        }
        // Capture compiled results.
        int res0 = test(false);
        int res1 = test(true);
        if (res0 != gold || res1 != gold) {
            throw new RuntimeException("Unexpected result: " + Integer.toHexString(res0) + " and " + Integer.toHexString(res1) + ", should be: " + Integer.toHexString(gold));
        }
    }
}
