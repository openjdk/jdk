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
 * @run main/othervm -XX:-TieredCompilation -XX:-UseOnStackReplacement -XX:-BackgroundCompilation -XX:LoopUnrollLimit=100
 *                   TestShortRunningIntLoopWithLongChecksPredicates
 * @run main/othervm TestShortRunningIntLoopWithLongChecksPredicates
 */

import java.util.Objects;

// int RC is first eliminated by predication which causes Assertion
// Predicates to be added. Then the loop is transformed to make it
// possible to optimize long RC. Finally unrolling happen which
// require the Assertion Predicates to have been properly copied when
// the loop was transformed for the long range check.
public class TestShortRunningIntLoopWithLongChecksPredicates {
    private static volatile int volatileField;

    public static void main(String[] args) {
        int[] array = new int[100];
        for (int i = 0; i < 20_000; i++) {
            helper1(100, array, 100);
            test1(1, 100);
        }
    }

    private static void test1(int stop, long range) {
        int[] array = new int[3];
        helper1(stop, array, range);
    }

    private static void helper1(int stop, int[] array, long range) {
        for (int i = 0; i < stop; i++) {
            if (i % 2 == 0) {
                array[i] += i;
            } else {
                volatileField = 42;
            }
            Objects.checkIndex(i, range);
        }
    }
}
