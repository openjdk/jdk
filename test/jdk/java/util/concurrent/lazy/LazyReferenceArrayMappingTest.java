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
 * @summary Verify basic BasicLazyReferenceArray operations
 * @enablePreview
 * @run junit LazyReferenceArrayMappingTest
 */

import org.junit.jupiter.api.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.EmptyLazyArray;
import java.util.concurrent.lazy.Lazy;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class LazyReferenceArrayMappingTest {

    private static final int SIZE = 63;
    private static final int INDEX = 13;

    EmptyLazyArray<Integer> lazy;
    CountingIntegerMapper mapper;

    @BeforeEach
    void setup() {
        lazy = Lazy.ofEmptyArray(SIZE);
        mapper = new CountingIntegerMapper(SIZE);
    }

    @Test
    void mapInt() {
        AtomicInteger cnt = new AtomicInteger();
        IntFunction<Integer> mapper = i -> {
            return cnt.getAndIncrement();
        };

        EmptyLazyArray<Integer> translated = Lazy.ofEmptyTranslatedArray(SIZE, 2);
        // 2 is cached
        int first = translated.computeIfEmpty(2, mapper);
        assertEquals(0, first);
        int second = translated.computeIfEmpty(2, mapper);
        assertEquals(0, second);

        // 3 is not cached
        int third = translated.computeIfEmpty(3, mapper);
        assertEquals(1, third);
        int fourth = translated.computeIfEmpty(3, mapper);
        assertEquals(2, fourth);
    }

    static private final class CountingIntegerMapper implements IntFunction<Integer> {
        private final AtomicInteger[] invocations;

        public CountingIntegerMapper(int size) {
            this.invocations = IntStream.range(0, size)
                    .mapToObj(i -> new AtomicInteger())
                    .toArray(AtomicInteger[]::new);
        }

        @Override
        public Integer apply(int i) {
            invocations[i].incrementAndGet();
            return i;
        }

        int invocations(int i) {
            return invocations[i].get();
        }
    }

}
