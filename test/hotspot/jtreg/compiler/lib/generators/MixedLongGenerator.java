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
 * Mixed results between {@link UniformLongGenerator} and {@link SpecialLongGenerator}.
 */
public final class MixedLongGenerator extends LongGenerator {
    private final UniformLongGenerator uniform;
    private final SpecialLongGenerator special;
    private final int weightUniform;
    private final int weightSpecial;

    /**
     * Creates a new {@link MixedLongGenerator}, which samples from {@link UniformLongGenerator} and {@link SpecialLongGenerator},
     * according to specified weights.
     *
     * @param weightUniform Weight for the uniform distribution.
     * @param weightSpecial Weight for the special distribution.
     * @param rangeSpecial Range for the special distribution.
     */
    public MixedLongGenerator(int weightUniform, int weightSpecial, int rangeSpecial) {
        this.weightUniform = weightUniform;
        this.weightSpecial = weightSpecial;
        this.uniform = new UniformLongGenerator();
        this.special = new SpecialLongGenerator(rangeSpecial);
    }

    @Override
    public long nextLong(long lo, long hi) {
        int r = Generators.RANDOM.nextInt(weightUniform + weightSpecial);
        if (r < weightUniform) {
            return uniform.nextLong(lo, hi);
        } else {
            return special.nextLong(lo, hi);
        }
    }
}
