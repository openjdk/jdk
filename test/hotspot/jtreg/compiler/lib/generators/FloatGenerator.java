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
 * Define interface of float generators.
 */
public abstract class FloatGenerator {
    private static final Random RANDOM = Utils.getRandomInstance();

    /**
     * Generate a random float, the distribution can be arbitrarily defined by the generator.
     */
    public abstract float nextFloat();

    /**
     * Fill the memory segments with floats using the distribution of nextFloat.
     */
    public final void fill(MemorySegment ms) {
        for (long i = 0; i < ms.byteSize() / 4; i++ ) {
            ms.set(ValueLayout.JAVA_FLOAT_UNALIGNED, 4L * i, nextFloat());
        }
    }

    /**
     * Fill the array with floats using the distribution of nextFloat.
     */
    public final void fill(float[] a) {
        fill(MemorySegment.ofArray(a));
    }
}
