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
import jdk.test.lib.Utils;

/*
 * @test id=generational
 * @key randomness
 * @requires vm.gc.Shenandoah
 * @requires vm.flagless
 * @library /test/lib
 *
 * @run main/othervm/timeout=960 -Xms4g -Xmx4g
 *     -XX:+UseShenandoahGC
 *     -XX:ShenandoahGCMode=generational
 *     -XX:+UnlockExperimentalVMOptions
 *     -XX:ShenandoahGuaranteedYoungGCInterval=0
 *     -XX:ShenandoahGuaranteedOldGCInterval=0
 *     -XX:ShenandoahOldEvacPercent=95
 *     -XX:ShenandoahPromoEvacWaste=3.0
 *     gc.shenandoah.generational.TestTransferOfAffiliated
 */
public class TestTransferOfAffiliated {
    // Size calculations below ignore the overhead of array headers, except to acknowledge that array header causes that
    // only 1 inner array fits per heap region.  Size cacluations also assume the root_array is negligible.
    // Run with 8GB heap size, ShenandoahHeapRegionSize is 4MB

    static Integer[][] root_array;
    // Each inner array spans 1M of memory, which is 2M / 4 bytes (per compressed oops pointer)
    final static int InnerArraySlots = (1 * 1024 * 1024) / 4;
    // Integer objects referenced from inner array
    //  Assume we fill 1048K of memory with Integer objects.
    //  Assume each Integer object consists of 4 bytes for int value, plus 8 bytes for compressed Lilliput header, plus 4 bytes for alignment
    //    (same as 12 bytes for non-compressed object header plus 4 bytes for int value).  
    //  We assign these Integer objects to random elements of the inner array. In the case that two Integer objects are
    //   randomly assigned to the same array element, one of the two will immediately become garbage.
    // The expectation is that the rare collision on array slots is sufficient to allow the "Inner Array" including the array header, plus
    //   all of its Integer elements to pack within a single heap region.
    final static int InnerIntegers = (1 * 1024 * 1024) / 16;
    // Assume heap size is 4 GB.  We want to consume 2 GB of live data.  Each array element consumes 2 MB
    // Raw computation may overflow integer arithmetic: (2 * 1024 * 1024 * 1024) / (2 * 1024 * 1024)
    // Simplified computation: 1024
    final static int OuterArraySlots = 1024;

    // We vary how many entries are spot checked for each filling of an inner array.
    // Spot-checking lots of entries corresponds to slow allocation.  Spot-checking few entries corresponds to faster allocation.
    // By varying the number of spot checks, we create a situation that looks like acceleration of allocation, causing more
    // frequent triggering of young. Times of slower allocation are needed to allow old marking to make progress.
    final static int MaxSpotCheck = 1024;
    final static int MinSpotCheck = 4;
    // Each inner array is intended to consume one region of memory. Process four regions at each spot-check value before
    // moving to the next spot check value.

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
            n *= 1;             // was 1: trying to speed this up a bit
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
    public static long do_inventory(Integer[] array, int index) {
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

        // Fragment young, slowly so we don't do GC cycles here.  We want the fragmented memory to accumulate in young.
        // We don't want this memory to get promoted until last possible moment.
        for (int i = 0; i < 768; i++) {
            int index = i % OuterArraySlots;
            root_array[index] = allocate_empty_inner_array();
            // Accumulate results to slow the allocation, so we have rare GC, long allocation ruway
            accumulator += do_inventory(root_array[index], index);
            int inventory_index = (index + OuterArraySlots - 16) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index], inventory_index);
            inventory_index = (index + OuterArraySlots - 32) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index], inventory_index);
            inventory_index = (index + OuterArraySlots - 64) % OuterArraySlots;
            accumulator += do_inventory(root_array[inventory_index], inventory_index);
        }

        // Fill the arrays slowly. We do this as slowly as possible to maximize allocation runway,
        // separate GC cycles, accumulate promo potential.  We want a big promo potential when we have
        // highly fragmented young memory.  This big promo potential must be paired with a large runway.
        for (int j = 0; j < 768; j++) {
            if (root_array[j] != null) {
                fill_array_Integers_with_probe(root_array[j], 2048);
            }
        }

        assertEquals(accumulator, 3103832320L);
    }

    private static void assertEquals(long a, long b) {
        if (a != b) throw new RuntimeException("assert failed");
    }

}
