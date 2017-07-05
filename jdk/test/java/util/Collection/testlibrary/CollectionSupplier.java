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

import java.lang.Exception;
import java.lang.Integer;
import java.lang.Iterable;
import java.lang.Override;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.testng.TestException;

import static org.testng.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

/**
 * @library
 * @summary A Supplier of test cases for Collection tests
 */
public final class CollectionSupplier implements Supplier<Iterable<CollectionSupplier.TestCase>> {

    private final String[] classNames;
    private final int size;

    /**
     * A Collection test case.
     */
    public static final class TestCase {

        /**
         * The name of the test case.
         */
        public final String name;

        /**
         * Class name of the instantiated Collection.
         */
        public final String className;

        /**
         * Unmodifiable reference collection, useful for comparisons.
         */
        public final Collection<Integer> original;

        /**
         * A modifiable test collection.
         */
        public final Collection<Integer> collection;

        /**
         * Create a Collection test case.
         * @param name name of the test case
         * @param className class name of the instantiated collection
         * @param original reference collection
         * @param collection the modifiable test collection
         */
        public TestCase(String name, String className,
                Collection<Integer> original, Collection<Integer> collection) {
            this.name = name;
            this.className = className;
            this.original =
                    List.class.isAssignableFrom(original.getClass()) ?
                    Collections.unmodifiableList((List<Integer>) original) :
                    Set.class.isAssignableFrom(original.getClass()) ?
                    Collections.unmodifiableSet((Set<Integer>) original) :
                    Collections.unmodifiableCollection(original);
            this.collection = collection;
        }

        @Override
        public String toString() {
            return name + " " + className +
                    "\n original: " + original +
                    "\n   target: " + collection;
        }
    }

    /**
     * Shuffle a list using a PRNG with known seed for repeatability
     * @param list the list to be shuffled
     */
    public static <E> void shuffle(final List<E> list) {
        // PRNG with known seed for repeatable tests
        final Random prng = new Random(13);
        final int size = list.size();
        for (int i=0; i < size; i++) {
            // random index in interval [i, size)
            final int j = i + prng.nextInt(size - i);
            // swap elements at indices i & j
            final E e = list.get(i);
            list.set(i, list.get(j));
            list.set(j, e);
        }
    }

    /**
     * Create a {@code Supplier} that creates instances of specified collection
     * classes of specified length.
     *
     * @param classNames class names that implement {@code Collection}
     * @param size the desired size of each collection
     */
    public CollectionSupplier(String[] classNames, int size) {
        this.classNames = Arrays.copyOf(classNames, classNames.length);
        this.size = size;
    }

    @Override
    public Iterable<TestCase> get() {
        try {
            return getThrows();
        } catch (Exception e) {
            throw new TestException(e);
        }
    }

