/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic tests for StableList methods
 * @modules java.base/jdk.internal.lang.stable
 * @enablePreview
 * @run junit StableListTest
 */

import jdk.internal.lang.stable.StableUtil;
import jdk.internal.lang.stable.StableValueImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class StableListTest {

    private static final int ZERO = 0;
    private static final int INDEX = 7;
    private static final int SIZE = 31;
    private static final IntFunction<Integer> IDENTITY = i -> i;

    @Test
    void factoryInvariants() {
        assertThrows(NullPointerException.class, () -> StableValue.list(SIZE, null));
        assertThrows(IllegalArgumentException.class, () -> StableValue.list(-1, IDENTITY));
    }

    @Test
    void isEmpty() {
        assertFalse(newList().isEmpty());
        assertTrue(newEmptyList().isEmpty());
    }

    @Test
    void size() {
        assertEquals(SIZE, newList().size());
        assertEquals(ZERO, newEmptyList().size());
    }

    @Test
    void get() {
        StableTestUtil.CountingIntFunction<Integer> cif = new StableTestUtil.CountingIntFunction<>(IDENTITY);
        var lazy = StableValue.list(SIZE, cif);
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
        var lazy = StableValue.list(SIZE, cif);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(2, cif.cnt());
    }

    @Test
    void toArray() {
        assertArrayEquals(new Object[ZERO], newEmptyList().toArray());
        assertArrayEquals(newRegularList().toArray(), newList().toArray());
    }

    @Test
    void toArrayWithArrayLarger() {
        Integer[] actual = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++) {
            actual[INDEX] = 100 + i;
        }
        var list = StableValue.list(INDEX, IDENTITY);
        assertSame(actual, list.toArray(actual));
        Integer[] expected = IntStream.range(0, SIZE)
                .mapToObj(i -> i < INDEX ? i : null)
                .toArray(Integer[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void toArrayWithArraySmaller() {
        Integer[] arr = new Integer[INDEX];
        Integer[] actual = newList().toArray(arr);
        assertNotSame(arr, actual);
        Integer[] expected = newRegularList().toArray(new Integer[0]);
        assertArrayEquals(expected, actual);
    }

    @Test
    void toArrayWithGenerator() {
        Integer[] expected = newRegularList().toArray(Integer[]::new);
        Integer[] actual = newList().toArray(Integer[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void firstIndex() {
        var lazy = newList();
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, lazy.indexOf(i));
        }
        assertEquals(-1, lazy.indexOf(SIZE + 1));
    }

    @Test
    void lastIndex() {
        var lazy = newList();
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, lazy.lastIndexOf(i));
        }
        assertEquals(-1, lazy.lastIndexOf(SIZE + 1));
    }

    @Test
    void toStringTest() {
        assertEquals("[]", newEmptyList().toString());
        var list = StableValue.list(2, IDENTITY);
        assertEquals("[.unset, .unset]", list.toString());
        list.get(0);
        assertEquals("[0, .unset]", list.toString());
        list.get(1);
        assertEquals("[0, 1]", list.toString());
    }

    @Test
    void hashCodeTest() {
        assertEquals(List.of().hashCode(), newEmptyList().hashCode());
        assertEquals(newRegularList().hashCode(), newList().hashCode());
    }

    @Test
    void equalsTest() {
        assertTrue(newEmptyList().equals(List.of()));
        assertTrue(List.of().equals(newEmptyList()));
        assertTrue(newList().equals(newRegularList()));
        assertTrue(newRegularList().equals(newList()));
        assertFalse(newList().equals("A"));
    }

    @Test
    void equalsPartialEvaluationTest() {
        var list = StableValue.list(2, IDENTITY);
        assertFalse(list.equals(List.of(0)));
        assertEquals("[0, .unset]", list.toString());
        assertTrue(list.equals(List.of(0, 1)));
        assertEquals("[0, 1]", list.toString());
    }

    @Test
    void iteratorTotal() {
        var iterator = newList().iterator();
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
        var iterator = newList().iterator();
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

    @Test
    void subList() {
        var lazy = newList();
        var lazySubList = lazy.subList(1, SIZE);
        assertInstanceOf(RandomAccess.class, lazySubList);
        var regularList = newRegularList();
        var regularSubList = regularList.subList(1, SIZE);
        assertEquals(regularSubList, lazySubList);
    }

    @Test
    void subList2() {
        var lazy = newList();
        var lazySubList = lazy.subList(1, SIZE);
        lazySubList.get(0);
        var eq = newList();
        eq.get(1);
        assertEquals(eq.toString(), lazy.toString());
    }

    @Test
    void subListToString() {
        subListToString0(newList());
        subListToString0(newList().subList(1, SIZE));
        subListToString0(newList().subList(1, SIZE).subList(0, SIZE - 2));
    }

    void subListToString0(List<Integer> subList) {
        assertEquals(asString(".unset", subList), subList.toString());

        var first = subList.getFirst();
        assertEquals(asString(first.toString(), subList), subList.toString());
    }

    @Test
    void reversed() {
        var reversed = newList().reversed();
        assertInstanceOf(RandomAccess.class, reversed);
        assertEquals(SIZE - 1, reversed.getFirst());
        assertEquals(0, reversed.getLast());

        var reversed2 = reversed.reversed();
        assertInstanceOf(RandomAccess.class, reversed2);
        assertEquals(0, reversed2.getFirst());
        assertEquals(SIZE - 1, reversed2.getLast());
        // Make sure we get back a non-reversed implementation
        assertEquals("java.util.ImmutableCollections$StableList", reversed2.getClass().getName());
    }

    @Test
    void reversedToString() {
        var reversed = newList().reversed();
        subListToString0(reversed);
    }

    @Test
    void subListReversedToString() {
        var list = newList().subList(1, SIZE - 1).reversed();
        // This combination is not lazy. There has to be a limit somewhere.
        var regularList = newRegularList().subList(1, SIZE - 1).reversed();
        assertEquals(regularList.toString(), list.toString());
    }

    @Test
    void recursiveCall() {
        AtomicReference<IntFunction<Integer>> ref = new AtomicReference<>();
        var lazy = StableValue.list(SIZE, i -> ref.get().apply(i));
        ref.set(lazy::get);
        var x = assertThrows(IllegalStateException.class, () -> lazy.get(INDEX));
        assertEquals("Recursive initialization of a stable value is illegal", x.getMessage());
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
        var lazy = newList();
        assertThrows(expectedType, () -> operation.accept(lazy));
        var sub = lazy.subList(1, SIZE / 2);
        assertThrows(expectedType, () -> operation.accept(sub));
        var subSub = sub.subList(1, sub.size() / 2);
        assertThrows(expectedType, () -> operation.accept(subSub));
    }

    // Implementing interfaces

    @Test
    void serializable() {
        serializable(newList());
        serializable(newEmptyList());
    }

    void serializable(List<Integer> list) {
        assertFalse(list instanceof Serializable);
        if (list.size()>INDEX) {
            assertFalse(newList().subList(1, INDEX) instanceof Serializable);
        }
        assertFalse(list.iterator() instanceof Serializable);
        assertFalse(list.reversed() instanceof Serializable);
        assertFalse(list.spliterator() instanceof Serializable);
    }

    @Test
    void randomAccess() {
        assertInstanceOf(RandomAccess.class, newList());
        assertInstanceOf(RandomAccess.class, newEmptyList());
        assertInstanceOf(RandomAccess.class, newList().subList(1, INDEX));
    }

    @Test
    void distinct() {
        StableValueImpl<Integer>[] array = StableUtil.array(SIZE);
        assertEquals(SIZE, array.length);
        // Check, every StableValue is distinct
        Map<StableValue<Integer>, Boolean> idMap = new IdentityHashMap<>();
        for (var e: array) {
            idMap.put(e, true);
        }
        assertEquals(SIZE, idMap.size());
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

    static List<Integer> newList() {
        return StableValue.list(SIZE, IDENTITY);
    }

    static List<Integer> newEmptyList() {
        return StableValue.list(ZERO, IDENTITY);
    }

    static List<Integer> newRegularList() {
        return IntStream.range(0, SIZE).boxed().toList();
    }

    static String asString(String first, List<Integer> list) {
        return "[" + first + ", " + Stream.generate(() -> ".unset")
                .limit(list.size() - 1)
                .collect(Collectors.joining(", ")) + "]";
    }

}
