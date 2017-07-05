/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8005698
 * @run testng SpliteratorCollisions
 * @summary Spliterator traversing and splitting hash maps containing colliding hashes
 * @author Brent Christian
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.testng.Assert.*;
import static org.testng.Assert.assertEquals;

@Test
public class SpliteratorCollisions {

    private static List<Integer> SIZES = Arrays.asList(0, 1, 10, 100, 1000);

    private static class SpliteratorDataBuilder<T> {
        List<Object[]> data;
        List<T> exp;
        Map<T, T> mExp;

        SpliteratorDataBuilder(List<Object[]> data, List<T> exp) {
            this.data = data;
            this.exp = exp;
            this.mExp = createMap(exp);
        }

        Map<T, T> createMap(List<T> l) {
            Map<T, T> m = new LinkedHashMap<>();
            for (T t : l) {
                m.put(t, t);
            }
            return m;
        }

        void add(String description, Collection<?> expected, Supplier<Spliterator<?>> s) {
            description = joiner(description).toString();
            data.add(new Object[]{description, expected, s});
        }

        void add(String description, Supplier<Spliterator<?>> s) {
            add(description, exp, s);
        }

        void addCollection(Function<Collection<T>, ? extends Collection<T>> c) {
            add("new " + c.apply(Collections.<T>emptyList()).getClass().getName() + ".spliterator()",
                () -> c.apply(exp).spliterator());
        }

        void addList(Function<Collection<T>, ? extends List<T>> l) {
            // @@@ If collection is instance of List then add sub-list tests
            addCollection(l);
        }

        void addMap(Function<Map<T, T>, ? extends Map<T, T>> m) {
            String description = "new " + m.apply(Collections.<T, T>emptyMap()).getClass().getName();
            add(description + ".keySet().spliterator()", () -> m.apply(mExp).keySet().spliterator());
            add(description + ".values().spliterator()", () -> m.apply(mExp).values().spliterator());
            add(description + ".entrySet().spliterator()", mExp.entrySet(), () -> m.apply(mExp).entrySet().spliterator());
        }

        StringBuilder joiner(String description) {
            return new StringBuilder(description).
                    append(" {").
                    append("size=").append(exp.size()).
                    append("}");
        }
    }

    static Object[][] spliteratorDataProvider;

    @DataProvider(name = "HashableIntSpliterator")
    public static Object[][] spliteratorDataProvider() {
        if (spliteratorDataProvider != null) {
            return spliteratorDataProvider;
        }

        List<Object[]> data = new ArrayList<>();
        for (int size : SIZES) {
            List<HashableInteger> exp = listIntRange(size, false);
            SpliteratorDataBuilder<HashableInteger> db = new SpliteratorDataBuilder<>(data, exp);

            // Maps
            db.addMap(HashMap::new);
            db.addMap(LinkedHashMap::new);

            // Collections that use HashMap
            db.addCollection(HashSet::new);
            db.addCollection(LinkedHashSet::new);
            db.addCollection(TreeSet::new);
        }
        return spliteratorDataProvider = data.toArray(new Object[0][]);
    }

    static Object[][] spliteratorDataProviderWithNull;

    @DataProvider(name = "HashableIntSpliteratorWithNull")
    public static Object[][] spliteratorNullDataProvider() {
        if (spliteratorDataProviderWithNull != null) {
            return spliteratorDataProviderWithNull;
        }

        List<Object[]> data = new ArrayList<>();
        for (int size : SIZES) {
            List<HashableInteger> exp = listIntRange(size, true);
            SpliteratorDataBuilder<HashableInteger> db = new SpliteratorDataBuilder<>(data, exp);

            // Maps
            db.addMap(HashMap::new);
            db.addMap(LinkedHashMap::new);
            // TODO: add this back in if we decide to keep TreeBin in WeakHashMap
            //db.addMap(WeakHashMap::new);

            // Collections that use HashMap
            db.addCollection(HashSet::new);
            db.addCollection(LinkedHashSet::new);
//            db.addCollection(TreeSet::new);

        }
        return spliteratorDataProviderWithNull = data.toArray(new Object[0][]);
    }

    static final class HashableInteger implements Comparable<HashableInteger> {

        final int value;
        final int hashmask; //yes duplication

        HashableInteger(int value, int hashmask) {
            this.value = value;
            this.hashmask = hashmask;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof HashableInteger) {
                HashableInteger other = (HashableInteger) obj;

                return other.value == value;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return value % hashmask;
        }

