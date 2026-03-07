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
 * @summary Basic tests for lazy list methods
 * @enablePreview
 * @modules java.base/java.util:+open
 * @run junit LazyListTest
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
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
        assertThrows(NullPointerException.class, () -> List.ofLazy(SIZE, null));
        assertThrows(IllegalArgumentException.class, () -> List.ofLazy(-1, IDENTITY));
    }

    @Test
    void isEmpty() {
        assertFalse(newLazyList().isEmpty());
        assertTrue(newEmptyLazyList().isEmpty());
    }

    @Test
    void size() {
        assertEquals(SIZE, newLazyList().size());
        assertEquals(ZERO, newEmptyLazyList().size());
    }

    @Test
    void get() {
        LazyConstantTestUtil.CountingIntFunction<Integer> cif = new LazyConstantTestUtil.CountingIntFunction<Integer>(IDENTITY);
        var lazy = List.ofLazy(SIZE, cif);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(i, lazy.get(i));
            assertEquals(i + 1, cif.cnt());
            assertEquals(i, lazy.get(i));
            assertEquals(i + 1, cif.cnt());
        }
    }

    @Test
    void getException() {
        LazyConstantTestUtil.CountingIntFunction<Integer> cif = new LazyConstantTestUtil.CountingIntFunction<Integer>(_ -> {
            throw new UnsupportedOperationException();
        });
        var lazy = List.ofLazy(SIZE, cif);
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(1, cif.cnt());
        assertThrows(UnsupportedOperationException.class, () -> lazy.get(INDEX));
        assertEquals(2, cif.cnt());
    }

    @Test
    void toArray() {
        assertArrayEquals(new Object[ZERO], newEmptyLazyList().toArray());
        assertArrayEquals(newRegularList().toArray(), newLazyList().toArray());
    }

    @Test
    void toArrayWithArrayLarger() {
        Integer[] actual = new Integer[SIZE];
        for (int i = 0; i < SIZE; i++) {
            actual[INDEX] = 100 + i;
        }
        var lazy = List.ofLazy(INDEX, IDENTITY);
        assertSame(actual, lazy.toArray(actual));
        Integer[] expected = IntStream.range(0, SIZE)
                .mapToObj(i -> i < INDEX ? i : null)
                .toArray(Integer[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void toArrayWithArraySmaller() {
        Integer[] arr = new Integer[INDEX];
        Integer[] actual = newLazyList().toArray(arr);
        assertNotSame(arr, actual);
        Integer[] expected = newRegularList().toArray(new Integer[0]);
        assertArrayEquals(expected, actual);
    }

    @Test
    void toArrayWithGenerator() {
        Integer[] expected = newRegularList().toArray(Integer[]::new);
        Integer[] actual = newLazyList().toArray(Integer[]::new);
        assertArrayEquals(expected, actual);
    }

    @Test
    void firstIndex() {
        var lazy = newLazyList();
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, lazy.indexOf(i));
        }
        assertEquals(-1, lazy.indexOf(SIZE + 1));
    }

    @Test
    void lastIndex() {
        var lazy = newLazyList();
        for (int i = INDEX; i < SIZE; i++) {
            assertEquals(i, lazy.lastIndexOf(i));
        }
        assertEquals(-1, lazy.lastIndexOf(SIZE + 1));
    }

    @Test
    void toStringTest() {
        assertEquals("[]", newEmptyLazyList().toString());
        assertEquals("[0, 1]", List.ofLazy(2, IDENTITY).toString());
    }

    @Test
    void hashCodeTest() {
        assertEquals(List.of().hashCode(), newEmptyLazyList().hashCode());
        assertEquals(newRegularList().hashCode(), newLazyList().hashCode());
    }

    @Test
    void equalsTest() {
        assertTrue(newEmptyLazyList().equals(List.of()));
        assertTrue(List.of().equals(newEmptyLazyList()));
        assertTrue(newLazyList().equals(newRegularList()));
        assertTrue(newRegularList().equals(newLazyList()));
        assertFalse(newLazyList().equals("A"));
    }

    @Test
    void iteratorTotal() {
        var iterator = newLazyList().iterator();
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
        var iterator = newLazyList().iterator();
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
        var lazy = newLazyList();
        var lazySubList = lazy.subList(1, SIZE);
        assertInstanceOf(RandomAccess.class, lazySubList);
        var regularList = newRegularList();
        var regularSubList = regularList.subList(1, SIZE);
        assertEquals(regularSubList, lazySubList);
    }

    @Test
    void subList2() {
        var lazy = newLazyList();
        var lazySubList = lazy.subList(1, SIZE);
        lazySubList.get(0);
        var eq = newLazyList();
        eq.get(1);
        assertEquals(eq.toString(), lazy.toString());
    }

    @Test
    void reversed() {
        var lazy = newLazyList();
        var reversedLazy = lazy.reversed();
        assertInstanceOf(RandomAccess.class, reversedLazy);
        assertEquals(SIZE - 1, reversedLazy.getFirst());
        assertEquals(0, reversedLazy.getLast());

        var reversed2Lazy = reversedLazy.reversed();
        assertInstanceOf(RandomAccess.class, reversed2Lazy);
        assertEquals(0, reversed2Lazy.getFirst());
        assertEquals(SIZE - 1, reversed2Lazy.getLast());
        // Make sure we get back a non-reversed implementation
        assertEquals(lazy.getClass().getName(), reversed2Lazy.getClass().getName());
    }

    @Test
    void sublistReversedToString() {
        var actual = List.ofLazy(4, IDENTITY);
        var expected = List.of(0, 1, 2, 3);
        for (UnaryOperation op : List.of(
                new UnaryOperation("subList", l -> l.subList(1, 3)),
                new UnaryOperation("reversed", List::reversed))) {
            actual = op.apply(actual);
            expected = op.apply(expected);
        }
        // Touch one of the elements
        actual.getLast();

        var actualToString = actual.toString();
        var expectedToString = expected.toString();
        assertEquals(expectedToString, actualToString);
    }

    @Test
    void recursiveCall() {
        AtomicReference<IntFunction<Integer>> ref = new AtomicReference<>();
        var lazy = List.ofLazy(SIZE, i -> ref.get().apply(i));
        ref.set(lazy::get);
        var x = assertThrows(IllegalStateException.class, () -> lazy.get(INDEX));
        assertEquals("Recursive initialization of a lazy collection is illegal", x.getMessage());
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
        var lazy = newLazyList();
        assertThrows(expectedType, () -> operation.accept(lazy));
        var sub = lazy.subList(1, SIZE / 2);
        assertThrows(expectedType, () -> operation.accept(sub));
        var subSub = sub.subList(1, sub.size() / 2);
        assertThrows(expectedType, () -> operation.accept(subSub));
    }

    // Implementing interfaces

    @Test
    void serializable() {
        serializable(newLazyList());
        serializable(newEmptyLazyList());
    }

    void serializable(List<Integer> list) {
        assertFalse(list instanceof Serializable);
        if (list.size()>INDEX) {
            assertFalse(newLazyList().subList(1, INDEX) instanceof Serializable);
        }
        assertFalse(list.iterator() instanceof Serializable);
        assertFalse(list.reversed() instanceof Serializable);
        assertFalse(list.spliterator() instanceof Serializable);
    }

    @Test
    void randomAccess() {
        assertInstanceOf(RandomAccess.class, newLazyList());
        assertInstanceOf(RandomAccess.class, newEmptyLazyList());
        assertInstanceOf(RandomAccess.class, newLazyList().subList(1, INDEX));
    }

    @Test
    void functionHolder() {
        LazyConstantTestUtil.CountingIntFunction<Integer> cif = new LazyConstantTestUtil.CountingIntFunction<>(IDENTITY);
        List<Integer> f1 = List.ofLazy(SIZE, cif);

        Object holder = LazyConstantTestUtil.functionHolder(f1);
        for (int i = 0; i < SIZE; i++) {
            assertEquals(SIZE - i, LazyConstantTestUtil.functionHolderCounter(holder));
            assertSame(cif, LazyConstantTestUtil.functionHolderFunction(holder));
            int v = f1.get(i);
            int v2 = f1.get(i);
        }
        assertEquals(0, LazyConstantTestUtil.functionHolderCounter(holder));
        assertNull(LazyConstantTestUtil.functionHolderFunction(holder));
    }

    // Support constructs

    record Operation(String name,
                     Consumer<List<Integer>> consumer) implements Consumer<List<Integer>> {
        @Override public void   accept(List<Integer> list) { consumer.accept(list); }
        @Override public String toString() { return name; }
    }

    record UnaryOperation(String name,
                     UnaryOperator<List<Integer>> operator) implements UnaryOperator<List<Integer>> {
        @Override public List<Integer> apply(List<Integer> list) { return operator.apply(list); }
        @Override public String toString() { return name; }
    }

    record ListFunction(String name,
                        Function<List<Integer>, Object> function) implements Function<List<Integer>, Object> {
        @Override public Object apply(List<Integer> list) { return function.apply(list); }
        @Override public String toString() { return name; }
    }

    static Stream<UnaryOperation> viewOperations() {
        return Stream.of(
                // We need identity to capture all combinations
                new UnaryOperation("identity", l -> l),
                new UnaryOperation("reversed", List::reversed),
                new UnaryOperation("subList", l -> l.subList(0, l.size()))
        );
    }

    static Stream<ListFunction> childOperations() {
        return Stream.of(
                // We need identity to capture all combinations
                new ListFunction("iterator", List::iterator),
                new ListFunction("listIterator", List::listIterator),
                new ListFunction("listIterator", List::stream)
        );
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

    static List<Integer> newLazyList() {
        return List.ofLazy(SIZE, IDENTITY);
    }

    static List<Integer> newEmptyLazyList() {
        return List.ofLazy(ZERO, IDENTITY);
    }

    static List<Integer> newRegularList() {
        return IntStream.range(0, SIZE).boxed().toList();
    }

    // Javadoc equivalent
    class LazyList<E> extends AbstractList<E> {

        private final List<LazyConstant<E>> backingList;

        public LazyList(int size, IntFunction<E> computingFunction) {
            this.backingList = IntStream.range(0, size)
                    .mapToObj(i -> LazyConstant.of(() -> computingFunction.apply(i)))
                    .toList();
        }

        @Override
        public E get(int index) {
            return backingList.get(index).get();
        }

        @Override
        public int size() {
            return backingList.size();
        }
    }
}
