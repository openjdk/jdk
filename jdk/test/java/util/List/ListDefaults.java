/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedList;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.lang.reflect.Constructor;
import java.util.ConcurrentModificationException;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @test
 * @summary Unit tests for extension methods on List
 * @bug 8023367
 * @library ../Collection/testlibrary
 * @build CollectionAsserts CollectionSupplier ExtendsAbstractList
 * @run testng ListDefaults
 */
public class ListDefaults {

    private static final Supplier<?>[] LIST_CLASSES = {
        java.util.ArrayList::new,
        java.util.LinkedList::new,
        java.util.Vector::new,
        java.util.concurrent.CopyOnWriteArrayList::new,
        ExtendsAbstractList::new
     };

    private static final Supplier<?>[] LIST_CME_CLASSES = {
        java.util.ArrayList::new,
        java.util.Vector::new
     };

    private static final Predicate<Integer> pEven = x -> 0 == x % 2;
    private static final Predicate<Integer> pOdd = x -> 1 == x % 2;

    private static final Comparator<Integer> BIT_COUNT_COMPARATOR =
            (x, y) -> Integer.bitCount(x) - Integer.bitCount(y);

    private static final Comparator<AtomicInteger> ATOMIC_INTEGER_COMPARATOR =
            (x, y) -> x.intValue() - y.intValue();

    private static final int SIZE = 100;
    private static final int SUBLIST_FROM = 20;
    private static final int SUBLIST_TO = SIZE - 5;
    private static final int SUBLIST_SIZE = SUBLIST_TO - SUBLIST_FROM;

    private static interface Callback {
        void call(List<Integer> list);
    }

    // call the callback for each recursive subList
    private void trimmedSubList(final List<Integer> list, final Callback callback) {
        int size = list.size();
        if (size > 1) {
            // trim 1 element from both ends
            final List<Integer> subList = list.subList(1, size - 1);
            callback.call(subList);
            trimmedSubList(subList, callback);
        }
    }

    @DataProvider(name="listProvider", parallel=true)
    public static Object[][] listCases() {
        final List<Object[]> cases = new LinkedList<>();
        cases.add(new Object[] { Collections.emptyList() });
        cases.add(new Object[] { new ArrayList<>() });
        cases.add(new Object[] { new LinkedList<>() });
        cases.add(new Object[] { new Vector<>() });
        cases.add(new Object[] { new Stack<>() });
        cases.add(new Object[] { new CopyOnWriteArrayList<>() });

        cases.add(new Object[] { new ArrayList(){{add(42);}} });
        cases.add(new Object[] { new LinkedList(){{add(42);}} });
        cases.add(new Object[] { new Vector(){{add(42);}} });
        cases.add(new Object[] { new Stack(){{add(42);}} });
        cases.add(new Object[] { new CopyOnWriteArrayList(){{add(42);}} });
        return cases.toArray(new Object[0][cases.size()]);
    }

    @Test(dataProvider = "listProvider")
    public void testProvidedWithNull(final List<Integer> list) throws Exception {
        try {
            list.forEach(null);
            fail("expected NPE not thrown");
        } catch (NullPointerException npe) {}
        try {
            list.replaceAll(null);
            fail("expected NPE not thrown");
        } catch (NullPointerException npe) {}
        try {
            list.removeIf(null);
            fail("expected NPE not thrown");
        } catch (NullPointerException npe) {}
        try {
            list.sort(null);
        } catch (Throwable t) {
            fail("Exception not expected: " + t);
        }
    }

