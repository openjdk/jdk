/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package gc.shenandoah.generational;
/*
 * @test id=generational
 * @summary Test that we do not attempt to transfer to the old
 * generation regions that are affiliated with young
 * @bug 8382085
 * @key stress
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @requires os.maxMemory >= 2g
 * @library /test/lib
 *
 * @run main/othervm/timeout=960 -Xms1g -Xmx1g
 *     -XX:+UnlockExperimentalVMOptions
 *     -XX:ShenandoahRegionSize=512K
 *     -XX:+AlwaysPreTouch
 *     -XX:+UseShenandoahGC
 *     -XX:ShenandoahGCMode=generational
 *     -XX:ShenandoahMinFreeThreshold=5
 *     -XX:ShenandoahGuaranteedYoungGCInterval=0
 *     -XX:ShenandoahGuaranteedOldGCInterval=0
 *     -XX:ShenandoahOldEvacPercent=95
 *     -XX:ShenandoahPromoEvacWaste=3.0
 *     gc.shenandoah.generational.TestTransferOfAffiliated
 */

import java.util.Random;

import jdk.test.lib.Asserts;

public class TestTransferOfAffiliated {
    // Heap size is 1 GB.  HeapRegionSize is 512KB of memory.
    // Note: 512KB/region * 2048 regions = 1 GB.
    //
    // Size calculations below ignore the overhead of array headers,
    // except to acknowledge that array header causes that only 1
    // inner array fits per heap region.  Size calculations also
    // assume the rootArray is negligible.
    private static Integer[][] rootArray;

    // Each inner array spans 256K of memory plus a small number of
    // bytes for the array header. Only 1 inner array fits within each
    // HeapRegion, causing a large amount of fragmentation. The number
    // of elements in an array is 256K divided by 4 bytes per
    // (compressed) oop
    private static final int INNER_ARRAY_SLOTS = (256 * 1024) / 4;

    // Integer objects are referenced from the inner array. We want
    // each InnerArray to consume a full HeapRegion after we account
    // for the InnerIntegers referenced from the array. We cannot fill
    // the entire array as that would consume more than a HeapRegion's
    // worth of memory. We assign Integer objects to random elements
    // of the inner array. In the case that two Integer objects are
    // randomly assigned to the same array element, one of the two
    // will immediately become garbage. The expectation is that the
    // rare collision on array slots is sufficient to allow the
    // "Inner Array", including its array header and all of its
    // Integer elements to pack within a single heap region.
    //
    //  Assume each Integer object consists of 4 bytes for int value,
    // plus 8 bytes for compressed Lilliput 1 header, plus 4 bytes for
    // alignment.  Alternatively, if we don't use Lilliput 1, each
    // Integer consumes the same 16 bytes: 12 bytes for non-compact
    // object header plus 4 bytes for int value.
    //
    //  The number of InnerIntegers for each InnerArray is 256K (half
    // the region size) / 16 bytes / Integer
    private static final int INNER_INTEGERS = (256 * 1024) / 16;

    // Assume heap size is 1 GB. We want to consume approximately
    // 384MB of live data.  Each InnerArray, including its referenced
    // Integer objects, consumes approximately 512KB.  768 array
    // elements * 512KB/array element = 384MB.
    private static final int OUTER_ARRAY_SLOTS = 768;

    private static final Random r = new Random(42);

    private static int absolute(int arg) {
        if (arg < 0) {
            arg = -arg;
        }
        if (arg < 0) {
            // negative of Integer.min_value EQUALS Integer.MIN_VALUE
            arg = 0;
        }
        return arg;
    }

    private static int truncateAbsolute(int i) {
        return absolute(i) % INNER_ARRAY_SLOTS;
    }

    private static long cpuIntensive(int n) {
        long result = 1;
        while (n >= 4) {
            // arithmetic may overflow
            result *= n;
            n /= 4;
        }
        if (n > 0) {
            result *= n;
        }
        return result;
    }

