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
 * @summary Test functionality of LongGenerator implementations.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run driver generators.tests.TestLongGenerators
 */

package generators.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;
import compiler.lib.generators.*;

public class TestLongGenerators {
    private static final Random RANDOM = Utils.getRandomInstance();

    public static void main(String[] args) {
        // Test every specific distribution.
        test(new UniformLongGenerator());
        test(new SpecialLongGenerator(0));
        test(new SpecialLongGenerator(2));
        test(new SpecialLongGenerator(16));
        test(new MixedLongGenerator(1, 1, 16));
        test(new MixedLongGenerator(1, 2, 2));

        // Test randomly picked generators.
        for (int i = 0; i < 10; i++) {
            test(Generators.longs());
        }
    }

    public static void test(LongGenerator g) {
        testIndividual(g);
        testFill(g);
    }

    public static void testIndividual(LongGenerator g) {
        // Just generate some full range longs - cannot test anything.
        for (int i = 0; i < 1000; i++) {
             g.nextLong();
        }

        // Test positive values.
        for (int i = 0; i < 1000; i++) {
             long hi = RANDOM.nextLong(Long.MAX_VALUE);
             long v = g.nextLong(hi);
             checkRange(v, 0, hi);
        }
        for (int i = 0; i < 1000; i++) {
             long v = g.nextLong(Long.MAX_VALUE);
             checkRange(v, 0, Long.MAX_VALUE);
        }
        for (int i = 0; i < 1000; i++) {
             long v = g.nextLong(i);
             checkRange(v, 0, i);
        }

        // Any range.
        for (int i = 0; i < 1000; i++) {
             long v = g.nextLong(Long.MIN_VALUE, Long.MAX_VALUE);
        }
        for (int i = 0; i < 10_000; i++) {
             // hi in [min_long+1, max_long]
             // lo in [min_long, hi-1]
             long hi = RANDOM.nextLong(Long.MIN_VALUE, Long.MAX_VALUE) + 1;
             long lo = RANDOM.nextLong(Long.MIN_VALUE, hi);
             long v = g.nextLong(lo, hi);
             checkRange(v, lo, hi);
        }
    }

    public static void testFill(LongGenerator g) {
        for (int i = 0; i < 10; i++) {
             long[] a = new long[1000];
             g.fill(a, Long.MIN_VALUE, Long.MAX_VALUE);
        }
        for (int i = 0; i < 100; i++) {
             long[] a = new long[1000];
             // hi in [min_long+1, max_long]
             // lo in [min_long, hi-1]
             long hi = RANDOM.nextLong(Long.MIN_VALUE, Long.MAX_VALUE) + 1;
             long lo = RANDOM.nextLong(Long.MIN_VALUE, hi);
             g.fill(a, lo, hi);
             checkRange(a, lo, hi);
             MemorySegment ms = MemorySegment.ofArray(a);
             g.fill(ms, lo, hi);
             checkRange(ms, lo, hi);
        }
    }

    public static void checkRange(long v, long lo, long hi) {
        if (v < lo || v > hi) {
            throw new RuntimeException("Out of bounds: " + v + "not in [" + lo + "," + hi + "]");
        }
    }

    public static void checkRange(long[] a, long lo, long hi) {
        checkRange(MemorySegment.ofArray(a), lo, hi);
    }

    public static void checkRange(MemorySegment ms, long lo, long hi) {
        for (long i = 0; i < ms.byteSize() / 8; i++ ) {
            long v = ms.get(ValueLayout.JAVA_LONG_UNALIGNED, 8L * i);
            checkRange(v, lo, hi);
        }
    }
}
