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
 * @run junit BasicLazyReferenceArrayTest
 */

import org.junit.jupiter.api.*;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.lazy.Lazy;
import java.util.concurrent.lazy.LazyReferenceArray;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

final class BasicLazyReferenceArrayTest {

    private static final int SIZE = 63;
    private static final int INDEX = 13;

    LazyReferenceArray<Integer> lazy;
    CountingIntegerMapper mapper;

    @BeforeEach
    void setup() {
        lazy = Lazy.ofEmptyArray(SIZE);
        mapper = new CountingIntegerMapper(SIZE);
    }

    @Test
    void compute() {
        Integer val = lazy.computeIfEmpty(INDEX, mapper);
        assertEquals(INDEX, val);
        assertEquals(1, mapper.invocations(INDEX));
        Integer val2 = lazy.computeIfEmpty(INDEX, mapper);
        assertEquals(INDEX, val);
        assertEquals(1, mapper.invocations(INDEX));
    }

    @Test
    void nulls() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> lazy.computeIfEmpty(INDEX, null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> lazy.computeIfEmpty(INDEX, i -> null));
    }

    @Test
    void noPresetGet() {
        assertThrows(IllegalStateException.class,
                () -> lazy.apply(INDEX));
    }

    @Test
    void state() {
        assertEquals(Lazy.State.EMPTY, lazy.state(INDEX));
        Integer val = lazy.computeIfEmpty(INDEX, mapper);
        assertEquals(Lazy.State.PRESENT, lazy.state(INDEX));
    }

    @Test
    void presetMapperBasic() {
        LazyReferenceArray<Integer> presetLazy = Lazy.ofArray(SIZE, mapper);
        assertEquals(0, mapper.invocations(INDEX));
        for (int i = 0; i < 2; i++) {
            assertEquals(INDEX, presetLazy.apply(INDEX));
            assertEquals(1, mapper.invocations(INDEX));
        }
    }

    @Test
    void presetMapperNullMapping() {
        // Mapper is null
        assertThrows(NullPointerException.class,
                () -> Lazy.ofArray(SIZE, null));
        // Mapper returns null
        assertThrows(NullPointerException.class,
                () -> Lazy.ofArray(SIZE, i -> null).apply(INDEX));
    }

    @Test
    void toListSize() {
        assertEquals(SIZE, halfComputedList().size());
        assertEquals(SIZE - 2, halfComputedList().subList(1, SIZE - 1).size());
    }

    @Test
    void toListUnmodifiable() {
        toListUnmodifiable(halfComputedList());
        toListUnmodifiable(halfComputedList().subList(1, SIZE - 1));
    }

    void toListUnmodifiable(List<Integer> list) {
        List<Integer> otherList = List.of(1);
        List<Named<Consumer<List<Integer>>>> operations = List.of(
                Named.of("add(1)", l -> l.add(1)),
                Named.of("add(1,1)", l -> l.add(1, 1)),
                Named.of("addAll(L)", l -> l.addAll(otherList)),
                Named.of("addAll(2,L)", l -> l.addAll(2, otherList)),
                Named.of("clear()", l -> l.clear()),
                Named.of("remove(int)", l -> l.remove(1)),
                Named.of("remove(Object)", l -> l.remove((Integer) 1)),
                Named.of("removeAll(L)", l -> l.removeAll(otherList)),
                Named.of("retainAll(L)", l -> l.retainAll(otherList)),
                Named.of("set(1,1)", l -> l.set(1, 1)),
                Named.of("sort(C)", l -> l.sort(Comparator.naturalOrder())),
                Named.of("removeIf(i -> i ==2)", l -> l.removeIf(i -> i == 2))
        );
        for (var operation : operations) {
            assertThrows(UnsupportedOperationException.class, () ->
                            operation.getPayload().accept(list)
                    , operation.getName());
        }
    }

    @Test
    void testToString() throws InterruptedException {
        var timeout = System.nanoTime()+ TimeUnit.SECONDS.toNanos(2);
        AtomicBoolean lambdaInvoked = new AtomicBoolean();
        lazy = Lazy.ofEmptyArray(3);

        lazy.computeIfEmpty(1, i -> 1);
        try {
            System.out.println(2);
            lazy.computeIfEmpty(2, i -> {
                throw new UnsupportedOperationException("Test");
            });
        } catch (UnsupportedOperationException ignored) {
            // Happy path
        }

        var toString = lazy.toString();

        assertEquals("LazyReferenceArray[-, 1, !]", toString);
    }

    // Todo:repeate the test 1000 times
    @Test
    void threadTest() throws InterruptedException {
        var gate = new AtomicBoolean();
        var threads = IntStream.range(0, Runtime.getRuntime().availableProcessors() * 2)
                .mapToObj(i -> new Thread(() -> {
                    while (!gate.get()) {
                        Thread.onSpinWait();
                    }
                    // Try to access the instance "simultaneously"
                    lazy.computeIfEmpty(INDEX, mapper);
                }))
                .toList();
        threads.forEach(Thread::start);
        Thread.sleep(10);
        gate.set(true);
        join(threads);
        assertEquals(INDEX, lazy.apply(INDEX));
        assertEquals(1, mapper.invocations(INDEX));
    }

    private List<Integer> halfComputedList() {
        for (int i = 0; i < SIZE / 2; i++) {
            lazy.computeIfEmpty(i, mapper);
        }
        return lazy.asList();
    }

    private static void join(Collection<Thread> threads) {
        for (var t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
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