    @Test
    public void testForEach() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);

            try {
                list.forEach(null);
                fail("expected NPE not thrown");
            } catch (NullPointerException npe) {}
            CollectionAsserts.assertContents(list, original);

            final List<Integer> actual = new LinkedList<>();
            list.forEach(actual::add);
            CollectionAsserts.assertContents(actual, list);
            CollectionAsserts.assertContents(actual, original);

            if (original.size() > SUBLIST_SIZE) {
                final List<Integer> subList = original.subList(SUBLIST_FROM, SUBLIST_TO);
                final List<Integer> actualSubList = new LinkedList<>();
                subList.forEach(actualSubList::add);
                assertEquals(actualSubList.size(), SUBLIST_SIZE);
                for (int i = 0; i < SUBLIST_SIZE; i++) {
                    assertEquals(actualSubList.get(i), original.get(i + SUBLIST_FROM));
                }
            }

            trimmedSubList(list, new Callback() {
                @Override
                public void call(final List<Integer> list) {
                    final List<Integer> actual = new LinkedList<>();
                    list.forEach(actual::add);
                    CollectionAsserts.assertContents(actual, list);
                }
            });
        }
    }

    @Test
    public void testRemoveIf() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);

            try {
                list.removeIf(null);
                fail("expected NPE not thrown");
            } catch (NullPointerException npe) {}
            CollectionAsserts.assertContents(list, original);

            final AtomicInteger offset = new AtomicInteger(1);
            while (list.size() > 0) {
                removeFirst(original, list, offset);
            }
        }

        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);
            list.removeIf(pOdd);
            for (int i : list) {
                assertTrue((i % 2) == 0);
            }
            for (int i : original) {
                if (i % 2 == 0) {
                    assertTrue(list.contains(i));
                }
            }
            list.removeIf(pEven);
            assertTrue(list.isEmpty());
        }

        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);
            final List<Integer> listCopy = new ArrayList<>(list);
            if (original.size() > SUBLIST_SIZE) {
                final List<Integer> subList = list.subList(SUBLIST_FROM, SUBLIST_TO);
                final List<Integer> subListCopy = new ArrayList<>(subList);
                listCopy.removeAll(subList);
                subList.removeIf(pOdd);
                for (int i : subList) {
                    assertTrue((i % 2) == 0);
                }
                for (int i : subListCopy) {
                    if (i % 2 == 0) {
                        assertTrue(subList.contains(i));
                    } else {
                        assertFalse(subList.contains(i));
                    }
                }
                subList.removeIf(pEven);
                assertTrue(subList.isEmpty());
                // elements outside the view should remain
                CollectionAsserts.assertContents(list, listCopy);
            }
        }

        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);
            trimmedSubList(list, new Callback() {
                @Override
                public void call(final List<Integer> list) {
                    final List<Integer> copy = new ArrayList<>(list);
                    list.removeIf(pOdd);
                    for (int i : list) {
                        assertTrue((i % 2) == 0);
                    }
                    for (int i : copy) {
                        if (i % 2 == 0) {
                            assertTrue(list.contains(i));
                        } else {
                            assertFalse(list.contains(i));
                        }
                    }
                }
            });
        }
    }

    // remove the first element
    private void removeFirst(final List<Integer> original, final List<Integer> list, final AtomicInteger offset) {
        final AtomicBoolean first = new AtomicBoolean(true);
        list.removeIf(x -> first.getAndSet(false));
        CollectionAsserts.assertContents(original.subList(offset.getAndIncrement(), original.size()), list);
    }

    @Test
    public void testReplaceAll() throws Exception {
        final int scale = 3;
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);

            try {
                list.replaceAll(null);
                fail("expected NPE not thrown");
            } catch (NullPointerException npe) {}
            CollectionAsserts.assertContents(list, original);

            list.replaceAll(x -> scale * x);
            for (int i=0; i < original.size(); i++) {
                assertTrue(list.get(i) == (scale * original.get(i)), "mismatch at index " + i);
            }

            if (original.size() > SUBLIST_SIZE) {
                final List<Integer> subList = list.subList(SUBLIST_FROM, SUBLIST_TO);
                subList.replaceAll(x -> x + 1);
                // verify elements in view [from, to) were replaced
                for (int i = 0; i < SUBLIST_SIZE; i++) {
                    assertTrue(subList.get(i) == ((scale * original.get(i + SUBLIST_FROM)) + 1),
                            "mismatch at sublist index " + i);
                }
                // verify that elements [0, from) remain unmodified
                for (int i = 0; i < SUBLIST_FROM; i++) {
                    assertTrue(list.get(i) == (scale * original.get(i)),
                            "mismatch at original index " + i);
                }
                // verify that elements [to, size) remain unmodified
                for (int i = SUBLIST_TO; i < list.size(); i++) {
                    assertTrue(list.get(i) == (scale * original.get(i)),
                            "mismatch at original index " + i);
                }
            }
        }

        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);
            trimmedSubList(list, new Callback() {
                @Override
                public void call(final List<Integer> list) {
                    final List<Integer> copy = new ArrayList<>(list);
                    final int offset = 5;
                    list.replaceAll(x -> offset + x);
                    for (int i=0; i < copy.size(); i++) {
                        assertTrue(list.get(i) == (offset + copy.get(i)), "mismatch at index " + i);
                    }
                }
            });
        }
    }

    @Test
    public void testSort() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);
            CollectionSupplier.shuffle(list);
            list.sort(Integer::compare);
            CollectionAsserts.assertSorted(list, Integer::compare);
            if (test.name.startsWith("reverse")) {
                Collections.reverse(list);
            }
            CollectionAsserts.assertContents(list, original);

            CollectionSupplier.shuffle(list);
            list.sort(null);
            CollectionAsserts.assertSorted(list, Comparator.<Integer>naturalOrder());
            if (test.name.startsWith("reverse")) {
                Collections.reverse(list);
            }
            CollectionAsserts.assertContents(list, original);

            CollectionSupplier.shuffle(list);
            list.sort(Comparator.<Integer>naturalOrder());
            CollectionAsserts.assertSorted(list, Comparator.<Integer>naturalOrder());
            if (test.name.startsWith("reverse")) {
                Collections.reverse(list);
            }
            CollectionAsserts.assertContents(list, original);

            CollectionSupplier.shuffle(list);
            list.sort(Comparator.<Integer>reverseOrder());
            CollectionAsserts.assertSorted(list, Comparator.<Integer>reverseOrder());
            if (!test.name.startsWith("reverse")) {
                Collections.reverse(list);
            }
            CollectionAsserts.assertContents(list, original);

            CollectionSupplier.shuffle(list);
            list.sort(BIT_COUNT_COMPARATOR);
            CollectionAsserts.assertSorted(list, BIT_COUNT_COMPARATOR);
            // check sort by verifying that bitCount increases and never drops
            int minBitCount = 0;
            int bitCount = 0;
            for (final Integer i : list) {
                bitCount = Integer.bitCount(i);
                assertTrue(bitCount >= minBitCount);
                minBitCount = bitCount;
            }

            @SuppressWarnings("unchecked")
            final Constructor<? extends List<?>> defaultConstructor = ((Class<? extends List<?>>)test.collection.getClass()).getConstructor();
            final List<AtomicInteger> incomparables = (List<AtomicInteger>) defaultConstructor.newInstance();

            for (int i=0; i < test.expected.size(); i++) {
                incomparables.add(new AtomicInteger(i));
            }
            CollectionSupplier.shuffle(incomparables);
            incomparables.sort(ATOMIC_INTEGER_COMPARATOR);
            for (int i=0; i < test.expected.size(); i++) {
                assertEquals(i, incomparables.get(i).intValue());
            }

            if (original.size() > SUBLIST_SIZE) {
                final List<Integer> copy = new ArrayList<>(list);
                final List<Integer> subList = list.subList(SUBLIST_FROM, SUBLIST_TO);
                CollectionSupplier.shuffle(subList);
                subList.sort(Comparator.<Integer>naturalOrder());
                CollectionAsserts.assertSorted(subList, Comparator.<Integer>naturalOrder());
                // verify that elements [0, from) remain unmodified
                for (int i = 0; i < SUBLIST_FROM; i++) {
                    assertTrue(list.get(i) == copy.get(i),
                            "mismatch at index " + i);
                }
                // verify that elements [to, size) remain unmodified
                for (int i = SUBLIST_TO; i < list.size(); i++) {
                    assertTrue(list.get(i) == copy.get(i),
                            "mismatch at index " + i);
                }
            }
        }

        for (final CollectionSupplier.TestCase test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);
            trimmedSubList(list, new Callback() {
                @Override
                public void call(final List<Integer> list) {
                    final List<Integer> copy = new ArrayList<>(list);
                    CollectionSupplier.shuffle(list);
                    list.sort(Comparator.<Integer>naturalOrder());
                    CollectionAsserts.assertSorted(list, Comparator.<Integer>naturalOrder());
                }
            });
        }
    }

    @Test
    public void testForEachThrowsCME() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CME_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);

            if (list.size() <= 1) {
                continue;
            }
            boolean gotException = false;
            try {
                // bad predicate that modifies its list, should throw CME
                list.forEach((x) -> {list.add(x);});
            } catch (ConcurrentModificationException cme) {
                gotException = true;
            }
            if (!gotException) {
                fail("expected CME was not thrown from " + test);
            }
        }
    }

    @Test
    public void testRemoveIfThrowsCME() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CME_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> original = ((List<Integer>) test.expected);
            final List<Integer> list = ((List<Integer>) test.collection);

            if (list.size() <= 1) {
                continue;
            }
            boolean gotException = false;
            try {
                // bad predicate that modifies its list, should throw CME
                list.removeIf((x) -> {return list.add(x);});
            } catch (ConcurrentModificationException cme) {
                gotException = true;
            }
            if (!gotException) {
                fail("expected CME was not thrown from " + test);
            }
        }
    }

    @Test
    public void testReplaceAllThrowsCME() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CME_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);

            if (list.size() <= 1) {
                continue;
            }
            boolean gotException = false;
            try {
                // bad predicate that modifies its list, should throw CME
                list.replaceAll(x -> {int n = 3 * x; list.add(n); return n;});
            } catch (ConcurrentModificationException cme) {
                gotException = true;
            }
            if (!gotException) {
                fail("expected CME was not thrown from " + test);
            }
        }
    }

    @Test
    public void testSortThrowsCME() throws Exception {
        final CollectionSupplier<List<Integer>> supplier = new CollectionSupplier((Supplier<List<Integer>>[])LIST_CME_CLASSES, SIZE);
        for (final CollectionSupplier.TestCase<List<Integer>> test : supplier.get()) {
            final List<Integer> list = ((List<Integer>) test.collection);

            if (list.size() <= 1) {
                continue;
            }
            boolean gotException = false;
            try {
                // bad predicate that modifies its list, should throw CME
                list.sort((x, y) -> {list.add(x); return x - y;});
            } catch (ConcurrentModificationException cme) {
                gotException = true;
            }
            if (!gotException) {
                fail("expected CME was not thrown from " + test);
            }
        }
    }

    private static final List<Integer> SLICED_EXPECTED = Arrays.asList(0, 1, 2, 3, 5, 6, 7, 8, 9);
    private static final List<Integer> SLICED_EXPECTED2 = Arrays.asList(0, 1, 2, 5, 6, 7, 8, 9);

    @DataProvider(name="shortIntListProvider", parallel=true)
    public static Object[][] intListCases() {
        final Integer[] DATA = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        final List<Object[]> cases = new LinkedList<>();
        cases.add(new Object[] { new ArrayList<>(Arrays.asList(DATA)) });
        cases.add(new Object[] { new LinkedList<>(Arrays.asList(DATA)) });
        cases.add(new Object[] { new Vector<>(Arrays.asList(DATA)) });
        cases.add(new Object[] { new CopyOnWriteArrayList<>(Arrays.asList(DATA)) });
        cases.add(new Object[] { new ExtendsAbstractList<>(Arrays.asList(DATA)) });
        return cases.toArray(new Object[0][cases.size()]);
    }

    @Test(dataProvider = "shortIntListProvider")
    public void testRemoveIfFromSlice(final List<Integer> list) throws Exception {
        final List<Integer> sublist = list.subList(3, 6);
        assertTrue(sublist.removeIf(x -> x == 4));
        CollectionAsserts.assertContents(list, SLICED_EXPECTED);

        final List<Integer> sublist2 = list.subList(2, 5);
        assertTrue(sublist2.removeIf(x -> x == 3));
        CollectionAsserts.assertContents(list, SLICED_EXPECTED2);
    }
}
