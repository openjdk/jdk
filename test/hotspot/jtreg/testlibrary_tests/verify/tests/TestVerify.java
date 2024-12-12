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
 * @run driver verify.tests.TestVerify
 */

package verify.tests;

import java.lang.foreign.*;
import java.util.Random;
import jdk.test.lib.Utils;
import compiler.lib.verify.*;

public class TestVerify {
    private static final Random RANDOM = Utils.getRandomInstance();
    // TODO remove random?

    public static void main(String[] args) {
        testArray();
        testMemorySegment();
    }

    public static void testArray() {
        byte[] a = new byte[1000];
        byte[] b = new byte[1001];
        byte[] c = new byte[1000];

        Verify.checkEQ(a, a);
        Verify.checkEQ(b, b);
        Verify.checkEQ(a, c);
        Verify.checkEQ(c, a);

        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(a));
        Verify.checkEQ(MemorySegment.ofArray(b), MemorySegment.ofArray(b));
        Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
        Verify.checkEQ(MemorySegment.ofArray(c), MemorySegment.ofArray(a));

        // Size mismatch
        try {
            Verify.checkEQ(a, b);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Size mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        c[RANDOM.nextInt(c.length)] = 1;

        // Value mismatch
        try {
            Verify.checkEQ(a, c);
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}

        // Value mismatch
        try {
            Verify.checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(c));
            throw new RuntimeException("Should have thrown");
        } catch (VerifyException e) {}
    }

    public static void testMemorySegment() {
    }

//        for (int i = 0; i < 10; i++) {
//             int[] a = new int[1000];
//             g.fill(a, Integer.MIN_VALUE, Integer.MAX_VALUE);
//        }
//        for (int i = 0; i < 100; i++) {
//             int[] a = new int[1000];
//             // hi in [min_int+1, max_int]
//             // lo in [min_int, hi-1]
//             int hi = RANDOM.nextInt(Integer.MIN_VALUE, Integer.MAX_VALUE) + 1;
//             int lo = RANDOM.nextInt(Integer.MIN_VALUE, hi);
//             g.fill(a, lo, hi);
//             checkRange(a, lo, hi);
//             MemorySegment ms = MemorySegment.ofArray(a);
//             g.fill(ms, lo, hi);
//             checkRange(ms, lo, hi);
//        }
//    }
//
//    public static void checkRange(MemorySegment ms, int lo, int hi) {
//        for (long i = 0; i < ms.byteSize() / 4; i++ ) {
//            int v = ms.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i);
//            checkRange(v, lo, hi);
//        }
//    }
}
