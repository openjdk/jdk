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
 *                   TestShortRunningLongCountedLoopPredicatesClone
 * @run main/othervm TestShortRunningLongCountedLoopPredicatesClone
 */

import java.util.Objects;

// Predicate added after int counted loop is created depends on
// narrowed limit which depends on predicate added before the int
// counted loop was created: predicates need to be properly ordered.
public class TestShortRunningLongCountedLoopPredicatesClone {
    public static void main(String[] args) {
        A a = new A(100);
        for (int i = 0; i < 20_000; i++) {
            test1(a, 0);
        }
    }

    private static void test1(A a, long start) {
        long i = start;
        do {
            synchronized (new Object()) {}
            Objects.checkIndex(i, a.range);
            i++;
        } while (i < a.range);
    }

    static class A {
        A(long range) {
            this.range = range;
        }

        long range;
    }
}
