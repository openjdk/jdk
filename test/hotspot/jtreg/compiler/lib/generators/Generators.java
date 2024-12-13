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

import java.util.Random;
import jdk.test.lib.Utils;

/**
 * The generators class provides a set of generator functions for testing.
 * The goal is to cover many special cases, such as NaNs in Floats or values
 * close to overflow in ints. They should produce values from specific
 * "intersting" distributions which might trigger various behaviours in
 * optimizations.
 */
public final class Generators {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Randomly pick an int generator.
     */
    public static IntGenerator ints() {
        switch(RANDOM.nextInt(6)) {
            case 0  -> { return new UniformIntGenerator(); }
            case 1  -> { return new SpecialIntGenerator(0); }
            case 2  -> { return new SpecialIntGenerator(2); }
            case 3  -> { return new SpecialIntGenerator(16); }
            case 4  -> { return new MixedIntGenerator(1, 1, 16); }
            case 5  -> { return new MixedIntGenerator(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * Randomly pick a long generator.
     */
    public static LongGenerator longs() {
        switch(RANDOM.nextInt(6)) {
            case 0  -> { return new UniformLongGenerator(); }
            case 1  -> { return new SpecialLongGenerator(0); }
            case 2  -> { return new SpecialLongGenerator(2); }
            case 3  -> { return new SpecialLongGenerator(16); }
            case 4  -> { return new MixedLongGenerator(1, 1, 16); }
            case 5  -> { return new MixedLongGenerator(1, 2, 2); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

    /**
     * Randomly pick a float generator.
     */
    public static FloatGenerator floats() {
        switch(RANDOM.nextInt(5)) {
            case 0  -> { return new UniformFloatGenerator(-1, 1); }
            // Well balanced, so that multiplication reduction never explodes or collapses to zero:
            case 1  -> { return new UniformFloatGenerator(0.999f, 1.001f); }
            case 2  -> { return new AnyBitsFloatGenerator(); }
            // A tame distribution, mixed in with the occasional special float value:
            case 3  -> { return new SpecialFloatGenerator(new UniformFloatGenerator(0.999f, 1.001f), 10, 1000); }
            // Generating any bits, but special values are more frequent.
            case 4  -> { return new SpecialFloatGenerator(new AnyBitsFloatGenerator(), 100, 200); }
            default -> { throw new RuntimeException("impossible"); }
        }
    }

}
