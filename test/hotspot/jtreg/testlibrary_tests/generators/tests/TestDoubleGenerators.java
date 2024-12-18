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
 * @summary Test functionality of DoubleGenerator implementations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver generators.tests.TestDoubleGenerators
 */

package generators.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;
import compiler.lib.generators.*;

public class TestDoubleGenerators {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Test every specific distribution.
        testGeneral(new UniformDoubleGenerator(-1, 1));
        testGeneral(new UniformDoubleGenerator(0.99f, 1.01f));
        testGeneral(new AnyBitsDoubleGenerator());
        testGeneral(new SpecialDoubleGenerator(new UniformDoubleGenerator(0.999f, 1.001f), 10, 1000));
        testGeneral(new SpecialDoubleGenerator(new AnyBitsDoubleGenerator(), 100, 200));

        // Test randomly picked generators.
        for (int i = 0; i < 10; i++) {
            testGeneral(Generators.doubles());
        }

        // Test specific distributions for their qualities.
        testUniform(-1, 1);
        testUniform(0.99f, 1.01f);
        testUniform(Double.MIN_VALUE, Double.MAX_VALUE);

        // Test for a distribution that does not degenerate to inf or NaN.
        testUniformReduction();

        // Check that the special distribution has the expected frequency of special values.
        testSpecial();
    }

    public static void testGeneral(DoubleGenerator g) {
        // Generate doubles from unknown distribution - cannot test anything.
        for (int i = 0; i < 1000; i++) {
             g.nextDouble();
        }
        for (int i = 0; i < 10; i++) {
             double[] a = new double[1000];
             g.fill(a);
        }
        for (int i = 0; i < 100; i++) {
             double[] a = new double[1000];
             MemorySegment ms = MemorySegment.ofArray(a);
             g.fill(ms);
        }
    }

    public static void testUniform(double lo, double hi) {
        DoubleGenerator g = new UniformDoubleGenerator(lo, hi);
        for (int i = 0; i < 1000; i++) {
             double v = g.nextDouble();
             checkRange(v, lo, hi);
        }
        for (int i = 0; i < 10; i++) {
             double[] a = new double[1000];
             g.fill(a);
             checkRange(a, lo, hi);
        }
        for (int i = 0; i < 100; i++) {
             double[] a = new double[1000];
             MemorySegment ms = MemorySegment.ofArray(a);
             g.fill(ms);
             checkRange(a, lo, hi);
             checkRange(ms, lo, hi);
        }
    }

    public static void testUniformReduction() {
        DoubleGenerator g1 = new UniformDoubleGenerator(0.998f, 0.999f);
        DoubleGenerator g2 = new UniformDoubleGenerator(1.001f, 1.002f);
        double v1 = 1;
        double v2 = 1;
        for (int i = 0; i < 10_000; i++) {
            v1 *= g1.nextDouble();
            v2 *= g2.nextDouble();
        }
        checkRange(v1, 1e-10f, 1e-4f);
        checkRange(v2, 1e4f,   1e10f);
    }

    public static void testSpecial() {
        // Generate "safe" values that do not overlap the special values.
        // Generate about 10% special values.
        DoubleGenerator g1 = new UniformDoubleGenerator(100, 200);
        DoubleGenerator g = new SpecialDoubleGenerator(g1, 10, 11);
        int specialCount = 0;
        for (int i = 0; i < 10_000; i++) {
            double v = g.nextDouble();
            if (!(100 <= v && v < 200)) { specialCount++; }
        }
        // Expect special count to be close to 10%
        if (specialCount < 1000 - 10 || 1000 + 10 < specialCount) {
            throw new RuntimeException("Special count too far away from 1000: " + specialCount);
        }
    }

    public static void checkRange(double v, double lo, double hi) {
        if (!(lo <= v && v < hi)) {
            throw new RuntimeException("Out of bounds: " + v + "not in [" + lo + "," + hi + ")");
        }
    }

    public static void checkRange(double[] a, double lo, double hi) {
        checkRange(MemorySegment.ofArray(a), lo, hi);
    }

    public static void checkRange(MemorySegment ms, double lo, double hi) {
        for (long i = 0; i < ms.byteSize() / 8; i++) {
            double v = ms.get(ValueLayout.JAVA_DOUBLE_UNALIGNED, 8L * i);
            checkRange(v, lo, hi);
        }
    }
}
