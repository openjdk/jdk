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

import java.util.Arrays;
import java.util.HashSet;

/**
 * Provides a distribution over values close to the powers of 2.
 */
public final class SpecialIntGenerator extends IntGenerator {

    /*
     * Pre-generated values we can chose from.
     */
    private final int[] values;

    /*
     * Fall-back generator if values does not contain any value in the
     * expected range.
     */
    private final UniformIntGenerator uniform = new UniformIntGenerator();

    /**
     * Creates a new {@link SpecialIntGenerator}. Generates only powers of 2, and
     * values that are not more than {@code range} away from powers of 2.
     *
     * @param range How far away from the powers of 2 the values should be generated.
     */
    public SpecialIntGenerator(int range) {
        HashSet<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < 32; i++) {
            int pow2 = 1 << i;
            for (int j = -range; j <= range; j++) {
                set.add(+pow2 + j);
                set.add(-pow2 + j);
            }
        }
        this.values = set.stream().mapToInt(Number::intValue).toArray();
        Arrays.sort(this.values);
    }

    @Override
    public int nextInt(int lo, int hi) {
        // Find indices in values.
        int loIndex = Arrays.binarySearch(values, lo);
        int hiIndex = Arrays.binarySearch(values, hi);
        if (loIndex < 0) {
            // Not found, but we know that any values higher than lo
            // must be at loIndex or higher.
            loIndex = -(loIndex + 1);
        }
        if (hiIndex < 0) {
            // Not found, but we know that any values lower than hi
            // must be at less than hiIndex.
            hiIndex = -(hiIndex + 1);
        } else {
            hiIndex++;
        }
        if (loIndex < hiIndex) {
            int r = Generators.RANDOM.nextInt(hiIndex - loIndex);
            return values[loIndex + r];
        }

        // No element in values is in the required range.
        // Fall-back to uniform.
        return uniform.nextInt(lo, hi);
    }
}
