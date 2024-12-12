/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.lib.generators;

import java.lang.foreign.*;

/**
 * Define interface of int generators.
 */
public abstract class IntGenerator {
    /**
     * Generate a random int from [lo, hi], where the bounds are inclusive.
     */
    public abstract int nextInt(int lo, int hi);

    /**
     * Generate a random integer from the whole int range.
     */
    public final int nextInt() {
        return nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    /**
     * Generate a random integer in the range [0, hi], where the bounds are inclusive.
     */
    public final int nextInt(int hi) {
        return nextInt(0, hi);
    }

    /**
     * Fill the memory segments with ints in range [lo, hi], where the bounds are inclusive,
     * Fill it with ints from the generators distribution.
     */
    public void fill(MemorySegment ms, int lo, int hi) {
        for (long i = 0; i < ms.byteSize() / 4; i++ ) {
            ms.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * i, nextInt(lo, hi));
        }
    }

    /**
     * Fill the array with ints in range [lo, hi], where the bounds are inclusive,
     * Fill it with ints from the generators distribution.
     */
    public void fill(int[] a, int lo, int hi) {
        fill(MemorySegment.ofArray(a), lo, hi);
    }
}