    private static Integer[] allocateEmptyInnerArray() {
        Integer[] result = new Integer[INNER_ARRAY_SLOTS];
        return result;
    }

    private static void fillArrayIntegersWithProbe(Integer[] array,
                                                  int spotCheckCount) {
        for (int i = 0; i < INNER_INTEGERS; i++) {
            int index = truncateAbsolute(r.nextInt());
            int newValue = absolute(r.nextInt());
            long newValueCPUIntensive = cpuIntensive(newValue);
            boolean rejectThisValue = false;
            // We just do a "spot check", because it consumes too much
            // CPU time if we check all previous values.
            for (int j = 0; j < spotCheckCount; j++) {
                int spotIndex = truncateAbsolute(r.nextInt());
                if ((array[spotIndex] != null) &&
                    (newValueCPUIntensive ==
                     cpuIntensive(array[spotIndex].intValue()))) {
                    rejectThisValue = true;
                    break;
                }
            }
            if (rejectThisValue) {
                i--;
            } else {
                // The same index value may be randomly generated
                // multiple times, resulting in overwrite and garbage.
                array[index] = Integer.valueOf(newValue);
            }
        }
    }

    // How much memory is represented by this array?
    private static long doInventory(Integer[] array) {
        int integerCount = 0;
        if (array != null) {
            for (int i = 0; i < INNER_ARRAY_SLOTS; i++) {
                if (array[i] != null) {
                    integerCount++;
                }
            }
        }
        if (array == null) {
            return 0;
        } else {
            return (INNER_ARRAY_SLOTS * 4L) + 16 + integerCount * 16L;
        }
    }

    public static void main(String[] args) {
        rootArray = new Integer[OUTER_ARRAY_SLOTS][];
        long accumulator = 0;

        // Fragment young, slowly so we don't do GC cycles here. We
        // want the fragmented memory to accumulate in young.
        // We don't want this memory to get promoted until last
        // possible moment.
        for (int index = 0; index < OUTER_ARRAY_SLOTS; index++) {
            rootArray[index] = allocateEmptyInnerArray();
            // Accumulate results to slow the allocation, so we have
            // rare GC, long allocation runway.
            accumulator += doInventory(rootArray[index]);
            int inventoryIndex =
                (index + OUTER_ARRAY_SLOTS - 16) % OUTER_ARRAY_SLOTS;
            accumulator += doInventory(rootArray[inventoryIndex]);
            inventoryIndex =
                (index + OUTER_ARRAY_SLOTS - 32) % OUTER_ARRAY_SLOTS;
            accumulator += doInventory(rootArray[inventoryIndex]);
            inventoryIndex =
                (index + OUTER_ARRAY_SLOTS - 64) % OUTER_ARRAY_SLOTS;
            accumulator += doInventory(rootArray[inventoryIndex]);
        }

        // Fill the arrays slowly. We do this as slowly as possible to
        // maximize allocation runway, separate GC cycles, accumulate
        // promo potential. We want a big promo potential when we have
        // highly fragmented young memory. This big promo potential
        // must be paired with a large runway.
        for (int j = 0; j < OUTER_ARRAY_SLOTS; j++) {
            if (rootArray[j] != null) {
                fillArrayIntegersWithProbe(rootArray[j], 2048);
                accumulator += doInventory(rootArray[j]);
            }
        }
        // The following assert simply confirms that the program ran
        // correctly and prevents optimizers from removing what might
        // appear to be dead code in the loops above. The
        // expected regression failure consists of an assert failure
        // observed with fast-debug builds of the JVM before resolution
        // of JDK-8382085. The expected value of accumulator is
        // determined empirically. The value may depend on the initial
        // seed for random number generator and on various constants
        // defined above which determine loop iterations.
        Asserts.assertNotEquals(0L, accumulator,
                                "Proper execution is demonstrated by matching " +
                                "expected accumulator value with no JVM " +
                                "assert failures");
    }
}
