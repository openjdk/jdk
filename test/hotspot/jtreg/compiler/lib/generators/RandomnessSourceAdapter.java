/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.random.RandomGenerator;
import static java.lang.Float.floatToFloat16;
import static java.lang.Float.float16ToFloat;

/**
 * An adapter for using a {@link RandomGenerator} as a {@link RandomnessSource}.
 * See RandomnessSource for more information.
 */
public class RandomnessSourceAdapter implements RandomnessSource {
    private final RandomGenerator rand;

    RandomnessSourceAdapter(RandomGenerator rand) {
        this.rand = rand;
    }

    @Override
    public long nextLong() {
        return rand.nextLong();
    }

    @Override
    public long nextLong(long lo, long hi) {
        return rand.nextLong(lo, hi);
    }

    @Override
    public int nextInt() {
        return rand.nextInt();
    }

    @Override
    public int nextInt(int lo, int hi) {
        return rand.nextInt(lo, hi);
    }

    @Override
    public double nextDouble(double lo, double hi) {
        return rand.nextDouble(lo, hi);
    }

    @Override
    public float nextFloat(float lo, float hi) {
        return rand.nextFloat(lo, hi);
    }

    @Override
    public short nextFloat16(short lo, short hi) {
        return floatToFloat16(rand.nextFloat(float16ToFloat(lo), float16ToFloat(hi)));
    }
}
