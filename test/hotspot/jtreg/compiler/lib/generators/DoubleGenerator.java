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
import java.util.Random;
import jdk.test.lib.Utils;

/**
 * Base class of double generators.
 */
public abstract class DoubleGenerator {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Creates a new {@link DoubleGenerator}.
     */
    public DoubleGenerator() {}

    /**
     * Generate a random double, the distribution can be arbitrarily defined by the generator.
     *
     * @return Random double value.
     */
    public abstract double nextDouble();

    /**
     * Fill the memory segments with doubles using the distribution of nextDouble.
     *
     * @param ms Memory segment to be filled with random values.
     */
    public final void fill(MemorySegment ms) {
        for (long i = 0; i < ms.byteSize() / 8; i++) {
            ms.set(ValueLayout.JAVA_DOUBLE_UNALIGNED, 8L * i, nextDouble());
        }
    }

    /**
     * Fill the array with doubles using the distribution of nextDouble.
     *
     * @param a Array to be filled with random values.
     */
    public final void fill(double[] a) {
        fill(MemorySegment.ofArray(a));
    }
}
