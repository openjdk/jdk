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
 * Base class for long generators.
 */
public abstract class LongGenerator {

    /**
     * Creates a new {@link LongGenerator}.
     */
    public LongGenerator() {}

    /**
     * Generates a random long from [lo, hi], where the bounds are inclusive.
     *
     * @param lo Lower bound of the sampling range (inclusive).
     * @param hi Higher bound of the sampling range (inclusive).
     * @return Value sampled between [lo, hi].
     */
    public abstract long nextLong(long lo, long hi);

    /**
     * Generates a random long from the whole long range.
     *
     * @return Value sampled from the whole long range.
     */
    public final long nextLong() {
        return nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
    }

    /**
     * Generates a random long in the range [0, hi], where the bounds are inclusive.
     *
     * @param hi Higher bound of the sampling range (inclusive).
     * @return Value sampled from {0, hi}.
     */
    public final long nextLong(long hi) {
        return nextLong(0, hi);
    }

    /**
     * Fill the memory segments with longs in range [lo, hi], where the bounds are inclusive,
     * Fill it with longs from the generators distribution.
     *
     * @param ms Memory segment to be filled.
     * @param hi Higher bound of the sampling range (inclusive).
     * @param lo Lower bound of the sampling range (inclusive).
     */
    public final void fill(MemorySegment ms, long lo, long hi) {
        for (long i = 0; i < ms.byteSize() / 8; i++) {
            ms.set(ValueLayout.JAVA_LONG_UNALIGNED, 8L * i, nextLong(lo, hi));
        }
    }

    /**
     * Fill the array with longs in range [lo, hi], where the bounds are inclusive,
     * Fill it with longs from the generators distribution.
     *
     * @param a Array to be filled.
     * @param hi Higher bound of the sampling range (inclusive).
     * @param lo Lower bound of the sampling range (inclusive).
     */
    public final void fill(long[] a, long lo, long hi) {
        fill(MemorySegment.ofArray(a), lo, hi);
    }
}
