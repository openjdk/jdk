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
 * @summary Test functionality of IntGenerator implementations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver generators.tests.TestIntGenerators
 */

package generators.tests;

import java.util.Random;
import jdk.test.lib.Utils;

import compiler.lib.generators.*;

public class TestIntGenerators {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Test every specific distribution.
        test(new UniformIntGenerator());

        // Test randomly picked generators.
        for (int i = 0; i < 10; i++) {
            test(Generators.ints());
        }
    }

    public static void test(IntGenerator g) {
        // Just generate some full range integers - cannot test anything.
        for (int i = 0; i < 1000; i++) {
             g.nextInt();
        }

        // Test positive values.
        for (int i = 0; i < 1000; i++) {
             int hi = RANDOM.nextInt(Integer.MAX_VALUE);
             int v = g.nextInt(hi);
             checkRange(v, 0, hi);
        }
        for (int i = 0; i < 1000; i++) {
             int v = g.nextInt(Integer.MAX_VALUE);
             checkRange(v, 0, Integer.MAX_VALUE);
        }
        for (int i = 0; i < 1000; i++) {
             int v = g.nextInt(i);
             checkRange(v, 0, i);
        }

        // Any range.
        for (int i = 0; i < 1000; i++) {
             int v = g.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        for (int i = 0; i < 10_000; i++) {
             // hi in [min_int+1, max_int]
             // lo in [min_int, hi-1]
             int hi = RANDOM.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE) + 1;
             int lo = RANDOM.nextInt(Integer.MIN_VALUE, hi);
             int v = g.nextInt(lo, hi);
             checkRange(v, lo, hi);
        }
    }

    public static void checkRange(int v, int lo, int hi) {
        if (v < lo || v > hi) {
            throw new RuntimeException("Out of bounds: " + v + "not in [" + lo + "," + hi + "]");
        }
    }
}
