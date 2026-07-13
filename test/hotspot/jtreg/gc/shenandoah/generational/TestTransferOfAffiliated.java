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

import java.util.Random;

/*
 * @test id=generational
 * @summary Test that we do not attempt to transfer to the old generation regions that are affiliated with young
 * @bug 8382085
 * @key stress
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 *
 * @run main/othervm/timeout=960 -Xms1g -Xmx1g
 *     -XX:+AlwaysPreTouch
 *     -XX:+UseShenandoahGC
 *     -XX:ShenandoahGCMode=generational
 *     -XX:+UnlockExperimentalVMOptions
 *     -XX:ShenandoahMinFreeThreshold=5
 *     -XX:ShenandoahGuaranteedYoungGCInterval=0
 *     -XX:ShenandoahGuaranteedOldGCInterval=0
 *     -XX:ShenandoahOldEvacPercent=95
 *     -XX:ShenandoahPromoEvacWaste=3.0
 *     gc.shenandoah.generational.TestTransferOfAffiliated
 */
public class TestTransferOfAffiliated {
    // Heap size is 1 GB.  HeapRegionSize is 512KB of memory.  Note: 512KB/region * 2048 regions = 1 GB.
    //
    // Size calculations below ignore the overhead of array headers, except to acknowledge that array header causes that
    // only 1 inner array fits per heap region.  Size calculations also assume the root_array is negligible.

    static Integer[][] root_array;

    // Each inner array spans 256K of memory plus a small number of bytes for the array header. Only 1 inner array fits
    // within each HeapRegion, causing a large amount of fragmentation.  The number of elements in an array is 256K divided
    // by 4 bytes per (compressed) oop
    final static int InnerArraySlots = (256 * 1024) / 4;

    // Integer objects are referenced from the inner array. We want each InnerArray to consume a full HeapRegion after
    //   we account for the InnerIntegers referenced from the array. We cannot fill the entire array as that would consume
    //   more than a HeapRegion's worth of memory. We assign Integer objects to random elements of the inner array.  In the
    //   case that two Integer objects are randomly assigned to the same array element, one of the two will immediately become
    //   garbage. The expectation is that the rare collision on array slots is sufficient to allow the "Inner Array", including
    //   its array header and all of its Integer elements to pack within a single heap region.
    //
    //  Assume each Integer object consists of 4 bytes for int value, plus 8 bytes for compressed Lilliput 1 header,
    //   plus 4 bytes for alignment.  Alternatively, if we don't use Lilliput 1, each Integer consumes the same:
    //   12 bytes for non-compressed object header plus 4 bytes for int value.
    //
    //  The number of InnerIntegers for each InnerArray is 256K (half the region size) / 12 bytes / Integer
    final static int InnerIntegers = (256 * 1024) / 12;

    // Assume heap size is 1 GB.  We want to consume approximately 384MB of live data.  Each InnerArray, including its
    // referenced Integer objects, consumes approximately 512KB.  768 array elements * 512KB/array element = 384MB.
    final static int OuterArraySlots = 768;

    final static Random r = new Random(42);

    static int truncateAbsolute(int i) {
        if (i < 0) {
            i = -i;
        }
        if (i < 0) {
            // negative of -1 equals -1
            i = 0;
        }
        return i % InnerArraySlots;
    }

    static int absolute(int arg) {
        if (arg < 0) {
            arg = -arg;
        }
        if (arg < 0) {
            // negative of -1 equals -1
            arg = 0;
        }
        return arg;
    }

    static long cpu_intensive(int n) {
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

    static Integer[] allocate_empty_inner_array() {
        Integer[] result = new Integer[InnerArraySlots];
        return result;
    }

    public static void fill_array_Integers_with_probe(Integer[] array, int spot_check_count) {
        for (int i = 0; i < InnerIntegers; i++) {
            int index = truncateAbsolute(r.nextInt());
            int new_value = absolute(r.nextInt());
            long new_value_cpu_intensive = cpu_intensive(new_value);
            boolean reject_this_value = false;
            // We just do a "spot check", because it consumes too much CPU time if we check all previous values.
            for (int j = 0; j < spot_check_count; j++) {
                int spot_index = truncateAbsolute(r.nextInt());
                if ((array[spot_index] != null) && (new_value_cpu_intensive == cpu_intensive(array[spot_index].intValue()))) {
                    reject_this_value = true;
                    break;
                }
            }
            if (reject_this_value) {
                i--;
            } else {
                // The same index value may be randomly generated multiple times, resulting in overwrite and garbage.
                array[index] = Integer.valueOf(new_value);
            }
        }
    }

    // How much memory is represented by this array?
    public static long do_inventory(Integer[] array) {
        int integer_count = 0;
        if (array != null) {
            for (int i = 0; i < InnerArraySlots; i++) {
                if (array[i] != null) {
                    integer_count++;
                }
            }
        }
        return (array == null)? 0: (InnerArraySlots * 4L) + 16 + integer_count * 16L;
    }

    public static void main(String[] args) {
        root_array = new Integer[OuterArraySlots][];
        long accumulator = 0;

        // Fragment young, slowly so we don't do GC cycles here. We want the fragmented memory to accumulate in young.
        // We don't want this memory to get promoted until last possible moment.
        for (int i = 0; i < 768; i++) {
            int index = i % OuterArraySlots;
            root_array[index] = allocate_empty_inner_array();
            // Accumulate results to slow the allocation, so we have rare GC, long allocation runway.
            accumulator += do_inventory(root_array[index]);
            int inventory_index = (index + OuterArraySlots - 16) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index]);
            inventory_index = (index + OuterArraySlots - 32) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index]);
            inventory_index = (index + OuterArraySlots - 64) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index]);
        }

        // Fill the arrays slowly. We do this as slowly as possible to maximize allocation runway,
        // separate GC cycles, accumulate promo potential. We want a big promo potential when we have
        // highly fragmented young memory. This big promo potential must be paired with a large runway.
        for (int j = 0; j < 768; j++) {
            if (root_array[j] != null) {
                fill_array_Integers_with_probe(root_array[j], 2048);
            }
        }
        // The following assert simply confirms that the program ran correctly and prevents optimizers from removing
        // what might appear to be dead code in the various loops above. The expected regression failure consists of an
        // assert failure observed with fast-debug builds of the JVM before integration of
        // https://github.com/openjdk/jdk/pull/31563.
        assertEquals(accumulator, 775993600L);
    }

    private static void assertEquals(long a, long b) {
        String message = "assert failed(" + Long.toString(a) + " != " + Long.toString(b) + ")";
        if (a != b) throw new RuntimeException(message);
    }


}
