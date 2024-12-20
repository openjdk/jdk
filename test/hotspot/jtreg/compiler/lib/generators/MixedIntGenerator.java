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
 * Mixed results between {@link UniformIntGenerator} and {@link SpecialIntGenerator}.
 */
public final class MixedIntGenerator extends IntGenerator {
    private final UniformIntGenerator uniform;
    private final SpecialIntGenerator special;
    private final int weightUniform;
    private final int weightSpecial;

    /**
     * Creates a new {@link MixedIntGenerator}, which samples from {@link UniformIntGenerator} and {@link SpecialIntGenerator},
     * according to specified weights.
     *
     * @param weightUniform Weight for the uniform distribution.
     * @param weightSpecial Weight for the special distribution.
     * @param rangeSpecial Range for the special distribution.
     */
    public MixedIntGenerator(int weightUniform, int weightSpecial, int rangeSpecial) {
        this.weightUniform = weightUniform;
        this.weightSpecial = weightSpecial;
        this.uniform = new UniformIntGenerator();
        this.special = new SpecialIntGenerator(rangeSpecial);
    }

    @Override
    public int nextInt(int lo, int hi) {
        int r = Generators.RANDOM.nextInt(weightUniform + weightSpecial);
        if (r < weightUniform) {
            return uniform.nextInt(lo, hi);
        } else {
            return special.nextInt(lo, hi);
        }
    }
}