        @Override
        public int compareTo(HashableInteger o) {
            return value - o.value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    private static List<HashableInteger> listIntRange(int upTo, boolean withNull) {
        List<HashableInteger> exp = new ArrayList<>();
        if (withNull) {
            exp.add(null);
        }
        for (int i = 0; i < upTo; i++) {
            exp.add(new HashableInteger(i, 10));
        }
        return Collections.unmodifiableList(exp);
    }

    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testNullPointerException(String description, Collection exp, Supplier<Spliterator> s) {
        executeAndCatch(NullPointerException.class, () -> s.get().forEachRemaining(null));
        executeAndCatch(NullPointerException.class, () -> s.get().tryAdvance(null));
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testNullPointerExceptionWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        executeAndCatch(NullPointerException.class, () -> s.get().forEachRemaining(null));
        executeAndCatch(NullPointerException.class, () -> s.get().tryAdvance(null));
    }


    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testForEach(String description, Collection exp, Supplier<Spliterator> s) {
        testForEach(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testForEachWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testForEach(exp, s, (Consumer<Object> b) -> b);
    }


    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testTryAdvance(String description, Collection exp, Supplier<Spliterator> s) {
        testTryAdvance(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testTryAdvanceWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testTryAdvance(exp, s, (Consumer<Object> b) -> b);
    }

/* skip this test until 8013649 is fixed
    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testMixedTryAdvanceForEach(String description, Collection exp, Supplier<Spliterator> s) {
        testMixedTryAdvanceForEach(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testMixedTryAdvanceForEachWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testMixedTryAdvanceForEach(exp, s, (Consumer<Object> b) -> b);
    }
*/

    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitAfterFullTraversal(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitAfterFullTraversal(s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitAfterFullTraversalWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitAfterFullTraversal(s, (Consumer<Object> b) -> b);
    }


    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitOnce(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitOnce(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitOnceWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitOnce(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitSixDeep(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitSixDeep(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitSixDeepWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitSixDeep(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliterator")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitUntilNull(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitUntilNull(exp, s, (Consumer<Object> b) -> b);
    }

    @Test(dataProvider = "HashableIntSpliteratorWithNull")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void testSplitUntilNullWithNull(String description, Collection exp, Supplier<Spliterator> s) {
        testSplitUntilNull(exp, s, (Consumer<Object> b) -> b);
    }

    private static <T, S extends Spliterator<T>> void testForEach(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        S spliterator = supplier.get();
        long sizeIfKnown = spliterator.getExactSizeIfKnown();
        boolean isOrdered = spliterator.hasCharacteristics(Spliterator.ORDERED);

        ArrayList<T> fromForEach = new ArrayList<>();
        spliterator = supplier.get();
        Consumer<T> addToFromForEach = boxingAdapter.apply(fromForEach::add);
        spliterator.forEachRemaining(addToFromForEach);

        // Assert that forEach now produces no elements
        spliterator.forEachRemaining(boxingAdapter.apply(e -> fail("Spliterator.forEach produced an element after spliterator exhausted: " + e)));
        // Assert that tryAdvance now produce no elements
        spliterator.tryAdvance(boxingAdapter.apply(e -> fail("Spliterator.tryAdvance produced an element after spliterator exhausted: " + e)));

        // assert that size, tryAdvance, and forEach are consistent
        if (sizeIfKnown >= 0) {
            assertEquals(sizeIfKnown, exp.size());
        }
        if (exp.contains(null)) {
            assertTrue(fromForEach.contains(null));
        }
        assertEquals(fromForEach.size(), exp.size());

        assertContents(fromForEach, exp, isOrdered);
    }

    private static <T, S extends Spliterator<T>> void testTryAdvance(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        S spliterator = supplier.get();
        long sizeIfKnown = spliterator.getExactSizeIfKnown();
        boolean isOrdered = spliterator.hasCharacteristics(Spliterator.ORDERED);

        spliterator = supplier.get();
        ArrayList<T> fromTryAdvance = new ArrayList<>();
        Consumer<T> addToFromTryAdvance = boxingAdapter.apply(fromTryAdvance::add);
        while (spliterator.tryAdvance(addToFromTryAdvance)) { }

        // Assert that forEach now produces no elements
        spliterator.forEachRemaining(boxingAdapter.apply(e -> fail("Spliterator.forEach produced an element after spliterator exhausted: " + e)));
        // Assert that tryAdvance now produce no elements
        spliterator.tryAdvance(boxingAdapter.apply(e -> fail("Spliterator.tryAdvance produced an element after spliterator exhausted: " + e)));

        // assert that size, tryAdvance, and forEach are consistent
        if (sizeIfKnown >= 0) {
            assertEquals(sizeIfKnown, exp.size());
        }
        assertEquals(fromTryAdvance.size(), exp.size());

        assertContents(fromTryAdvance, exp, isOrdered);
    }

    private static <T, S extends Spliterator<T>> void testMixedTryAdvanceForEach(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        S spliterator = supplier.get();
        long sizeIfKnown = spliterator.getExactSizeIfKnown();
        boolean isOrdered = spliterator.hasCharacteristics(Spliterator.ORDERED);

        // tryAdvance first few elements, then forEach rest
        ArrayList<T> dest = new ArrayList<>();
        spliterator = supplier.get();
        Consumer<T> addToDest = boxingAdapter.apply(dest::add);
        for (int i = 0; i < 10 && spliterator.tryAdvance(addToDest); i++) { }
        spliterator.forEachRemaining(addToDest);

        // Assert that forEach now produces no elements
        spliterator.forEachRemaining(boxingAdapter.apply(e -> fail("Spliterator.forEach produced an element after spliterator exhausted: " + e)));
        // Assert that tryAdvance now produce no elements
        spliterator.tryAdvance(boxingAdapter.apply(e -> fail("Spliterator.tryAdvance produced an element after spliterator exhausted: " + e)));

        if (sizeIfKnown >= 0) {
            assertEquals(sizeIfKnown, dest.size());
        }
        assertEquals(dest.size(), exp.size());

        if (isOrdered) {
            assertEquals(dest, exp);
        }
        else {
            assertContentsUnordered(dest, exp);
        }
    }

    private static <T, S extends Spliterator<T>> void testSplitAfterFullTraversal(
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        // Full traversal using tryAdvance
        Spliterator<T> spliterator = supplier.get();
        while (spliterator.tryAdvance(boxingAdapter.apply(e -> { }))) { }
        Spliterator<T> split = spliterator.trySplit();
        assertNull(split);

        // Full traversal using forEach
        spliterator = supplier.get();
        spliterator.forEachRemaining(boxingAdapter.apply(e -> {
        }));
        split = spliterator.trySplit();
        assertNull(split);

        // Full traversal using tryAdvance then forEach
        spliterator = supplier.get();
        spliterator.tryAdvance(boxingAdapter.apply(e -> { }));
        spliterator.forEachRemaining(boxingAdapter.apply(e -> {
        }));
        split = spliterator.trySplit();
        assertNull(split);
    }

    private static <T, S extends Spliterator<T>> void testSplitOnce(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        S spliterator = supplier.get();
        long sizeIfKnown = spliterator.getExactSizeIfKnown();
        boolean isOrdered = spliterator.hasCharacteristics(Spliterator.ORDERED);

        ArrayList<T> fromSplit = new ArrayList<>();
        Spliterator<T> s1 = supplier.get();
        Spliterator<T> s2 = s1.trySplit();
        long s1Size = s1.getExactSizeIfKnown();
        long s2Size = (s2 != null) ? s2.getExactSizeIfKnown() : 0;

        Consumer<T> addToFromSplit = boxingAdapter.apply(fromSplit::add);
        if (s2 != null)
            s2.forEachRemaining(addToFromSplit);
        s1.forEachRemaining(addToFromSplit);

        if (sizeIfKnown >= 0) {
            assertEquals(sizeIfKnown, fromSplit.size());
            if (s1Size >= 0 && s2Size >= 0)
                assertEquals(sizeIfKnown, s1Size + s2Size);
        }
        assertContents(fromSplit, exp, isOrdered);
    }

    private static <T, S extends Spliterator<T>> void testSplitSixDeep(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        S spliterator = supplier.get();
        boolean isOrdered = spliterator.hasCharacteristics(Spliterator.ORDERED);

        for (int depth=0; depth < 6; depth++) {
            List<T> dest = new ArrayList<>();
            spliterator = supplier.get();

            assertSpliterator(spliterator);

            // verify splitting with forEach
            visit(depth, 0, dest, spliterator, boxingAdapter, spliterator.characteristics(), false);
            assertContents(dest, exp, isOrdered);

            // verify splitting with tryAdvance
            dest.clear();
            spliterator = supplier.get();
            visit(depth, 0, dest, spliterator, boxingAdapter, spliterator.characteristics(), true);
            assertContents(dest, exp, isOrdered);
        }
    }

    private static <T, S extends Spliterator<T>> void visit(int depth, int curLevel,
                                                            List<T> dest, S spliterator, UnaryOperator<Consumer<T>> boxingAdapter,
                                                            int rootCharacteristics, boolean useTryAdvance) {
        if (curLevel < depth) {
            long beforeSize = spliterator.getExactSizeIfKnown();
            Spliterator<T> split = spliterator.trySplit();
            if (split != null) {
                assertSpliterator(split, rootCharacteristics);
                assertSpliterator(spliterator, rootCharacteristics);

                if ((rootCharacteristics & Spliterator.SUBSIZED) != 0 &&
                    (rootCharacteristics & Spliterator.SIZED) != 0) {
                    assertEquals(beforeSize, split.estimateSize() + spliterator.estimateSize());
                }
                visit(depth, curLevel + 1, dest, split, boxingAdapter, rootCharacteristics, useTryAdvance);
            }
            visit(depth, curLevel + 1, dest, spliterator, boxingAdapter, rootCharacteristics, useTryAdvance);
        }
        else {
            long sizeIfKnown = spliterator.getExactSizeIfKnown();
            if (useTryAdvance) {
                Consumer<T> addToDest = boxingAdapter.apply(dest::add);
                int count = 0;
                while (spliterator.tryAdvance(addToDest)) {
                    ++count;
                }

                if (sizeIfKnown >= 0)
                    assertEquals(sizeIfKnown, count);

                // Assert that forEach now produces no elements
                spliterator.forEachRemaining(boxingAdapter.apply(e -> fail("Spliterator.forEach produced an element after spliterator exhausted: " + e)));

                Spliterator<T> split = spliterator.trySplit();
                assertNull(split);
            }
            else {
                List<T> leafDest = new ArrayList<>();
                Consumer<T> addToLeafDest = boxingAdapter.apply(leafDest::add);
                spliterator.forEachRemaining(addToLeafDest);

                if (sizeIfKnown >= 0)
                    assertEquals(sizeIfKnown, leafDest.size());

                // Assert that forEach now produces no elements
                spliterator.tryAdvance(boxingAdapter.apply(e -> fail("Spliterator.tryAdvance produced an element after spliterator exhausted: " + e)));

                Spliterator<T> split = spliterator.trySplit();
                assertNull(split);

                dest.addAll(leafDest);
            }
        }
    }

    private static <T, S extends Spliterator<T>> void testSplitUntilNull(
            Collection<T> exp,
            Supplier<S> supplier,
            UnaryOperator<Consumer<T>> boxingAdapter) {
        Spliterator<T> s = supplier.get();
        boolean isOrdered = s.hasCharacteristics(Spliterator.ORDERED);
        assertSpliterator(s);

        List<T> splits = new ArrayList<>();
        Consumer<T> c = boxingAdapter.apply(splits::add);

        testSplitUntilNull(new SplitNode<T>(c, s));
        assertContents(splits, exp, isOrdered);
    }

    private static class SplitNode<T> {
        // Constant for every node
        final Consumer<T> c;
        final int rootCharacteristics;

        final Spliterator<T> s;

        SplitNode(Consumer<T> c, Spliterator<T> s) {
            this(c, s.characteristics(), s);
        }

        private SplitNode(Consumer<T> c, int rootCharacteristics, Spliterator<T> s) {
            this.c = c;
            this.rootCharacteristics = rootCharacteristics;
            this.s = s;
        }

        SplitNode<T> fromSplit(Spliterator<T> split) {
            return new SplitNode<>(c, rootCharacteristics, split);
        }
    }

    /**
     * Set the maximum stack capacity to 0.25MB. This should be more than enough to detect a bad spliterator
     * while not unduly disrupting test infrastructure given the test data sizes that are used are small.
     * Note that j.u.c.ForkJoinPool sets the max queue size to 64M (1 << 26).
     */
    private static final int MAXIMUM_STACK_CAPACITY = 1 << 18; // 0.25MB

    private static <T> void testSplitUntilNull(SplitNode<T> e) {
        // Use an explicit stack to avoid a StackOverflowException when testing a Spliterator
        // that when repeatedly split produces a right-balanced (and maybe degenerate) tree, or
        // for a spliterator that is badly behaved.
        Deque<SplitNode<T>> stack = new ArrayDeque<>();
        stack.push(e);

        int iteration = 0;
        while (!stack.isEmpty()) {
            assertTrue(iteration++ < MAXIMUM_STACK_CAPACITY, "Exceeded maximum stack modification count of 1 << 18");

            e = stack.pop();
            Spliterator<T> parentAndRightSplit = e.s;

            long parentEstimateSize = parentAndRightSplit.estimateSize();
            assertTrue(parentEstimateSize >= 0,
                       String.format("Split size estimate %d < 0", parentEstimateSize));

            long parentSize = parentAndRightSplit.getExactSizeIfKnown();
            Spliterator<T> leftSplit = parentAndRightSplit.trySplit();
            if (leftSplit == null) {
                parentAndRightSplit.forEachRemaining(e.c);
                continue;
            }

            assertSpliterator(leftSplit, e.rootCharacteristics);
            assertSpliterator(parentAndRightSplit, e.rootCharacteristics);

            if (parentEstimateSize != Long.MAX_VALUE && leftSplit.estimateSize() > 0 && parentAndRightSplit.estimateSize() > 0) {
                assertTrue(leftSplit.estimateSize() < parentEstimateSize,
                           String.format("Left split size estimate %d >= parent split size estimate %d", leftSplit.estimateSize(), parentEstimateSize));
                assertTrue(parentAndRightSplit.estimateSize() < parentEstimateSize,
                           String.format("Right split size estimate %d >= parent split size estimate %d", leftSplit.estimateSize(), parentEstimateSize));
            }
            else {
                assertTrue(leftSplit.estimateSize() <= parentEstimateSize,
                           String.format("Left split size estimate %d > parent split size estimate %d", leftSplit.estimateSize(), parentEstimateSize));
                assertTrue(parentAndRightSplit.estimateSize() <= parentEstimateSize,
                           String.format("Right split size estimate %d > parent split size estimate %d", leftSplit.estimateSize(), parentEstimateSize));
            }

            long leftSize = leftSplit.getExactSizeIfKnown();
            long rightSize = parentAndRightSplit.getExactSizeIfKnown();
            if (parentSize >= 0 && leftSize >= 0 && rightSize >= 0)
                assertEquals(parentSize, leftSize + rightSize,
                             String.format("exact left split size %d + exact right split size %d != parent exact split size %d",
                                           leftSize, rightSize, parentSize));

            // Add right side to stack first so left side is popped off first
            stack.push(e.fromSplit(parentAndRightSplit));
            stack.push(e.fromSplit(leftSplit));
        }
    }

    private static void assertSpliterator(Spliterator<?> s, int rootCharacteristics) {
        if ((rootCharacteristics & Spliterator.SUBSIZED) != 0) {
            assertTrue(s.hasCharacteristics(Spliterator.SUBSIZED),
                       "Child split is not SUBSIZED when root split is SUBSIZED");
        }
        assertSpliterator(s);
    }

    private static void assertSpliterator(Spliterator<?> s) {
        if (s.hasCharacteristics(Spliterator.SUBSIZED)) {
            assertTrue(s.hasCharacteristics(Spliterator.SIZED));
        }
        if (s.hasCharacteristics(Spliterator.SIZED)) {
            assertTrue(s.estimateSize() != Long.MAX_VALUE);
            assertTrue(s.getExactSizeIfKnown() >= 0);
        }
        try {
            s.getComparator();
            assertTrue(s.hasCharacteristics(Spliterator.SORTED));
        } catch (IllegalStateException e) {
            assertFalse(s.hasCharacteristics(Spliterator.SORTED));
        }
    }

    private static<T> void assertContents(Collection<T> actual, Collection<T> expected, boolean isOrdered) {
        if (isOrdered) {
            assertEquals(actual, expected);
        }
        else {
            assertContentsUnordered(actual, expected);
        }
    }

    private static<T> void assertContentsUnordered(Iterable<T> actual, Iterable<T> expected) {
        assertEquals(toBoxedMultiset(actual), toBoxedMultiset(expected));
    }

    private static <T> Map<T, HashableInteger> toBoxedMultiset(Iterable<T> c) {
        Map<T, HashableInteger> result = new HashMap<>();
        c.forEach(e -> {
            if (result.containsKey(e)) {
                result.put(e, new HashableInteger(result.get(e).value + 1, 10));
            } else {
                result.put(e, new HashableInteger(1, 10));
            }
        });
        return result;
    }

    private void executeAndCatch(Class<? extends Exception> expected, Runnable r) {
        Exception caught = null;
        try {
            r.run();
        }
        catch (Exception e) {
            caught = e;
        }

        assertNotNull(caught,
                      String.format("No Exception was thrown, expected an Exception of %s to be thrown",
                                    expected.getName()));
        assertTrue(expected.isInstance(caught),
                   String.format("Exception thrown %s not an instance of %s",
                                 caught.getClass().getName(), expected.getName()));
    }

}
