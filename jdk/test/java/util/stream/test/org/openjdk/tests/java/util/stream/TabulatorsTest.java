/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.tests.java.util.stream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.LambdaTestHelpers;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamOpFlagTestHelper;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import org.testng.annotations.Test;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.groupingByConcurrent;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;
import static java.util.stream.LambdaTestHelpers.assertContents;
import static java.util.stream.LambdaTestHelpers.assertContentsUnordered;
import static java.util.stream.LambdaTestHelpers.mDoubler;

/**
 * TabulatorsTest
 *
 * @author Brian Goetz
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class TabulatorsTest extends OpTestCase {
    // There are 8 versions of groupingBy:
    //   groupingBy: { map supplier, not } x { downstream collector, not } x { concurrent, not }
    // There are 2 versions of partition: { map supplier, not }
    // There are 4 versions of toMap
    //   mappedTo(function, mapSupplier?, mergeFunction?)
    // Each variety needs at least one test
    // Plus a variety of multi-level tests (groupBy(..., partition), partition(..., groupBy))
    // Plus negative tests for mapping to null
    // Each test should be matched by a nest of asserters (see TabulationAssertion...)


    private static abstract class TabulationAssertion<T, U> {
        abstract void assertValue(U value,
                                  Supplier<Stream<T>> source,
                                  boolean ordered) throws ReflectiveOperationException;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class GroupedMapAssertion<T, K, V, M extends Map<K, ? extends V>> extends TabulationAssertion<T, M> {
        private final Class<? extends Map> clazz;
        private final Function<T, K> classifier;
        private final TabulationAssertion<T,V> downstream;

        protected GroupedMapAssertion(Function<T, K> classifier,
                                      Class<? extends Map> clazz,
                                      TabulationAssertion<T, V> downstream) {
            this.clazz = clazz;
            this.classifier = classifier;
            this.downstream = downstream;
        }

        void assertValue(M map,
                         Supplier<Stream<T>> source,
                         boolean ordered) throws ReflectiveOperationException {
            if (!clazz.isAssignableFrom(map.getClass()))
                fail(String.format("Class mismatch in GroupedMapAssertion: %s, %s", clazz, map.getClass()));
            assertContentsUnordered(map.keySet(), source.get().map(classifier).collect(Collectors.toSet()));
            for (Map.Entry<K, ? extends V> entry : map.entrySet()) {
                K key = entry.getKey();
                downstream.assertValue(entry.getValue(),
                                       () -> source.get().filter(e -> classifier.apply(e).equals(key)),
                                       ordered);
            }
        }
    }

    static class PartitionAssertion<T, D> extends TabulationAssertion<T, Map<Boolean,D>> {
        private final Predicate<T> predicate;
        private final TabulationAssertion<T,D> downstream;

        protected PartitionAssertion(Predicate<T> predicate,
                                     TabulationAssertion<T, D> downstream) {
            this.predicate = predicate;
            this.downstream = downstream;
        }

        void assertValue(Map<Boolean, D> map,
                         Supplier<Stream<T>> source,
                         boolean ordered) throws ReflectiveOperationException {
            if (!Map.class.isAssignableFrom(map.getClass()))
                fail(String.format("Class mismatch in PartitionAssertion: %s", map.getClass()));
            assertEquals(2, map.size());
            downstream.assertValue(map.get(true), () -> source.get().filter(predicate), ordered);
            downstream.assertValue(map.get(false), () -> source.get().filter(predicate.negate()), ordered);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class ListAssertion<T> extends TabulationAssertion<T, List<T>> {
        @Override
        void assertValue(List<T> value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            if (!List.class.isAssignableFrom(value.getClass()))
                fail(String.format("Class mismatch in ListAssertion: %s", value.getClass()));
            Stream<T> stream = source.get();
            List<T> result = new ArrayList<>();
            for (Iterator<T> it = stream.iterator(); it.hasNext(); ) // avoid capturing result::add
                result.add(it.next());
            if (StreamOpFlagTestHelper.isStreamOrdered(stream) && ordered)
                assertContents(value, result);
            else
                assertContentsUnordered(value, result);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static class CollectionAssertion<T> extends TabulationAssertion<T, Collection<T>> {
        private final Class<? extends Collection> clazz;
        private final boolean targetOrdered;

        protected CollectionAssertion(Class<? extends Collection> clazz, boolean targetOrdered) {
            this.clazz = clazz;
            this.targetOrdered = targetOrdered;
        }

        @Override
        void assertValue(Collection<T> value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            if (!clazz.isAssignableFrom(value.getClass()))
                fail(String.format("Class mismatch in CollectionAssertion: %s, %s", clazz, value.getClass()));
            Stream<T> stream = source.get();
            Collection<T> result = clazz.newInstance();
            for (Iterator<T> it = stream.iterator(); it.hasNext(); ) // avoid capturing result::add
                result.add(it.next());
            if (StreamOpFlagTestHelper.isStreamOrdered(stream) && targetOrdered && ordered)
                assertContents(value, result);
            else
                assertContentsUnordered(value, result);
        }
    }

    static class ReduceAssertion<T, U> extends TabulationAssertion<T, U> {
        private final U identity;
        private final Function<T, U> mapper;
        private final BinaryOperator<U> reducer;

        ReduceAssertion(U identity, Function<T, U> mapper, BinaryOperator<U> reducer) {
            this.identity = identity;
            this.mapper = mapper;
            this.reducer = reducer;
        }

        @Override
        void assertValue(U value, Supplier<Stream<T>> source, boolean ordered)
                throws ReflectiveOperationException {
            Optional<U> reduced = source.get().map(mapper).reduce(reducer);
            if (value == null)
                assertTrue(!reduced.isPresent());
            else if (!reduced.isPresent()) {
                assertEquals(value, identity);
            }
            else {
                assertEquals(value, reduced.get());
            }
        }
    }

    private <T> ResultAsserter<T> mapTabulationAsserter(boolean ordered) {
        return (act, exp, ord, par) -> {
            if (par & (!ordered || !ord)) {
                TabulatorsTest.nestedMapEqualityAssertion(act, exp);
            }
            else {
                LambdaTestHelpers.assertContentsEqual(act, exp);
            }
        };
    }

    private<T, M extends Map>
    void exerciseMapTabulation(TestData<T, Stream<T>> data,
                               Collector<T, ? extends M> collector,
                               TabulationAssertion<T, M> assertion)
            throws ReflectiveOperationException {
        boolean ordered = !collector.characteristics().contains(Collector.Characteristics.UNORDERED);

        M m = withData(data)
                .terminal(s -> s.collect(collector))
                .resultAsserter(mapTabulationAsserter(ordered))
                .exercise();
        assertion.assertValue(m, () -> data.stream(), ordered);

        m = withData(data)
                .terminal(s -> s.unordered().collect(collector))
                .resultAsserter(mapTabulationAsserter(ordered))
                .exercise();
        assertion.assertValue(m, () -> data.stream(), false);
    }

    private static void nestedMapEqualityAssertion(Object o1, Object o2) {
        if (o1 instanceof Map) {
            Map m1 = (Map) o1;
            Map m2 = (Map) o2;
            assertContentsUnordered(m1.keySet(), m2.keySet());
            for (Object k : m1.keySet())
                nestedMapEqualityAssertion(m1.get(k), m2.get(k));
        }
        else if (o1 instanceof Collection) {
            assertContentsUnordered(((Collection) o1), ((Collection) o2));
        }
        else
            assertEquals(o1, o2);
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSimpleGroupBy(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 3;

        // Single-level groupBy
        exerciseMapTabulation(data, groupingBy(classifier),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ListAssertion<>()));
        exerciseMapTabulation(data, groupingByConcurrent(classifier),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ListAssertion<>()));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, toCollection(HashSet::new)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new CollectionAssertion<Integer>(HashSet.class, false)));
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new,
                                                   toCollection(HashSet::new)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new CollectionAssertion<Integer>(HashSet.class, false)));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testTwoLevelGroupBy(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 6;
        Function<Integer, Integer> classifier2 = i -> i % 23;

        // Two-level groupBy
        exerciseMapTabulation(data,
                              groupingBy(classifier, groupingBy(classifier2)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, HashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, groupingBy(classifier2)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, HashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as downstream
        exerciseMapTabulation(data,
                              groupingBy(classifier, groupingByConcurrent(classifier2)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentHashMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream and downstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, groupingByConcurrent(classifier2)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentHashMap.class,
                                                                                  new ListAssertion<>())));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, groupingBy(classifier2, TreeMap::new, toCollection(HashSet::new))),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new GroupedMapAssertion<>(classifier2, TreeMap.class,
                                                                                  new CollectionAssertion<Integer>(HashSet.class, false))));
        // with concurrent as upstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, groupingBy(classifier2, TreeMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new GroupedMapAssertion<>(classifier2, TreeMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as downstream
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, groupingByConcurrent(classifier2, ConcurrentSkipListMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentSkipListMap.class,
                                                                                  new ListAssertion<>())));
        // with concurrent as upstream and downstream
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, groupingByConcurrent(classifier2, ConcurrentSkipListMap::new, toList())),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new GroupedMapAssertion<>(classifier2, ConcurrentSkipListMap.class,
                                                                                  new ListAssertion<>())));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testGroupedReduce(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Function<Integer, Integer> classifier = i -> i % 3;

        // Single-level simple reduce
        exerciseMapTabulation(data,
                              groupingBy(classifier, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, reducing(0, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));

        // Single-level map-reduce
        exerciseMapTabulation(data,
                              groupingBy(classifier, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, HashMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentHashMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));

        // With explicit constructors
        exerciseMapTabulation(data,
                              groupingBy(classifier, TreeMap::new, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, TreeMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
        // with concurrent
        exerciseMapTabulation(data,
                              groupingByConcurrent(classifier, ConcurrentSkipListMap::new, reducing(0, mDoubler, Integer::sum)),
                              new GroupedMapAssertion<>(classifier, ConcurrentSkipListMap.class,
                                                        new ReduceAssertion<>(0, mDoubler, Integer::sum)));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testSimplePartition(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Predicate<Integer> classifier = i -> i % 3 == 0;

        // Single-level partition to downstream List
        exerciseMapTabulation(data,
                              partitioningBy(classifier),
                              new PartitionAssertion<>(classifier, new ListAssertion<>()));
        exerciseMapTabulation(data,
                              partitioningBy(classifier, toList()),
                              new PartitionAssertion<>(classifier, new ListAssertion<>()));
    }

    @Test(dataProvider = "StreamTestData<Integer>", dataProviderClass = StreamTestDataProvider.class)
    public void testTwoLevelPartition(String name, TestData.OfRef<Integer> data) throws ReflectiveOperationException {
        Predicate<Integer> classifier = i -> i % 3 == 0;
        Predicate<Integer> classifier2 = i -> i % 7 == 0;

        // Two level partition
        exerciseMapTabulation(data,
                              partitioningBy(classifier, partitioningBy(classifier2)),
                              new PartitionAssertion<>(classifier,
                                                       new PartitionAssertion(classifier2, new ListAssertion<>())));

        // Two level partition with reduce
        exerciseMapTabulation(data,
                              partitioningBy(classifier, reducing(0, Integer::sum)),
                              new PartitionAssertion<>(classifier,
                                                       new ReduceAssertion<>(0, LambdaTestHelpers.identity(), Integer::sum)));
    }
}
