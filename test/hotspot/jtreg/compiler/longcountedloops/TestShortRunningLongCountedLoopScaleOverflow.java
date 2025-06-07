/*
 * Copyright (c) 2025, Red Hat, Inc. All rights reserved.
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

/**
 * @test
 * @bug 8342692
 * @summary C2: long counted loop/long range checks: don't create loop-nest for short running loops
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:LoopMaxUnroll=0
 *                   -XX:-UseLoopPredicate -XX:-RangeCheckElimination TestShortRunningLongCountedLoopScaleOverflow
 * @run main/othervm TestShortRunningLongCountedLoopScaleOverflow
 */

import java.util.Objects;

// When scale is large, even if loop is short running having a single
// counted loop is not possible.
public class TestShortRunningLongCountedLoopScaleOverflow {
    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            test1(Integer.MAX_VALUE, 0);
            test2(Integer.MAX_VALUE, 0, 100);
        }
        boolean exception = false;
        try {
            test1(Integer.MAX_VALUE, 10);
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            exception = true;
        }
        if (!exception) {
            throw new RuntimeException("Expected exception not thrown");
        }
        exception = false;
        try {
            test2(Integer.MAX_VALUE, 10, 100);
        } catch (IndexOutOfBoundsException indexOutOfBoundsException) {
            exception = true;
        }
        if (!exception) {
            throw new RuntimeException("Expected exception not thrown");
        }
    }

    static final long veryLargeScale = 1 << 29;

    private static void test1(long range, long j) {
        Objects.checkIndex(0, range);
        for (long i = 0; i < 100; i++) {
            if (i == j) {
                Objects.checkIndex(veryLargeScale * i, range);
            }
        }
    }

    private static void test2(long range, long j, long stop) {
        Objects.checkIndex(0, range);
        for (long i = 0; i < stop; i++) {
            if (i == j) {
                Objects.checkIndex(veryLargeScale * i, range);
            }
        }
    }
}
