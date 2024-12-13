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

/*
 * @test
 * @summary Test functionality of FloatGenerator implementations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver generators.tests.TestFloatGenerators
 */

package generators.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;
import compiler.lib.generators.*;

public class TestFloatGenerators {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Test every specific distribution.
        testGeneral(new UniformFloatGenerator(-1, 1));
        testGeneral(new UniformFloatGenerator(0.99f, 1.01f));
        testGeneral(new AnyBitsFloatGenerator());

        // Test randomly picked generators.
        for (int i = 0; i < 10; i++) {
            testGeneral(Generators.floats());
        }
    }

    public static void testGeneral(FloatGenerator g) {
        testGeneralIndividual(g);
        testGeneralFill(g);
    }

    public static void testGeneralIndividual(FloatGenerator g) {
        // Generate floats from unknown distribution - cannot test anything.
        for (int i = 0; i < 1000; i++) {
             g.nextFloat();
        }
    }

    public static void testGeneralFill(FloatGenerator g) {
        // Generate floats from unknown distribution - cannot test anything.
        for (int i = 0; i < 10; i++) {
             float[] a = new float[1000];
             g.fill(a);
        }
        for (int i = 0; i < 100; i++) {
             float[] a = new float[1000];
             MemorySegment ms = MemorySegment.ofArray(a);
             g.fill(ms);
        }
    }

    public static void checkRange(float v, float lo, float hi) {
        if (!(lo <= v && v < hi)) {
            throw new RuntimeException("Out of bounds: " + v + "not in [" + lo + "," + hi + ")");
        }
    }

    public static void checkRange(float[] a, float lo, float hi) {
        checkRange(MemorySegment.ofArray(a), lo, hi);
    }

    public static void checkRange(MemorySegment ms, float lo, float hi) {
        for (long i = 0; i < ms.byteSize() / 4; i++ ) {
            float v = ms.get(ValueLayout.JAVA_FLOAT_UNALIGNED, 4L * i);
            checkRange(v, lo, hi);
        }
    }
}
