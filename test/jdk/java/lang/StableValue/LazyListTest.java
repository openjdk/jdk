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

/* @test
 * @summary Basic tests for LazyList methods
 * @modules java.base/jdk.internal.lang
 * @compile --enable-preview -source ${jdk.version} LazyListTest.java
 * @run junit/othervm --enable-preview LazyListTest
 */

import jdk.internal.lang.StableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class LazyListTest {

    private static final int ZERO = 0;
    private static final int INDEX = 7;
    private static final int SIZE = 31;
    private static final IntFunction<Integer> IDENTITY = i -> i;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValue.lazyList(SIZE, null));
        assertThrows(IllegalArgumentException.class, () -> StableValue.lazyList(-1, IDENTITY));
    }

    @Test
    void isEmpty() {
        assertFalse(StableValue.lazyList(SIZE, IDENTITY).isEmpty());
        assertTrue(StableValue.lazyList(ZERO, IDENTITY).isEmpty());
    }

    @Test
    void size() {
        assertEquals(SIZE, StableValue.lazyList(SIZE, IDENTITY).size());
        assertEquals(ZERO, StableValue.lazyList(ZERO, IDENTITY).size());
    }

    @Test
    void get() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(IDENTITY);
        var lazy = StableValue.lazyList(SIZE, cif);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i, lazy.get(i));
            assertEquals(i + 1, cif.cnt());
            assertEquals(i, lazy.get(i));
            assertEquals(i + 1, cif.cnt());
        }
    }

    @Test
    void getException() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(_ -> {
            throw new UnsupportedOperationException();
        });
        var lazy = StableValue.lazyList(SIZE, cif);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(2, cif.cnt());
    }

    @Test
    void toArray() {
        assertArrayEquals(new Object[ZERO], StableValue.lazyList(ZERO, IDENTITY).toArray());
        assertArrayEquals(IntStream.range(0, SIZE).boxed().toList().toArray(), StableValue.lazyList(SIZE, IDENTITY).toArray());
    }

    @Test
    void toArrayWithArrayLarger() {
        Integer[] arr = new Integer[SIZE];
        arr[INDEX] = 1;
        assertSame(arr, StableValue.lazyList(INDEX, IDENTITY).toArray(arr));
        assertNull(arr[INDEX]);
    }

    @Test
    void toArrayWithArraySmaller() {
        Integer[] arr = new Integer[INDEX];
        Integer[] actual = StableValue.lazyList(SIZE, IDENTITY).toArray(arr);
        assertNotSame(arr, actual);
        Integer[] expected = IntStream.range(0, SIZE).boxed().toList().toArray(new Integer[0]);
        assertArrayEquals(expected, actual);
    }

    @Test
    void toArrayWithGenerator() {
        Integer[] expected = IntStream.range(0, SIZE).boxed().toList().toArray(Integer[]::new);
        Integer[] actual = StableValue.lazyList(SIZE, IDENTITY).toArray(Integer[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void firstIndex() {
        var lazy = StableValue.lazyList(SIZE, IDENTITY);
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, StableValue.lazyList(SIZE, IDENTITY).indexOf(i));
        }
        assertEquals(-1, lazy.indexOf(SIZE + 1));
    }

    @Test
    void lastIndex() {
        var lazy = StableValue.lazyList(SIZE, IDENTITY);
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, lazy.lastIndexOf(i));
        }
        assertEquals(-1, lazy.lastIndexOf(SIZE + 1));
    }

    @Test
    void toStringTest() {
        assertEquals("[]", StableValue.lazyList(ZERO, IDENTITY).toString());
        assertEquals("[0, 1]", StableValue.lazyList(2, IDENTITY).toString());
        assertEquals(IntStream.range(0, SIZE).boxed().toList().toString(), StableValue.lazyList(SIZE, IDENTITY).toString());
    }

    @Test
    void hashCodeTest() {
        assertEquals(List.of().hashCode(), StableValue.lazyList(ZERO, IDENTITY).hashCode());
        assertEquals(IntStream.range(0, SIZE).boxed().toList().hashCode(), StableValue.lazyList(SIZE, IDENTITY).hashCode());
    }

    @Test
    void equalsTest() {
        assertEquals(List.of(), StableValue.lazyList(ZERO, IDENTITY));
        assertEquals(IntStream.range(0, SIZE).boxed().toList(), StableValue.lazyList(SIZE, IDENTITY));
        assertFalse(StableValue.lazyList(SIZE, IDENTITY).equals("A"));
    }

    @Test
    void iteratorTotal() {
        var iterator = StableValue.lazyList(SIZE, IDENTITY).iterator();
        for (int i = 0; i < SIZE; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            assertEquals(i, iterator.next());
        }
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        AtomicInteger cnt = new AtomicInteger();
        iterator.forEachRemaining(_ -> cnt.incrementAndGet());
        assertEquals(0, cnt.get());
    }

    @Test
    void iteratorPartial() {
        var iterator = StableValue.lazyList(SIZE, IDENTITY).iterator();
        for (int i = 0; i < INDEX; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            assertEquals(i, iterator.next());
        }
        assertTrue(iterator.hasNext());
        AtomicInteger cnt = new AtomicInteger();
        iterator.forEachRemaining(_ -> cnt.incrementAndGet());
        assertEquals(SIZE - INDEX, cnt.get());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    // Immutability

    @ParameterizedTest
    @MethodSource("unsupportedOperations")
    void unsupported(Operation operation) {
        assertThrowsForOperation(UnsupportedOperationException.class, operation);
    }

    // Method parameter invariant checking

    @ParameterizedTest
    @MethodSource("nullAverseOperations")
    void nullAverse(Operation operation) {
        assertThrowsForOperation(NullPointerException.class, operation);
    }

    @ParameterizedTest
    @MethodSource("outOfBoundsOperations")
    void outOfBounds(Operation operation) {
        assertThrowsForOperation(IndexOutOfBoundsException.class, operation);
    }

    static <T extends Throwable> void assertThrowsForOperation(Class<T> expectedType, Operation operation) {
        var lazy = StableValue.lazyList(SIZE, IDENTITY);
        assertThrows(expectedType, () -> operation.accept(lazy));
        var sub = lazy.subList(1, SIZE / 2);
        assertThrows(expectedType, () -> operation.accept(sub));
        var subSub = sub.subList(1, sub.size() / 2);
        assertThrows(expectedType, () -> operation.accept(subSub));
    }

    // Implementing interfaces

    @Test
    void serializable() {
        assertFalse(StableValue.lazyList(SIZE, IDENTITY) instanceof Serializable);
        assertFalse(StableValue.lazyList(ZERO, IDENTITY) instanceof Serializable);
        assertFalse(StableValue.lazyList(SIZE, IDENTITY).subList(1, INDEX) instanceof Serializable);
    }

    @Test
    void randomAccess() {
        assertInstanceOf(RandomAccess.class, StableValue.lazyList(SIZE, IDENTITY));
        assertInstanceOf(RandomAccess.class, StableValue.lazyList(ZERO, IDENTITY));
        assertInstanceOf(RandomAccess.class, StableValue.lazyList(SIZE, IDENTITY).subList(1, INDEX));
    }

    // Support constructs

    record Operation(String name,
                     Consumer<List<Integer>> consumer) implements Consumer<List<Integer>> {
        @Override public void   accept(List<Integer> list) { consumer.accept(list); }
        @Override public String toString() { return name; }
    }

    static Stream<Operation> nullAverseOperations() {
        return Stream.of(
                new Operation("forEach",     l -> l.forEach(null)),
                new Operation("containsAll", l -> l.containsAll(null)),
                new Operation("toArray",     l -> l.toArray((Integer[]) null)),
                new Operation("toArray",     l -> l.toArray((IntFunction<Integer[]>) null))
        );
    }

    static Stream<Operation> outOfBoundsOperations() {
        return Stream.of(
                new Operation("get(-1)",        l -> l.get(-1)),
                new Operation("get(size)",      l -> l.get(l.size())),
                new Operation("sublist(-1,)",   l -> l.subList(-1, INDEX)),
                new Operation("sublist(,size)", l -> l.subList(0, l.size() + 1)),
                new Operation("listIter(-1)",   l -> l.listIterator(-1)),
                new Operation("listIter(size)", l -> l.listIterator(l.size() + 1))
        );
    }

    static Stream<Operation> unsupportedOperations() {
        final Set<Integer> SET = Set.of(0, 1);
        return Stream.of(
                new Operation("add(0)",            l -> l.add(0)),
                new Operation("add(0, 1)",         l -> l.add(0, 1)),
                new Operation("addAll(col)",       l -> l.addAll(SET)),
                new Operation("addAll(1, coll)",   l -> l.addAll(1, SET)),
                new Operation("addFirst(0)",       l -> l.addFirst(0)),
                new Operation("addLast(0)",        l -> l.addLast(0)),
                new Operation("clear",             List::clear),
                new Operation("remove(Obj)",       l -> l.remove((Object)1)),
                new Operation("remove(1)",         l -> l.remove(1)),
                new Operation("removeAll",         l -> l.removeAll(SET)),
                new Operation("removeFirst",       List::removeFirst),
                new Operation("removeLast",        List::removeLast),
                new Operation("removeIf",          l -> l.removeIf(i -> i % 2 == 0)),
                new Operation("replaceAll",        l -> l.replaceAll(i -> i + 1)),
                new Operation("sort",              l -> l.sort(Comparator.naturalOrder())),
                new Operation("iterator().remove", l -> l.iterator().remove()),
                new Operation("listIter().remove", l -> l.listIterator().remove()),
                new Operation("listIter().add",    l -> l.listIterator().add(1)),
                new Operation("listIter().set",    l -> l.listIterator().set(1))
        );
    }

}
