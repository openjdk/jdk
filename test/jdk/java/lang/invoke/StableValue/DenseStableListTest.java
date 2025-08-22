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
 * @modules java.base/jdk.internal.invoke.stable
 * @enablePreview
 * @run junit DenseStableListTest
 */

import jdk.internal.invoke.stable.StableUtil;
import jdk.internal.invoke.stable.StandardStableValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.Serializable;
import java.lang.invoke.StableValue;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.RandomAccess;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

final class DenseStableListTest {

    private static final int ZERO = 0;
    private static final int INDEX = 7;
    private static final int SIZE = 31;
    private static final IntFunction<Integer> IDENTITY = i -> i;

    @Test
    void factoryInvariants() {
        assertThrows(IllegalArgumentException.class, () -> StableValue.ofList(-1));
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
        var list = newList();
        for (int i = 0; i < SIZE; i++) {
            assertFalse(list.get(i).isSet());
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void toArray() {
        assertArrayEquals(new Object[ZERO], newEmptyList().toArray());
        Object[] expected = newRegularList().toArray();
        Object[] actual = fill(newList()).toArray();
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Object e = expected[i];
            Object a = actual[i];
            if (!(e instanceof StableValue<?> esv)) {
                fail(e.toString());
            } else {
                if (!(a instanceof StableValue<?> asv)) {
                    fail(a.toString());
                } else {
                    assertEquals(((StableValue<Integer>)esv).orElse(-1), ((StableValue<Integer>)asv).orElse(-1));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void toArrayWithArrayLarger() {
        StableValue<Integer>[] actual = (StableValue<Integer>[]) new StableValue[SIZE];
        var list = fill(StableValue.ofList(INDEX));
        assertSame(actual, list.toArray(actual));
        StableValue<Integer>[] expected = IntStream.range(0, SIZE)
                .mapToObj(i -> i < INDEX ? StableValue.of(i) : null)
                .toArray(StableValue[]::new);
        assertStableValueArrayEqual(expected, actual);
    }

    @SuppressWarnings("unchecked")
    @Test
    void toArrayWithArraySmaller() {
        StableValue<Integer>[] arr = (StableValue<Integer>[]) new StableValue[INDEX];
        StableValue<Integer>[] actual = fill(newList()).toArray(arr);
        assertNotSame(arr, actual);
        StableValue<Integer>[] expected = newRegularList().toArray(new StableValue[0]);
        assertStableValueArrayEqual(expected, actual);
    }

    @SuppressWarnings("unchecked")
    @Test
    void toArrayWithGenerator() {
        StableValue<Integer>[] expected = newRegularList().toArray(StableValue[]::new);
        StableValue<Integer>[] actual = fill(newList()).toArray(StableValue[]::new);
        assertStableValueArrayEqual(expected, actual);
    }

    @Test
    void firstIndex() {
        var list = fill(newList());
        for (int i = INDEX; i < SIZE; i++) {
            var e = list.get(i);
            assertEquals(i, list.indexOf(e));
        }
        assertEquals(-1, list.indexOf(StableValue.of(INDEX)));
    }

    @Test
    void lastIndex() {
        var list = fill(newList());
        for (int i = INDEX; i < SIZE; i++) {
            var e = list.get(i);
            assertEquals(i, list.lastIndexOf(e));
        }
        assertEquals(-1, list.lastIndexOf(StableValue.of(INDEX)));
    }

    @Test
    void toStringTest() {
        assertEquals("[]", newEmptyList().toString());
        var list = StableValue.ofList(2);
        assertEquals("[.unset, .unset]", list.toString());
        list.get(0).trySet(0);
        assertEquals("[0, .unset]", list.toString());
        list.get(1).trySet(1);
        assertEquals("[0, 1]", list.toString());
    }

    @Test
    void hashCodeTest() {
        assertEquals(StableValue.ofList(0).hashCode(), newEmptyList().hashCode());
        var list = fill(newList());
        int h0 = list.hashCode();
        int h1 = list.hashCode();
        // This makes sure the hashcode does not depend on transient SV instances
        assertEquals(h0, h1);
        int expected = hashCodeDefinitionFor(list);
        assertEquals(expected, list.hashCode());
    }

    static <E> int hashCodeDefinitionFor(List<E> list) {
        int hashCode = 1;
        for (E e : list)
            hashCode = 31 * hashCode + (e == null ? 0 : e.hashCode());
        return hashCode;
    }

    @Test
    void equalsTest() {
        assertTrue(newEmptyList().equals(List.of()));
        assertTrue(List.of().equals(newEmptyList()));
/*        assertTrue(newList().equals(newRegularList()));
        assertTrue(newRegularList().equals(newList()));*/
        assertFalse(newList().equals("A"));
    }

    @Test
    void iteratorTotal() {
        var iterator = fill(newList()).iterator();
        for (int i = 0; i < SIZE; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            assertEquals(i, iterator.next().get());
        }
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        AtomicInteger cnt = new AtomicInteger();
        iterator.forEachRemaining(_ -> cnt.incrementAndGet());
        assertEquals(0, cnt.get());
    }

    @Test
    void iteratorPartial() {
        var iterator = fill(newList()).iterator();
        for (int i = 0; i < INDEX; i++) {
            assertTrue(iterator.hasNext());
            assertTrue(iterator.hasNext());
            assertEquals(i, iterator.next().get());
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
        var stable = fill(newList());
        var stableSubList = stable.subList(1, SIZE);
        assertInstanceOf(RandomAccess.class, stableSubList);
        var regularList = fill(newRegularList());
        var regularSubList = regularList.subList(1, SIZE);
        for (int i = 0; i < stableSubList.size(); i++) {
            assertEquals(regularSubList.get(i).get(), stableSubList.get(i).get());
        }
    }

    @Test
    void subList2() {
        var stable = newList();
        var stableSubList = stable.subList(1, SIZE);
        stableSubList.get(0).trySet(42);
        var eq = newList();
        eq.get(1).trySet(42);
        assertEquals(eq.toString(), stable.toString());
    }

    void assertUnevaluated(List<StableValue<Integer>> subList) {
        assertEquals(asString(".unset", subList), subList.toString());
    }

    @Test
    void reversed() {
        var stable = fill(newList());
        var reversed = stable.reversed();
        assertInstanceOf(RandomAccess.class, reversed);
        assertEquals(SIZE - 1, reversed.getFirst().get());
        assertEquals(0, reversed.getLast().get());

        var reversed2 = reversed.reversed();
        assertInstanceOf(RandomAccess.class, reversed2);
        assertEquals(0, reversed2.getFirst().get());
        assertEquals(SIZE - 1, reversed2.getLast().get());
        // Make sure we get back a non-reversed implementation
        assertEquals(stable.getClass().getName(), reversed2.getClass().getName());
    }

    @Test
    void sublistReversedToString() {
        var actual = fill(StableValue.ofList(4));
        var expected = IntStream.range(0, 4)
                .boxed()
                .map(StableValue::of)
                .toList();
        for (UnaryOperation op : List.of(
                new UnaryOperation("subList", l -> l.subList(1, 3)),
                new UnaryOperation("reversed", List::reversed))) {
            actual = op.apply(actual);
            expected = op.apply(expected);
        }

        var actualToString = actual.toString();
        var expectedToString = expected.toString();
        assertEquals(expectedToString, actualToString);
    }

    @Test
    void viewsStable() {
        viewOperations().forEach(op0 -> {
            viewOperations().forEach( op1 -> {
                viewOperations().forEach(op2 -> {
                    var list = newList();
                    var view1 = op0.apply(list);
                    var view2 = op1.apply(view1);
                    var view3 = op2.apply(view2);
                    var className3 = className(view3);
                    var transitions = className(list) + ", " +
                            op0 + " -> " + className(view1) + ", " +
                            op1 + " -> " + className(view2) + ", " +
                            op2 + " -> " + className3;
                    assertUnevaluated(list);
                    assertUnevaluated(view1);
                    assertUnevaluated(view2);
                    assertUnevaluated(view3);
                });
            });
        });
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

    void serializable(List<StableValue<Integer>> list) {
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
        StandardStableValue<Integer>[] array = StableUtil.array(SIZE);
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
                     Consumer<List<StableValue<Integer>>> consumer) implements Consumer<List<StableValue<Integer>>> {
        @Override public void   accept(List<StableValue<Integer>> list) { consumer.accept(list); }
        @Override public String toString() { return name; }
    }

    record UnaryOperation(String name,
                     UnaryOperator<List<StableValue<Integer>>> operator) implements UnaryOperator<List<StableValue<Integer>>> {
        @Override public List<StableValue<Integer>> apply(List<StableValue<Integer>> list) { return operator.apply(list); }
        @Override public String toString() { return name; }
    }

    record ListFunction(String name,
                        Function<List<StableValue<Integer>>, Object> function) implements Function<List<StableValue<Integer>>, Object> {
        @Override public Object apply(List<StableValue<Integer>> list) { return function.apply(list); }
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
                new ListFunction("stream", List::stream)
        );
    }

    static Stream<Operation> nullAverseOperations() {
        return Stream.of(
                new Operation("forEach",     l -> l.forEach(null)),
                new Operation("containsAll", l -> l.containsAll(null)),
                new Operation("toArray",     l -> l.toArray((StableValue<Integer>[]) null)),
                new Operation("toArray",     l -> l.toArray((IntFunction<StableValue<Integer>[]>) null))
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
        final Set<StableValue<Integer>> SET = Set.of(StableValue.of(0), StableValue.of(1));
        return Stream.of(
                new Operation("add(0)",            l -> l.add(StableValue.of(0))),
                new Operation("add(0, 1)",         l -> l.add(0, StableValue.of(1))),
                new Operation("addAll(col)",       l -> l.addAll(SET)),
                new Operation("addAll(1, coll)",   l -> l.addAll(1, SET)),
                new Operation("addFirst(0)",       l -> l.addFirst(StableValue.of(0))),
                new Operation("addLast(0)",        l -> l.addLast(StableValue.of(0))),
                new Operation("clear",             List::clear),
                new Operation("remove(Obj)",       l -> l.remove(StableValue.of(1))),
                new Operation("remove(1)",         l -> l.remove(1)),
                new Operation("removeAll",         l -> l.removeAll(SET)),
                new Operation("removeFirst",       List::removeFirst),
                new Operation("removeLast",        List::removeLast),
                new Operation("removeIf",          l -> l.removeIf(i -> i.get() % 2 == 0)),
                new Operation("replaceAll",        l -> l.replaceAll(i -> StableValue.of(i.get()+1))),
                new Operation("sort",              l -> l.sort(Comparator.comparingInt(StableValue::hashCode))),
                new Operation("iterator().remove", l -> l.iterator().remove()),
                new Operation("listIter().remove", l -> l.listIterator().remove()),
                new Operation("listIter().add",    l -> l.listIterator().add(StableValue.of(1))),
                new Operation("listIter().set",    l -> l.listIterator().set(StableValue.of(1)))
        );
    }

    static List<StableValue<Integer>> newList() {
        return StableValue.ofList(SIZE);
    }

    static List<StableValue<Integer>> fill(List<StableValue<Integer>> target) {
        IntStream.range(0, target.size())
                .forEach(i -> target.get(i).trySet(i));
        return target;
    }

    static List<StableValue<Integer>> newEmptyList() {
        return StableValue.ofList(ZERO);
    }

    static List<StableValue<Integer>> newRegularList() {
        return IntStream.range(0, SIZE)
                .boxed()
                .map(StableValue::of)
                .toList();
    }

    static String asString(String first, List<StableValue<Integer>> list) {
        return "[" + first + ", " + Stream.generate(() -> ".unset")
                .limit(list.size() - 1)
                .collect(Collectors.joining(", ")) + "]";
    }

    static String className(Object o) {
        return o.getClass().getName();
    }

    static void assertStableValueArrayEqual(StableValue<Integer>[] expected, StableValue<Integer>[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            StableValue<Integer> e = expected[i];
            StableValue<Integer> a = actual[i];
            if (e == null && a == null) {
                continue;
            }
            assertEquals(e.orElse(-1), a.orElse(-1), Integer.toString(i));
        }
    }
}
