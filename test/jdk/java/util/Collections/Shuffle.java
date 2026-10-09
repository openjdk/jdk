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

/*
 * @test
 * @bug     8294693
 * @summary Basic test for Collections.shuffle
 * @key     randomness
 */

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.random.RandomGenerator;

public class Shuffle {
    static final int N = 100;

    public static void main(String[] args) {
        test(new ArrayList<>());
        test(new LinkedList<>());
    }

    static void test(List<Integer> list) {
        for (int i = 0; i < N; i++) {
            list.add(i);
        }
        Collections.shuffle(list);
        if (list.size() != N) {
            throw new RuntimeException(list.getClass() + ": size " + list.size() + " != " + N);
        }
        for (int i = 0; i < N; i++) {
            if (!list.contains(i)) {
                throw new RuntimeException(list.getClass() + ": does not contain " + i);
            }
        }
        checkRandom(list, l -> Collections.shuffle(l, new Random(1)));
        RandomGenerator.JumpableGenerator generator = RandomGenerator.JumpableGenerator.of("Xoshiro256PlusPlus");
        checkRandom(list, l -> Collections.shuffle(l, generator.copy()));
    }

    private static void checkRandom(List<Integer> list, Consumer<List<?>> randomizer) {
        list.sort(null);
        randomizer.accept(list);
        ArrayList<Integer> copy = new ArrayList<>(list);
        list.sort(null);
        if (list.equals(copy)) {
            // Assume that at least one pair of elements must be reordered during shuffle
            throw new RuntimeException(list.getClass() + ": list is not shuffled");
        }
        randomizer.accept(list);
        if (!list.equals(copy)) {
            throw new RuntimeException(list.getClass() + ": " + list + " != " + copy);
        }
    }
}
