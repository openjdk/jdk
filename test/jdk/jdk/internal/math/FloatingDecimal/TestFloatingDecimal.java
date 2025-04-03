/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigDecimal;
import java.util.Random;
import jdk.internal.math.FloatingDecimal;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @test
 * @bug 7032154 8343453
 * @summary FloatingDecimal parsing methods (use -Dseed=X to set PRANDOM seed)
 * @modules java.base/jdk.internal.math
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit TestFloatingDecimal
 * @author Brian Burkhalter
 * @key randomness
 */
public class TestFloatingDecimal {
    private static final int NUM_RANDOM_TESTS = 100_000;

    private static final Random RANDOM = RandomFactory.getRandom();

    /*
     * The tests rely on the different conversion implementations
     * in FloatDecimal and BigDecimal.
     */

    @Test
    public void testParseDouble() {
        for (int i = 0; i < NUM_RANDOM_TESTS; i++) {
            double[] d = {
                    RANDOM.nextLong(),
                    RANDOM.nextGaussian(),
                    RANDOM.nextDouble() * Double.MAX_VALUE,
            };
            for (double v : d) {
                String dec = Double.toString(v);
                assertEquals(new BigDecimal(dec).doubleValue(), FloatingDecimal.parseDouble(dec));

                BigDecimal bd = new BigDecimal(v);
                String full = bd.toString();
                assertEquals(bd.doubleValue(), FloatingDecimal.parseDouble(full));

                String hex = Double.toHexString(v);
                assertEquals(FloatingDecimal.parseDouble(dec), FloatingDecimal.parseDouble(hex));
            }
        }
    }

    @Test
    public void testParseFloat() {
        for (int i = 0; i < NUM_RANDOM_TESTS; i++) {
            float[] f = {
                    RANDOM.nextLong(),
                    (float) RANDOM.nextGaussian(),
                    RANDOM.nextFloat() * Float.MAX_VALUE
            };
            for (float v : f) {
                String dec = Float.toString(v);
                assertEquals(new BigDecimal(dec).floatValue(), FloatingDecimal.parseFloat(dec));

                BigDecimal bd = new BigDecimal(v);
                String full = bd.toString();
                assertEquals(bd.floatValue(), FloatingDecimal.parseFloat(full));

                String hex = Float.toHexString(v);
                assertEquals(FloatingDecimal.parseFloat(dec), FloatingDecimal.parseFloat(hex));
            }
        }
    }

}