    private Iterable<TestCase> getThrows() throws Exception {
        final Collection<TestCase> collections = new LinkedList<>();
        for (final String className : classNames) {
            @SuppressWarnings("unchecked")
            final Class<? extends Collection<Integer>> type =
                    (Class<? extends Collection<Integer>>) Class.forName(className);
            final Constructor<? extends Collection<Integer>>
                    defaultConstructor = type.getConstructor();
            final Constructor<? extends Collection<Integer>>
                    copyConstructor = type.getConstructor(Collection.class);

            final Collection<Integer> empty = defaultConstructor.newInstance();
            collections.add(new TestCase("empty",
                    className,
                    copyConstructor.newInstance(empty),
                    empty));

            final Collection<Integer> single = defaultConstructor.newInstance();
            single.add(42);
            collections.add(new TestCase("single",
                    className,
                    copyConstructor.newInstance(single),
                    single));

            final Collection<Integer> regular = defaultConstructor.newInstance();
            for (int i=0; i < size; i++) {
                regular.add(i);
            }
            collections.add(new TestCase("regular",
                    className,
                    copyConstructor.newInstance(regular),
                    regular));

            final Collection<Integer> reverse = defaultConstructor.newInstance();
            for (int i=size; i >= 0; i--) {
                reverse.add(i);
            }
            collections.add(new TestCase("reverse",
                    className,
                    copyConstructor.newInstance(reverse),
                    reverse));

            final Collection<Integer> odds = defaultConstructor.newInstance();
            for (int i=0; i < size; i++) {
                odds.add((i * 2) + 1);
            }
            collections.add(new TestCase("odds",
                    className,
                    copyConstructor.newInstance(odds),
                    odds));

            final Collection<Integer> evens = defaultConstructor.newInstance();
            for (int i=0; i < size; i++) {
                evens.add(i * 2);
            }
            collections.add(new TestCase("evens",
                    className,
                    copyConstructor.newInstance(evens),
                    evens));

            final Collection<Integer> fibonacci = defaultConstructor.newInstance();
            int prev2 = 0;
            int prev1 = 1;
            for (int i=0; i < size; i++) {
                final int n = prev1 + prev2;
                if (n < 0) { // stop on overflow
                    break;
                }
                fibonacci.add(n);
                prev2 = prev1;
                prev1 = n;
            }
            collections.add(new TestCase("fibonacci",
                    className,
                    copyConstructor.newInstance(fibonacci),
                    fibonacci));

            // variants where the size of the backing storage != reported size
            // created by removing half of the elements

            final Collection<Integer> emptyWithSlack = defaultConstructor.newInstance();
            emptyWithSlack.add(42);
            assertTrue(emptyWithSlack.remove(42));
            collections.add(new TestCase("emptyWithSlack",
                    className,
                    copyConstructor.newInstance(emptyWithSlack),
                    emptyWithSlack));

            final Collection<Integer> singleWithSlack = defaultConstructor.newInstance();
            singleWithSlack.add(42);
            singleWithSlack.add(43);
            assertTrue(singleWithSlack.remove(43));
            collections.add(new TestCase("singleWithSlack",
                    className,
                    copyConstructor.newInstance(singleWithSlack),
                    singleWithSlack));

            final Collection<Integer> regularWithSlack = defaultConstructor.newInstance();
            for (int i=0; i < (2 * size); i++) {
                regularWithSlack.add(i);
            }
            assertTrue(regularWithSlack.removeIf((x) -> {return x >= size;}));
            collections.add(new TestCase("regularWithSlack",
                    className,
                    copyConstructor.newInstance(regularWithSlack),
                    regularWithSlack));

            final Collection<Integer> reverseWithSlack = defaultConstructor.newInstance();
            for (int i=2 * size; i >= 0; i--) {
                reverseWithSlack.add(i);
            }
            assertTrue(reverseWithSlack.removeIf((x) -> {return x < size;}));
            collections.add(new TestCase("reverseWithSlack",
                    className,
                    copyConstructor.newInstance(reverseWithSlack),
                    reverseWithSlack));

            final Collection<Integer> oddsWithSlack = defaultConstructor.newInstance();
            for (int i = 0; i < 2 * size; i++) {
                oddsWithSlack.add((i * 2) + 1);
            }
            assertTrue(oddsWithSlack.removeIf((x) -> {return x >= size;}));
            collections.add(new TestCase("oddsWithSlack",
                    className,
                    copyConstructor.newInstance(oddsWithSlack),
                    oddsWithSlack));

            final Collection<Integer> evensWithSlack = defaultConstructor.newInstance();
            for (int i = 0; i < 2 * size; i++) {
                evensWithSlack.add(i * 2);
            }
            assertTrue(evensWithSlack.removeIf((x) -> {return x >= size;}));
            collections.add(new TestCase("evensWithSlack",
                    className,
                    copyConstructor.newInstance(evensWithSlack),
                    evensWithSlack));

            final Collection<Integer> fibonacciWithSlack = defaultConstructor.newInstance();
            prev2 = 0;
            prev1 = 1;
            for (int i=0; i < size; i++) {
                final int n = prev1 + prev2;
                if (n < 0) { // stop on overflow
                    break;
                }
                fibonacciWithSlack.add(n);
                prev2 = prev1;
                prev1 = n;
            }
            assertTrue(fibonacciWithSlack.removeIf((x) -> {return x < 20;}));
            collections.add(new TestCase("fibonacciWithSlack",
                    className,
                    copyConstructor.newInstance(fibonacciWithSlack),
                    fibonacciWithSlack));

        }

        return collections;
    }

}
