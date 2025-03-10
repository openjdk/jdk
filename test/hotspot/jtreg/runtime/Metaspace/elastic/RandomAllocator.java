/*
 * Copyright (c) 2021 SAP SE. All rights reserved.
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Random;

import jdk.test.whitebox.WhiteBox;

/**
 * RandomAllocator sits atop an arena and allocates from it.
 *
 * It will, according to an allocation profile, allocate random blocks in a certain size range and, from time to time,
 * deallocate old blocks.
 *
 * At some point it will reach a limit: either the commit/reserve limit of the underlying MetaspaceTestContext,
 * or the allocation ceiling imposed by the test. From that point on allocations will start failing. We can (and do)
 * deallocate a bit more, but since that will only exercise the Arena's internal free block list and nothing much else,
 * this is unexciting in terms of stressing Metaspace. So, the caller may decide to kill the arena and create a new one.
 *
 */
public class RandomAllocator {

    final MetaspaceTestArena arena;
    final AllocationProfile profile;

    ArrayList<Allocation> to_dealloc = new ArrayList<>();

    long ticks = 0;

    // Allocate (breathe in) until arena is full, then - to test the arena deallocator - deallocate some allocations
    // and breathe in again until full.
    boolean breatheIn = true;
    int breatheOutTicks = 0;

    Random localRandom;

    // Roll dice and return true if probability was hit
    private boolean rollDice(double probability) {
        return ((double)localRandom.nextInt(100) > (100.0 * (1.0 - probability))) ? true : false;
    }

    // Allocate a random amount from the arena. If dice hits right, add this to the deallocation list.
    void allocateRandomly() {
        long words = profile.randomAllocationSize();
        Allocation a = arena.allocate(words * Settings.WORD_SIZE);
        if (a != null) {
            if (to_dealloc.size() < 10000) {
                to_dealloc.add(a);
            }
        } else {
            // On allocation error, breathe out a bit
            breatheIn = false;
            breatheOutTicks = 0;
        }
    }

    // Randomly choose one of the allocated in the deallocation list and deallocate it
    void deallocateRandomly() {
        if (to_dealloc.size() == 0) {
            return;
        }
        int n = localRandom.nextInt(to_dealloc.size());
        Allocation a = to_dealloc.remove(n);
        arena.deallocate(a);
    }

    public void tick() {
        if (breatheIn) {
            // allocate until we hit the ceiling
            allocateRandomly();
            if (rollDice(profile.randomDeallocProbability)) {
               deallocateRandomly();
            }
        } else {
            if (++breatheOutTicks < 100) {
                deallocateRandomly();
            } else {
                breatheIn = true;
            }
        }
        ticks ++;
    }

    public RandomAllocator(MetaspaceTestArena arena) {
        this.arena = arena;
        this.profile = AllocationProfile.randomProfile();
        // reproducable randoms (we assume each allocator is only used from within one thread, and gets created from the main thread).
        this.localRandom = new Random(RandomHelper.random().nextInt());
    }

    long numAllocationFailures() {
        return arena.numAllocationFailures;
    }

    @Override
    public String toString() {
        return  arena.toString() + ", ticks=" + ticks;
    }

}
