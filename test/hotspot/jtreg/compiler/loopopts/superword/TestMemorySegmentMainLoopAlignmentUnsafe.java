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

package compiler.loopopts.superword;

import jdk.internal.misc.Unsafe;
import jdk.internal.util.Preconditions;

/*
 * @test
 * @bug 8330819
 * @summary Case where VPointer finds an "adr" CastX2P, which contains a CastLL,
 *          that has a ctrl after the pre-loop. This value cannot be used in the
 *          pre-loop limit for main-loop adjustment.
 * @modules java.base/jdk.internal.misc
 * @modules java.base/jdk.internal.util
 * @library /test/lib /
 * @run main/othervm -Xbatch compiler.loopopts.superword.TestMemorySegmentMainLoopAlignmentUnsafe
 * @run main                 compiler.loopopts.superword.TestMemorySegmentMainLoopAlignmentUnsafe
 */

public class TestMemorySegmentMainLoopAlignmentUnsafe {
    static final Unsafe UNSAFE = Unsafe.getUnsafe();

    static long RANGE = 6400;

    // Type definition for the lambda
    interface MemoryUnsafeOp {
        int apply(long base, long offset, int i);
    }

    public static void main(String[] args) {
        // Allocate some raw memory:
        long base = UNSAFE.allocateMemory(6400);
        for (int i = 0; i < 10_000; i++) {
            test(base, 0, TestMemorySegmentMainLoopAlignmentUnsafe::memoryUnsafeGet);
        }
    }

    // Somehow, it is necessary to pass this as a lambda
    static int memoryUnsafeGet(long base, long o, int i) {
        long index = o + i * 4L;
        // checkIndex -> CastLL: index >= 0.
        // Together with the info about i (known range for phi), this CastLL floats up to
        // the offset. Then we get adr = CastX2P(base + CastLL(offset)), where the CastLL
        // is pinned between the pre and main loop.
        Preconditions.checkIndex(index, RANGE, null);
        return UNSAFE.getInt(base + index);
    }

    static int test(long base, long offset, MemoryUnsafeOp f) {
        // Constant size array size allows a known range for the array access/loop iv i.
        int size = 16;
        int[] res = new int[size];
        int sum = 0;
        for (int i = 0; i < size; i++) {
            // With inlining, this eventually becomes:
            // sum += LoadI(unsafe) + LoadI(array)
            // and we attempt vectorization.
            sum += f.apply(base, offset, i) + res[i];
        }
        return sum;
    }
}
