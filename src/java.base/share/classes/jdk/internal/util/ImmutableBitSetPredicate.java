/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.util;

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.util.BitSet;
import java.util.function.IntPredicate;

/**
 * Class for working with immutable BitSets.
 */
@ValueBased
public class ImmutableBitSetPredicate implements IntPredicate {

    @Stable
    private final long[] words;

    private ImmutableBitSetPredicate(BitSet original) {
        // If this class is made public, we need to do
        // a defensive array copy as certain BitSet implementations
        // may return a shared array. The spec says the array should be _new_ though but
        // the consequences might be unspecified for a malicious BitSet.
        this.words = original.toLongArray();
    }

    /**
     * @param bitIndex the bit index to test
     * @return true if the bit is in the range of the BitSet and the bit is set, otherwise false
     */
    @Override
    public boolean test(int bitIndex) {
        if (bitIndex < 0) {
            return false;
        }

        int wordIndex = bitIndex >> 6;
        return (wordIndex < words.length)
                && ((words[wordIndex] & (1L << bitIndex)) != 0);
    }

    /**
     * {@return a new {@link IntPredicate } representing the {@link BitSet#get(int)} method applied
     * on an immutable snapshot of the current state of this BitSet}.
     * <p>
     * If the returned predicate is invoked with a {@code bitIndex} that is negative, the predicate
     * will throw an IndexOutOfBoundsException just as the {@link BitSet#get(int)} method would.
     * <p>
     * Returned predicates are threadsafe and can be used without external synchronisation.
     *
     * @implNote The method is free to return a {@link ValueBased} implementation.
     *
     * @since 22
     */
    public static IntPredicate of(BitSet original) {
        if (original.size() <= 128) {
            long[] array = original.toLongArray();
            return new SmallImmutableBitSetPredicate(
                    array.length > 0 ? array[0] : 0L,
                    array.length > 1 ? array[1] : 0L);
        }
        return new ImmutableBitSetPredicate(original);
    }

    /**
     * Specialization for small sets of 128 bits or less
     * @param first - bits index 0 through 63, inclusive
     * @param second - bits index 64 through 127, inclusive
     */
    public record SmallImmutableBitSetPredicate(long first, long second) implements IntPredicate {

        /**
         * @param bitIndex the bit index to test
         * @return true if the bit is in the range of the BitSet and the bit is set, otherwise false
         */
        @Override
        public boolean test(int bitIndex) {
            if (bitIndex < 0) {
                return false;
            }

            int wordIndex = bitIndex >> 6;
            if (wordIndex > 1) {
                return false;
            }
            long bits = wordIndex == 0 ? first : second;
            return (bits & (1L << bitIndex)) != 0;
        }
    }
}
