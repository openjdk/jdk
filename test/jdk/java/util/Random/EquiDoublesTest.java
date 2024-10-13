/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.test.lib.RandomFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Iterator;
import java.util.TreeSet;
import java.util.random.RandomGenerator;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.params.provider.Arguments.arguments;

/**
 * @test
 * @bug 8302987
 * @key randomness
 *
 * @summary Check consistency of RandomGenerator::equiDoubles
 * @library /test/lib
 * @run junit EquiDoublesTest
 *
 */

public class EquiDoublesTest {

    private static final int SAMPLES = 100_000;

    /*
     * A factor to use in the tight*() tests to make sure that
     * all equidistant doubles are generated.
     */
    private static final long SAFETY_FACTOR = 100L;
    private static final RandomGenerator rnd = RandomFactory.getRandom();

    private static double nextUp(double d, int steps) {
        for (int i = 0; i < steps; ++i) {
            d = Math.nextUp(d);
        }
        return d;
    }

    private static double nextDown(double d, int steps) {
        for (int i = 0; i < steps; ++i) {
            d = Math.nextDown(d);
        }
        return d;
    }

    static Arguments[] equi() {
        return new Arguments[] {
                arguments(0.0, 1e-9),
                arguments(1.0, 1.1),
                arguments(1.0e23, 1.1e23),
                arguments(1.0e300, 1.1e300),
                arguments(-1.2, 1.1),
                arguments(-1.2e-30, 1.1e6),
                arguments(-Double.MIN_VALUE, Double.MIN_VALUE),
                arguments(-Double.MAX_VALUE, Double.MAX_VALUE),
        };
    }

    @ParameterizedTest
    @MethodSource
    void equi(double l, double r) {
        double[] minmax = new double[2];

        resetMinmax(minmax);
        DoubleStream equi = rnd.equiDoubles(l, r, true, false);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(l <= minmax[0]);
        assertTrue(minmax[1] < r);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(l, r, true, true);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(l <= minmax[0]);
        assertTrue(minmax[1] <= r);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(l, r, false, true);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(l < minmax[0]);
        assertTrue(minmax[1] <= r);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(l, r, false, false);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(l < minmax[0]);
        assertTrue(minmax[1] < r);

        /* with negated intervals */
        resetMinmax(minmax);
        equi = rnd.equiDoubles(-r, -l, true, false);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(-r <= minmax[0]);
        assertTrue(minmax[1] < -l);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(-r, -l, true, true);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(-r <= minmax[0]);
        assertTrue(minmax[1] <= -l);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(-r, -l, false, true);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(-r < minmax[0]);
        assertTrue(minmax[1] <= -l);

        resetMinmax(minmax);
        equi = rnd.equiDoubles(-r, -l, false, false);
        equi.limit(SAMPLES).forEach(d -> updateMinmax(minmax, d));
        assertTrue(-r < minmax[0]);
        assertTrue(minmax[1] < -l);
    }

    private void resetMinmax(double[] minmax) {
        minmax[0] = Double.POSITIVE_INFINITY;
        minmax[1] = Double.NEGATIVE_INFINITY;
    }

    private void updateMinmax(double[] minmax, double d) {
        if (d < minmax[0]) {
            minmax[0] = d;
        }
        if (d > minmax[1]) {
            minmax[1] = d;
        }
    }

    static Arguments[] tight() {
        return new Arguments[] {
                arguments(0.0, (short) 100),
                arguments(1.0, (short) 100),
                arguments(1.1, (short) 100),
                arguments(1.0e23, (short) 100),
                arguments(1.0e300, (short) 100),
                arguments(-1.2, (short) 100),
                arguments(-1.2e-30, (short) 100),
                arguments(-Double.MIN_VALUE, (short) 100),

                arguments(-Double.MIN_VALUE, (short) 2),
                arguments(-Double.MAX_VALUE, (short) 2),
        };
    }

    /*
     * All equidistant doubles in a tight range are expected to be generated.
     * The arguments must be chosen as to not overlap a value with irregular
     * spacing around it.
     */
    @ParameterizedTest
    @MethodSource
    void tight(double l, short steps) {
        double r = nextUp(l, steps);

        TreeSet<Double> set = new TreeSet<>();
        DoubleStream equi = rnd.equiDoubles(l, r, true, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, false, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, false, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps - 1, set.size());
        checkEquidistance(set);

        /* with negated intervals */
        set.clear();
        equi = rnd.equiDoubles(-r, -l, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, false, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, false, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps - 1, set.size());
        checkEquidistance(set);
    }

    static Arguments[] tightWithIrregularSpacing() {
        return new Arguments[] {
                arguments(0x1p-1, (short) 15, (short) 23),
                arguments(0x1p0, (short) 17, (short) 5),
                arguments(0x1p1, (short) 7, (short) 8),
                arguments(0x1p-600, (short) 28, (short) 33),
                arguments(0x1p600, (short) 9, (short) 19),
        };
    }

    /*
     * m must be a power of 2 greater than Double.MIN_NORMAL
     */
    @ParameterizedTest
    @MethodSource
    void tightWithIrregularSpacing(double m, short lSteps, short rSteps) {
        double l = nextDown(m, 2 * lSteps);
        double r = nextUp(m, rSteps);
        int steps = lSteps + rSteps;

        TreeSet<Double> set = new TreeSet<>();
        DoubleStream equi = rnd.equiDoubles(l, r, true, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, false, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(l, r, false, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps - 1, set.size());
        checkEquidistance(set);

        /* with negated intervals */
        set.clear();
        equi = rnd.equiDoubles(-r, -l, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, true, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps + 1, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, false, true);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps, set.size());
        checkEquidistance(set);

        set.clear();
        equi = rnd.equiDoubles(-r, -l, false, false);
        equi.limit(SAFETY_FACTOR * steps).forEach(set::add);
        assertEquals(steps - 1, set.size());
        checkEquidistance(set);
    }

    private void checkEquidistance(TreeSet<Double> set) {
        if (set.size() < 3) {
            return;
        }
        Iterator<Double> iter = set.iterator();
        double prev = iter.next();
        double curr = iter.next();
        double delta = curr - prev;
        while (iter.hasNext()) {
            prev = curr;
            curr = iter.next();
            assertEquals(delta, curr - prev);
        }
    }

    static Arguments[] empty() {
        return new Arguments[] {
                arguments(1.0),
                arguments(-1.0),
                arguments(0.0),
                arguments(nextDown(Double.MAX_VALUE, 1)),
                arguments(nextUp(-Double.MAX_VALUE, 1)),
        };
    }

    @ParameterizedTest
    @MethodSource
    void empty(double l) {
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(l, l, true, false)
        );
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(l, nextUp(l, 1), false, false)
        );
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(nextDown(l, 1), l, false, false)
        );
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(l, l, false, true)
        );
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(l, nextDown(l, 1), true, true)
        );
        assertThrows(IllegalArgumentException.class,
                () -> rnd.equiDoubles(nextUp(l, 1), l, true, true)
        );
    }

}
