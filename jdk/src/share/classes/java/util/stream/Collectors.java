/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package java.util.stream;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.Iterator;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;

/**
 * Implementations of {@link Collector} that implement various useful reduction
 * operations, such as accumulating elements into collections, summarizing
 * elements according to various criteria, etc.
 *
 * <p>The following are examples of using the predefined {@code Collector}
 * implementations in {@link Collectors} with the {@code Stream} API to perform
 * mutable reduction tasks:
 *
 * <pre>{@code
 *     // Accumulate elements into a List
 *     List<Person> list = people.collect(Collectors.toList());
 *
 *     // Accumulate elements into a TreeSet
 *     List<Person> list = people.collect(Collectors.toCollection(TreeSet::new));
 *
 *     // Convert elements to strings and concatenate them, separated by commas
 *     String joined = stream.map(Object::toString)
 *                           .collect(Collectors.toStringJoiner(", "))
 *                           .toString();
 *
 *     // Find highest-paid employee
 *     Employee highestPaid = employees.stream()
 *                                     .collect(Collectors.maxBy(Comparator.comparing(Employee::getSalary)));
 *
 *     // Group employees by department
 *     Map<Department, List<Employee>> byDept
 *         = employees.stream()
 *                    .collect(Collectors.groupingBy(Employee::getDepartment));
 *
 *     // Find highest-paid employee by department
 *     Map<Department, Employee> highestPaidByDept
 *         = employees.stream()
 *                    .collect(Collectors.groupingBy(Employee::getDepartment,
 *                                                   Collectors.maxBy(Comparator.comparing(Employee::getSalary))));
 *
 *     // Partition students into passing and failing
 *     Map<Boolean, List<Student>> passingFailing =
 *         students.stream()
 *                 .collect(Collectors.partitioningBy(s -> s.getGrade() >= PASS_THRESHOLD);
 *
 * }</pre>
 *
 * TODO explanation of parallel collection
 *
 * @since 1.8
 */
public final class Collectors {

    private static final Set<Collector.Characteristics> CH_CONCURRENT
            = Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.CONCURRENT,
                                                     Collector.Characteristics.STRICTLY_MUTATIVE,
                                                     Collector.Characteristics.UNORDERED));
    private static final Set<Collector.Characteristics> CH_STRICT
            = Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.STRICTLY_MUTATIVE));
    private static final Set<Collector.Characteristics> CH_STRICT_UNORDERED
            = Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.STRICTLY_MUTATIVE,
                                                     Collector.Characteristics.UNORDERED));

    private Collectors() { }

    /**
     * Returns a merge function, suitable for use in
     * {@link Map#merge(Object, Object, BiFunction) Map.merge()} or
     * {@link #toMap(Function, Function, BinaryOperator) toMap()}, which always
     * throws {@code IllegalStateException}.  This can be used to enforce the
     * assumption that the elements being collected are distinct.
     *
     * @param <T> the type of input arguments to the merge function
     * @return a merge function which always throw {@code IllegalStateException}
     *
     * @see #firstWinsMerger()
     * @see #lastWinsMerger()
     */
    public static <T> BinaryOperator<T> throwingMerger() {
        return (u,v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); };
    }

    /**
     * Returns a merge function, suitable for use in
     * {@link Map#merge(Object, Object, BiFunction) Map.merge()} or
     * {@link #toMap(Function, Function, BinaryOperator) toMap()},
     * which implements a "first wins" policy.
     *
     * @param <T> the type of input arguments to the merge function
     * @return a merge function which always returns its first argument
     * @see #lastWinsMerger()
     * @see #throwingMerger()
     */
    public static <T> BinaryOperator<T> firstWinsMerger() {
        return (u,v) -> u;
    }

    /**
     * Returns a merge function, suitable for use in
     * {@link Map#merge(Object, Object, BiFunction) Map.merge()} or
     * {@link #toMap(Function, Function, BinaryOperator) toMap()},
     * which implements a "last wins" policy.
     *
     * @param <T> the type of input arguments to the merge function
     * @return a merge function which always returns its second argument
     * @see #firstWinsMerger()
     * @see #throwingMerger()
     */
    public static <T> BinaryOperator<T> lastWinsMerger() {
        return (u,v) -> v;
    }

    /**
     * Simple implementation class for {@code Collector}.
     *
     * @param <T> the type of elements to be collected
     * @param <R> the type of the result
     */
    private static final class CollectorImpl<T, R> implements Collector<T,R> {
        private final Supplier<R> resultSupplier;
        private final BiFunction<R, T, R> accumulator;
        private final BinaryOperator<R> combiner;
        private final Set<Characteristics> characteristics;

        CollectorImpl(Supplier<R> resultSupplier,
                      BiFunction<R, T, R> accumulator,
                      BinaryOperator<R> combiner,
                      Set<Characteristics> characteristics) {
            this.resultSupplier = resultSupplier;
            this.accumulator = accumulator;
            this.combiner = combiner;
            this.characteristics = characteristics;
        }

        CollectorImpl(Supplier<R> resultSupplier,
                      BiFunction<R, T, R> accumulator,
                      BinaryOperator<R> combiner) {
            this(resultSupplier, accumulator, combiner, Collections.emptySet());
        }

        @Override
        public BiFunction<R, T, R> accumulator() {
            return accumulator;
        }

        @Override
        public Supplier<R> resultSupplier() {
            return resultSupplier;
        }

        @Override
        public BinaryOperator<R> combiner() {
            return combiner;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return characteristics;
        }
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a
     * new {@code Collection}, in encounter order.  The {@code Collection} is
     * created by the provided factory.
     *
     * @param <T> the type of the input elements
     * @param <C> the type of the resulting {@code Collection}
     * @param collectionFactory a {@code Supplier} which returns a new, empty
     * {@code Collection} of the appropriate type
     * @return a {@code Collector} which collects all the input elements into a
     * {@code Collection}, in encounter order
     */
    public static <T, C extends Collection<T>>
    Collector<T, C> toCollection(Supplier<C> collectionFactory) {
        return new CollectorImpl<>(collectionFactory,
                                   (r, t) -> { r.add(t); return r; },
                                   (r1, r2) -> { r1.addAll(r2); return r1; },
                                   CH_STRICT);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a
     * new {@code List}. There are no guarantees on the type, mutability,
     * serializability, or thread-safety of the {@code List} returned.
     *
     * @param <T> the type of the input elements
     * @return a {@code Collector} which collects all the input elements into a
     * {@code List}, in encounter order
     */
    public static <T>
    Collector<T, List<T>> toList() {
        BiFunction<List<T>, T, List<T>> accumulator = (list, t) -> {
            switch (list.size()) {
                case 0:
                    return Collections.singletonList(t);
                case 1:
                    List<T> newList = new ArrayList<>();
                    newList.add(list.get(0));
                    newList.add(t);
                    return newList;
                default:
                    list.add(t);
                    return list;
            }
        };
        BinaryOperator<List<T>> combiner = (left, right) -> {
            switch (left.size()) {
                case 0:
                    return right;
                case 1:
                    List<T> newList = new ArrayList<>(left.size() + right.size());
                    newList.addAll(left);
                    newList.addAll(right);
                    return newList;
                default:
                    left.addAll(right);
                    return left;
            }
        };
        return new CollectorImpl<>(Collections::emptyList, accumulator, combiner);
    }

    /**
     * Returns a {@code Collector} that accumulates the input elements into a
     * new {@code Set}. There are no guarantees on the type, mutability,
     * serializability, or thread-safety of the {@code Set} returned.
     *
     * <p>This is an {@link Collector.Characteristics#UNORDERED unordered}
     * Collector.
     *
     * @param <T> the type of the input elements
     * @return a {@code Collector} which collects all the input elements into a
     * {@code Set}
     */
    public static <T>
    Collector<T, Set<T>> toSet() {
        return new CollectorImpl<>((Supplier<Set<T>>) HashSet::new,
                                   (r, t) -> { r.add(t); return r; },
                                   (r1, r2) -> { r1.addAll(r2); return r1; },
                                   CH_STRICT_UNORDERED);
    }

    /**
     * Returns a {@code Collector} that concatenates the input elements into a
     * new {@link StringBuilder}.
     *
     * @return a {@code Collector} which collects String elements into a
     * {@code StringBuilder}, in encounter order
     */
    public static Collector<String, StringBuilder> toStringBuilder() {
        return new CollectorImpl<>(StringBuilder::new,
                                   (r, t) -> { r.append(t); return r; },
                                   (r1, r2) -> { r1.append(r2); return r1; },
                                   CH_STRICT);
    }

    /**
     * Returns a {@code Collector} that concatenates the input elements into a
     * new {@link StringJoiner}, using the specified delimiter.
     *
     * @param delimiter the delimiter to be used between each element
     * @return A {@code Collector} which collects String elements into a
     * {@code StringJoiner}, in encounter order
     */
    public static Collector<CharSequence, StringJoiner> toStringJoiner(CharSequence delimiter) {
        BinaryOperator<StringJoiner> merger = (sj, other) -> {
            if (other.length() > 0)
                sj.add(other.toString());
            return sj;
        };
        return new CollectorImpl<>(() -> new StringJoiner(delimiter),
                                   (r, t) -> { r.add(t); return r; },
                                   merger, CH_STRICT);
    }

    /**
     * {@code BinaryOperator<Map>} that merges the contents of its right
     * argument into its left argument, using the provided merge function to
     * handle duplicate keys.
     *
     * @param <K> type of the map keys
     * @param <V> type of the map values
     * @param <M> type of the map
     * @param mergeFunction A merge function suitable for
     * {@link Map#merge(Object, Object, BiFunction) Map.merge()}
     * @return a merge function for two maps
     */
    private static <K, V, M extends Map<K,V>>
    BinaryOperator<M> mapMerger(BinaryOperator<V> mergeFunction) {
        return (m1, m2) -> {
            for (Map.Entry<K,V> e : m2.entrySet())
                m1.merge(e.getKey(), e.getValue(), mergeFunction);
            return m1;
        };
    }

    /**
     * Adapts a {@code Collector<U,R>} to a {@code Collector<T,R>} by applying
     * a mapping function to each input element before accumulation.
     *
     * @apiNote
     * The {@code mapping()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.  For example, given a stream of
     * {@code Person}, to accumulate the set of last names in each city:
     * <pre>{@code
     *     Map<City, Set<String>> lastNamesByCity
     *         = people.stream().collect(groupingBy(Person::getCity,
     *                                              mapping(Person::getLastName, toSet())));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <U> type of elements accepted by downstream collector
     * @param <R> result type of collector
     * @param mapper a function to be applied to the input elements
     * @param downstream a collector which will accept mapped values
     * @return a collector which applies the mapping function to the input
     * elements and provides the mapped results to the downstream collector
     */
    public static <T, U, R> Collector<T, R>
    mapping(Function<? super T, ? extends U> mapper, Collector<? super U, R> downstream) {
        BiFunction<R, ? super U, R> downstreamAccumulator = downstream.accumulator();
        return new CollectorImpl<>(downstream.resultSupplier(),
                                   (r, t) -> downstreamAccumulator.apply(r, mapper.apply(t)),
                                   downstream.combiner(), downstream.characteristics());
    }

    /**
     * Returns a {@code Collector<T, Long>} that counts the number of input
     * elements.
     *
     * @implSpec
     * This produces a result equivalent to:
     * <pre>{@code
     *     reducing(0L, e -> 1L, Long::sum)
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @return a {@code Collector} that counts the input elements
     */
    public static <T> Collector<T, Long>
    counting() {
        return reducing(0L, e -> 1L, Long::sum);
    }

    /**
     * Returns a {@code Collector<T, T>} that produces the minimal element
     * according to a given {@code Comparator}.
     *
     * @implSpec
     * This produces a result equivalent to:
     * <pre>{@code
     *     reducing(BinaryOperator.minBy(comparator))
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param comparator a {@code Comparator} for comparing elements
     * @return a {@code Collector} that produces the minimal value
     */
    public static <T> Collector<T, T>
    minBy(Comparator<? super T> comparator) {
        return reducing(BinaryOperator.minBy(comparator));
    }

    /**
     * Returns a {@code Collector<T, T>} that produces the maximal element
     * according to a given {@code Comparator}.
     *
     * @implSpec
     * This produces a result equivalent to:
     * <pre>{@code
     *     reducing(BinaryOperator.maxBy(comparator))
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param comparator a {@code Comparator} for comparing elements
     * @return a {@code Collector} that produces the maximal value
     */
    public static <T> Collector<T, T>
    maxBy(Comparator<? super T> comparator) {
        return reducing(BinaryOperator.maxBy(comparator));
    }

    /**
     * Returns a {@code Collector<T, Long>} that produces the sum of a
     * long-valued function applied to the input element.
     *
     * @implSpec
     * This produces a result equivalent to:
     * <pre>{@code
     *     reducing(0L, mapper, Long::sum)
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param mapper a function extracting the property to be summed
     * @return a {@code Collector} that produces the sum of a derived property
     */
    public static <T> Collector<T, Long>
    sumBy(Function<? super T, Long> mapper) {
        return reducing(0L, mapper, Long::sum);
    }

    /**
     * Returns a {@code Collector<T,T>} which performs a reduction of its
     * input elements under a specified {@code BinaryOperator}.
     *
     * @apiNote
     * The {@code reducing()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.  To perform a simple reduction on a stream,
     * use {@link Stream#reduce(BinaryOperator)} instead.
     *
     * @param <T> element type for the input and output of the reduction
     * @param identity the identity value for the reduction (also, the value
     *                 that is returned when there are no input elements)
     * @param op a {@code BinaryOperator<T>} used to reduce the input elements
     * @return a {@code Collector} which implements the reduction operation
     *
     * @see #reducing(BinaryOperator)
     * @see #reducing(Object, Function, BinaryOperator)
     */
    public static <T> Collector<T, T>
    reducing(T identity, BinaryOperator<T> op) {
        return new CollectorImpl<>(() -> identity, (r, t) -> (r == null ? t : op.apply(r, t)), op);
    }

    /**
     * Returns a {@code Collector<T,T>} which performs a reduction of its
     * input elements under a specified {@code BinaryOperator}.
     *
     * @apiNote
     * The {@code reducing()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.  To perform a simple reduction on a stream,
     * use {@link Stream#reduce(BinaryOperator)} instead.
     *
     * <p>For example, given a stream of {@code Person}, to calculate tallest
     * person in each city:
     * <pre>{@code
     *     Comparator<Person> byHeight = Comparator.comparing(Person::getHeight);
     *     BinaryOperator<Person> tallerOf = BinaryOperator.greaterOf(byHeight);
     *     Map<City, Person> tallestByCity
     *         = people.stream().collect(groupingBy(Person::getCity, reducing(tallerOf)));
     * }</pre>
     *
     * @implSpec
     * The default implementation is equivalent to:
     * <pre>{@code
     *     reducing(null, op);
     * }</pre>
     *
     * @param <T> element type for the input and output of the reduction
     * @param op a {@code BinaryOperator<T>} used to reduce the input elements
     * @return a {@code Collector} which implements the reduction operation
     *
     * @see #reducing(Object, BinaryOperator)
     * @see #reducing(Object, Function, BinaryOperator)
     */
    public static <T> Collector<T, T>
    reducing(BinaryOperator<T> op) {
        return reducing(null, op);
    }

    /**
     * Returns a {@code Collector<T,U>} which performs a reduction of its
     * input elements under a specified mapping function and
     * {@code BinaryOperator}. This is a generalization of
     * {@link #reducing(Object, BinaryOperator)} which allows a transformation
     * of the elements before reduction.
     *
     * @apiNote
     * The {@code reducing()} collectors are most useful when used in a
     * multi-level reduction, downstream of {@code groupingBy} or
     * {@code partitioningBy}.  To perform a simple reduction on a stream,
     * use {@link Stream#reduce(BinaryOperator)} instead.
     *
     * <p>For example, given a stream of {@code Person}, to calculate the longest
     * last name of residents in each city:
     * <pre>{@code
     *     Comparator<String> byLength = Comparator.comparing(String::length);
     *     BinaryOperator<String> longerOf = BinaryOperator.greaterOf(byLength);
     *     Map<City, String> longestLastNameByCity
     *         = people.stream().collect(groupingBy(Person::getCity,
     *                                              reducing(Person::getLastName, longerOf)));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <U> the type of the mapped values
     * @param identity the identity value for the reduction (also, the value
     *                 that is returned when there are no input elements)
     * @param mapper a mapping function to apply to each input value
     * @param op a {@code BinaryOperator<U>} used to reduce the mapped values
     * @return a {@code Collector} implementing the map-reduce operation
     *
     * @see #reducing(Object, BinaryOperator)
     * @see #reducing(BinaryOperator)
     */
    public static <T, U>
    Collector<T, U> reducing(U identity,
                             Function<? super T, ? extends U> mapper,
                             BinaryOperator<U> op) {
        return new CollectorImpl<>(() -> identity,
                                   (r, t) -> (r == null ? mapper.apply(t) : op.apply(r, mapper.apply(t))),
                                   op);
    }

    /**
     * Returns a {@code Collector} implementing a "group by" operation on
     * input elements of type {@code T}, grouping elements according to a
     * classification function.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The collector produces a {@code Map<K, List<T>>} whose keys are the
     * values resulting from applying the classification function to the input
     * elements, and whose corresponding values are {@code List}s containing the
     * input elements which map to the associated key under the classification
     * function.
     *
     * <p>There are no guarantees on the type, mutability, serializability, or
     * thread-safety of the {@code Map} or {@code List} objects returned.
     * @implSpec
     * This produces a result similar to:
     * <pre>{@code
     *     groupingBy(classifier, toList());
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param classifier the classifier function mapping input elements to keys
     * @return a {@code Collector} implementing the group-by operation
     *
     * @see #groupingBy(Function, Collector)
     * @see #groupingBy(Function, Supplier, Collector)
     * @see #groupingByConcurrent(Function)
     */
    public static <T, K>
    Collector<T, Map<K, List<T>>> groupingBy(Function<? super T, ? extends K> classifier) {
        return groupingBy(classifier, HashMap::new, toList());
    }

    /**
     * Returns a {@code Collector} implementing a cascaded "group by" operation
     * on input elements of type {@code T}, grouping elements according to a
     * classification function, and then performing a reduction operation on
     * the values associated with a given key using the specified downstream
     * {@code Collector}.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The downstream collector operates on elements of type {@code T} and
     * produces a result of type {@code D}. The resulting collector produces a
     * {@code Map<K, D>}.
     *
     * <p>There are no guarantees on the type, mutability,
     * serializability, or thread-safety of the {@code Map} returned.
     *
     * <p>For example, to compute the set of last names of people in each city:
     * <pre>{@code
     *     Map<City, Set<String>> namesByCity
     *         = people.stream().collect(groupingBy(Person::getCity,
     *                                              mapping(Person::getLastName, toSet())));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param <D> the result type of the downstream reduction
     * @param classifier a classifier function mapping input elements to keys
     * @param downstream a {@code Collector} implementing the downstream reduction
     * @return a {@code Collector} implementing the cascaded group-by operation
     * @see #groupingBy(Function)
     *
     * @see #groupingBy(Function, Supplier, Collector)
     * @see #groupingByConcurrent(Function, Collector)
     */
    public static <T, K, D>
    Collector<T, Map<K, D>> groupingBy(Function<? super T, ? extends K> classifier,
                                       Collector<? super T, D> downstream) {
        return groupingBy(classifier, HashMap::new, downstream);
    }

    /**
     * Returns a {@code Collector} implementing a cascaded "group by" operation
     * on input elements of type {@code T}, grouping elements according to a
     * classification function, and then performing a reduction operation on
     * the values associated with a given key using the specified downstream
     * {@code Collector}.  The {@code Map} produced by the Collector is created
     * with the supplied factory function.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The downstream collector operates on elements of type {@code T} and
     * produces a result of type {@code D}. The resulting collector produces a
     * {@code Map<K, D>}.
     *
     * <p>For example, to compute the set of last names of people in each city,
     * where the city names are sorted:
     * <pre>{@code
     *     Map<City, Set<String>> namesByCity
     *         = people.stream().collect(groupingBy(Person::getCity, TreeMap::new,
     *                                              mapping(Person::getLastName, toSet())));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param <D> the result type of the downstream reduction
     * @param <M> the type of the resulting {@code Map}
     * @param classifier a classifier function mapping input elements to keys
     * @param downstream a {@code Collector} implementing the downstream reduction
     * @param mapFactory a function which, when called, produces a new empty
     *                   {@code Map} of the desired type
     * @return a {@code Collector} implementing the cascaded group-by operation
     *
     * @see #groupingBy(Function, Collector)
     * @see #groupingBy(Function)
     * @see #groupingByConcurrent(Function, Supplier, Collector)
     */
    public static <T, K, D, M extends Map<K, D>>
    Collector<T, M> groupingBy(Function<? super T, ? extends K> classifier,
                               Supplier<M> mapFactory,
                               Collector<? super T, D> downstream) {
        Supplier<D> downstreamSupplier = downstream.resultSupplier();
        BiFunction<D, ? super T, D> downstreamAccumulator = downstream.accumulator();
        BiFunction<M, T, M> accumulator = (m, t) -> {
            K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
            D oldContainer = m.computeIfAbsent(key, k -> downstreamSupplier.get());
            D newContainer = downstreamAccumulator.apply(oldContainer, t);
            if (newContainer != oldContainer)
                m.put(key, newContainer);
            return m;
        };
        return new CollectorImpl<>(mapFactory, accumulator, mapMerger(downstream.combiner()), CH_STRICT);
    }

    /**
     * Returns a {@code Collector} implementing a concurrent "group by"
     * operation on input elements of type {@code T}, grouping elements
     * according to a classification function.
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The collector produces a {@code ConcurrentMap<K, List<T>>} whose keys are the
     * values resulting from applying the classification function to the input
     * elements, and whose corresponding values are {@code List}s containing the
     * input elements which map to the associated key under the classification
     * function.
     *
     * <p>There are no guarantees on the type, mutability, or serializability
     * of the {@code Map} or {@code List} objects returned, or of the
     * thread-safety of the {@code List} objects returned.
     * @implSpec
     * This produces a result similar to:
     * <pre>{@code
     *     groupingByConcurrent(classifier, toList());
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param classifier a classifier function mapping input elements to keys
     * @return a {@code Collector} implementing the group-by operation
     *
     * @see #groupingBy(Function)
     * @see #groupingByConcurrent(Function, Collector)
     * @see #groupingByConcurrent(Function, Supplier, Collector)
     */
    public static <T, K>
    Collector<T, ConcurrentMap<K, List<T>>> groupingByConcurrent(Function<? super T, ? extends K> classifier) {
        return groupingByConcurrent(classifier, ConcurrentHashMap::new, toList());
    }

    /**
     * Returns a {@code Collector} implementing a concurrent cascaded "group by"
     * operation on input elements of type {@code T}, grouping elements
     * according to a classification function, and then performing a reduction
     * operation on the values associated with a given key using the specified
     * downstream {@code Collector}.
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The downstream collector operates on elements of type {@code T} and
     * produces a result of type {@code D}. The resulting collector produces a
     * {@code Map<K, D>}.
     *
     * <p>For example, to compute the set of last names of people in each city,
     * where the city names are sorted:
     * <pre>{@code
     *     ConcurrentMap<City, Set<String>> namesByCity
     *         = people.stream().collect(groupingByConcurrent(Person::getCity, TreeMap::new,
     *                                                        mapping(Person::getLastName, toSet())));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param <D> the result type of the downstream reduction
     * @param classifier a classifier function mapping input elements to keys
     * @param downstream a {@code Collector} implementing the downstream reduction
     * @return a {@code Collector} implementing the cascaded group-by operation
     *
     * @see #groupingBy(Function, Collector)
     * @see #groupingByConcurrent(Function)
     * @see #groupingByConcurrent(Function, Supplier, Collector)
     */
    public static <T, K, D>
    Collector<T, ConcurrentMap<K, D>> groupingByConcurrent(Function<? super T, ? extends K> classifier,
                                                           Collector<? super T, D> downstream) {
        return groupingByConcurrent(classifier, ConcurrentHashMap::new, downstream);
    }

    /**
     * Returns a concurrent {@code Collector} implementing a cascaded "group by"
     * operation on input elements of type {@code T}, grouping elements
     * according to a classification function, and then performing a reduction
     * operation on the values associated with a given key using the specified
     * downstream {@code Collector}.  The {@code ConcurrentMap} produced by the
     * Collector is created with the supplied factory function.
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * <p>The classification function maps elements to some key type {@code K}.
     * The downstream collector operates on elements of type {@code T} and
     * produces a result of type {@code D}. The resulting collector produces a
     * {@code Map<K, D>}.
     *
     * <p>For example, to compute the set of last names of people in each city,
     * where the city names are sorted:
     * <pre>{@code
     *     ConcurrentMap<City, Set<String>> namesByCity
     *         = people.stream().collect(groupingBy(Person::getCity, ConcurrentSkipListMap::new,
     *                                              mapping(Person::getLastName, toSet())));
     * }</pre>
     *
     *
     * @param <T> the type of the input elements
     * @param <K> the type of the keys
     * @param <D> the result type of the downstream reduction
     * @param <M> the type of the resulting {@code ConcurrentMap}
     * @param classifier a classifier function mapping input elements to keys
     * @param downstream a {@code Collector} implementing the downstream reduction
     * @param mapFactory a function which, when called, produces a new empty
     *                   {@code ConcurrentMap} of the desired type
     * @return a {@code Collector} implementing the cascaded group-by operation
     *
     * @see #groupingByConcurrent(Function)
     * @see #groupingByConcurrent(Function, Collector)
     * @see #groupingBy(Function, Supplier, Collector)
     */
    public static <T, K, D, M extends ConcurrentMap<K, D>>
    Collector<T, M> groupingByConcurrent(Function<? super T, ? extends K> classifier,
                                         Supplier<M> mapFactory,
                                         Collector<? super T, D> downstream) {
        Supplier<D> downstreamSupplier = downstream.resultSupplier();
        BiFunction<D, ? super T, D> downstreamAccumulator = downstream.accumulator();
        BinaryOperator<M> combiner = mapMerger(downstream.combiner());
        if (downstream.characteristics().contains(Collector.Characteristics.CONCURRENT)) {
            BiFunction<M, T, M> accumulator = (m, t) -> {
                K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
                downstreamAccumulator.apply(m.computeIfAbsent(key, k -> downstreamSupplier.get()), t);
                return m;
            };
            return new CollectorImpl<>(mapFactory, accumulator, combiner, CH_CONCURRENT);
        } else if (downstream.characteristics().contains(Collector.Characteristics.STRICTLY_MUTATIVE)) {
            BiFunction<M, T, M> accumulator = (m, t) -> {
                K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
                D resultContainer = m.computeIfAbsent(key, k -> downstreamSupplier.get());
                synchronized (resultContainer) {
                    downstreamAccumulator.apply(resultContainer, t);
                }
                return m;
            };
            return new CollectorImpl<>(mapFactory, accumulator, combiner, CH_CONCURRENT);
        } else {
            BiFunction<M, T, M> accumulator = (m, t) -> {
                K key = Objects.requireNonNull(classifier.apply(t), "element cannot be mapped to a null key");
                do {
                    D oldResult = m.computeIfAbsent(key, k -> downstreamSupplier.get());
                    if (oldResult == null) {
                        if (m.putIfAbsent(key, downstreamAccumulator.apply(null, t)) == null)
                            return m;
                    } else {
                        synchronized (oldResult) {
                            if (m.get(key) != oldResult)
                                continue;
                            D newResult = downstreamAccumulator.apply(oldResult, t);
                            if (oldResult != newResult)
                                m.put(key, newResult);
                            return m;
                        }
                    }
                } while (true);
            };
            return new CollectorImpl<>(mapFactory, accumulator, combiner, CH_CONCURRENT);
        }
    }

    /**
     * Returns a {@code Collector} which partitions the input elements according
     * to a {@code Predicate}, and organizes them into a
     * {@code Map<Boolean, List<T>>}.
     *
     * There are no guarantees on the type, mutability,
     * serializability, or thread-safety of the {@code Map} returned.
     *
     * @param <T> the type of the input elements
     * @param predicate a predicate used for classifying input elements
     * @return a {@code Collector} implementing the partitioning operation
     *
     * @see #partitioningBy(Predicate, Collector)
     */
    public static <T>
    Collector<T, Map<Boolean, List<T>>> partitioningBy(Predicate<? super T> predicate) {
        return partitioningBy(predicate, toList());
    }

    /**
     * Returns a {@code Collector} which partitions the input elements according
     * to a {@code Predicate}, reduces the values in each partition according to
     * another {@code Collector}, and organizes them into a
     * {@code Map<Boolean, D>} whose values are the result of the downstream
     * reduction.
     *
     * <p>There are no guarantees on the type, mutability,
     * serializability, or thread-safety of the {@code Map} returned.
     *
     * @param <T> the type of the input elements
     * @param <D> the result type of the downstream reduction
     * @param predicate a predicate used for classifying input elements
     * @param downstream a {@code Collector} implementing the downstream
     *                   reduction
     * @return a {@code Collector} implementing the cascaded partitioning
     *         operation
     *
     * @see #partitioningBy(Predicate)
     */
    public static <T, D>
    Collector<T, Map<Boolean, D>> partitioningBy(Predicate<? super T> predicate,
                                                 Collector<? super T, D> downstream) {
        BiFunction<D, ? super T, D> downstreamAccumulator = downstream.accumulator();
        BiFunction<Map<Boolean, D>, T, Map<Boolean, D>> accumulator = (result, t) -> {
            Partition<D> asPartition = ((Partition<D>) result);
            if (predicate.test(t)) {
                D newResult = downstreamAccumulator.apply(asPartition.forTrue, t);
                if (newResult != asPartition.forTrue)
                    asPartition.forTrue = newResult;
            } else {
                D newResult = downstreamAccumulator.apply(asPartition.forFalse, t);
                if (newResult != asPartition.forFalse)
                    asPartition.forFalse = newResult;
            }
            return result;
        };
        return new CollectorImpl<>(() -> new Partition<>(downstream.resultSupplier().get(),
                                                         downstream.resultSupplier().get()),
                                   accumulator, partitionMerger(downstream.combiner()), CH_STRICT);
    }

    /**
     * Merge function for two partitions, given a merge function for the
     * elements.
     */
    private static <D> BinaryOperator<Map<Boolean, D>> partitionMerger(BinaryOperator<D> op) {
        return (m1, m2) -> {
            Partition<D> left = (Partition<D>) m1;
            Partition<D> right = (Partition<D>) m2;
            if (left.forFalse == null)
                left.forFalse = right.forFalse;
            else if (right.forFalse != null)
                left.forFalse = op.apply(left.forFalse, right.forFalse);
            if (left.forTrue == null)
                left.forTrue = right.forTrue;
            else if (right.forTrue != null)
                left.forTrue = op.apply(left.forTrue, right.forTrue);
            return left;
        };
    }

    /**
     * Accumulate elements into a {@code Map} whose keys and values are the
     * result of applying mapping functions to the input elements.
     * If the mapped keys contains duplicates (according to
     * {@link Object#equals(Object)}), an {@code IllegalStateException} is
     * thrown when the collection operation is performed.  If the mapped keys
     * may have duplicates, use {@link #toMap(Function, Function, BinaryOperator)}
     * instead.
     *
     * @apiNote
     * It is common for either the key or the value to be the input elements.
     * In this case, the utility method
     * {@link java.util.function.Function#identity()} may be helpful.
     * For example, the following produces a {@code Map} mapping
     * students to their grade point average:
     * <pre>{@code
     *     Map<Student, Double> studentToGPA
     *         students.stream().collect(toMap(Functions.identity(),
     *                                         student -> computeGPA(student)));
     * }</pre>
     * And the following produces a {@code Map} mapping a unique identifier to
     * students:
     * <pre>{@code
     *     Map<String, Student> studentIdToStudent
     *         students.stream().collect(toMap(Student::getId,
     *                                         Functions.identity());
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @return a {@code Collector} which collects elements into a {@code Map}
     * whose keys and values are the result of applying mapping functions to
     * the input elements
     *
     * @see #toMap(Function, Function, BinaryOperator)
     * @see #toMap(Function, Function, BinaryOperator, Supplier)
     * @see #toConcurrentMap(Function, Function)
     */
    public static <T, K, U>
    Collector<T, Map<K,U>> toMap(Function<? super T, ? extends K> keyMapper,
                                 Function<? super T, ? extends U> valueMapper) {
        return toMap(keyMapper, valueMapper, throwingMerger(), HashMap::new);
    }

    /**
     * Accumulate elements into a {@code Map} whose keys and values are the
     * result of applying mapping functions to the input elements. If the mapped
     * keys contains duplicates (according to {@link Object#equals(Object)}),
     * the value mapping function is applied to each equal element, and the
     * results are merged using the provided merging function.
     *
     * @apiNote
     * There are multiple ways to deal with collisions between multiple elements
     * mapping to the same key.  There are some predefined merging functions,
     * such as {@link #throwingMerger()}, {@link #firstWinsMerger()}, and
     * {@link #lastWinsMerger()}, that implement common policies, or you can
     * implement custom policies easily.  For example, if you have a stream
     * of {@code Person}, and you want to produce a "phone book" mapping name to
     * address, but it is possible that two persons have the same name, you can
     * do as follows to gracefully deals with these collisions, and produce a
     * {@code Map} mapping names to a concatenated list of addresses:
     * <pre>{@code
     *     Map<String, String> phoneBook
     *         people.stream().collect(toMap(Person::getName,
     *                                       Person::getAddress,
     *                                       (s, a) -> s + ", " + a));
     * }</pre>
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param mergeFunction a merge function, used to resolve collisions between
     *                      values associated with the same key, as supplied
     *                      to {@link Map#merge(Object, Object, BiFunction)}
     * @return a {@code Collector} which collects elements into a {@code Map}
     * whose keys are the result of applying a key mapping function to the input
     * elements, and whose values are the result of applying a value mapping
     * function to all input elements equal to the key and combining them
     * using the merge function
     *
     * @see #toMap(Function, Function)
     * @see #toMap(Function, Function, BinaryOperator, Supplier)
     * @see #toConcurrentMap(Function, Function, BinaryOperator)
     */
    public static <T, K, U>
    Collector<T, Map<K,U>> toMap(Function<? super T, ? extends K> keyMapper,
                                 Function<? super T, ? extends U> valueMapper,
                                 BinaryOperator<U> mergeFunction) {
        return toMap(keyMapper, valueMapper, mergeFunction, HashMap::new);
    }

    /**
     * Accumulate elements into a {@code Map} whose keys and values are the
     * result of applying mapping functions to the input elements. If the mapped
     * keys contains duplicates (according to {@link Object#equals(Object)}),
     * the value mapping function is applied to each equal element, and the
     * results are merged using the provided merging function.  The {@code Map}
     * is created by a provided supplier function.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param <M> the type of the resulting {@code Map}
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param mergeFunction a merge function, used to resolve collisions between
     *                      values associated with the same key, as supplied
     *                      to {@link Map#merge(Object, Object, BiFunction)}
     * @param mapSupplier a function which returns a new, empty {@code Map} into
     *                    which the results will be inserted
     * @return a {@code Collector} which collects elements into a {@code Map}
     * whose keys are the result of applying a key mapping function to the input
     * elements, and whose values are the result of applying a value mapping
     * function to all input elements equal to the key and combining them
     * using the merge function
     *
     * @see #toMap(Function, Function)
     * @see #toMap(Function, Function, BinaryOperator)
     * @see #toConcurrentMap(Function, Function, BinaryOperator, Supplier)
     */
    public static <T, K, U, M extends Map<K, U>>
    Collector<T, M> toMap(Function<? super T, ? extends K> keyMapper,
                          Function<? super T, ? extends U> valueMapper,
                          BinaryOperator<U> mergeFunction,
                          Supplier<M> mapSupplier) {
        BiFunction<M, T, M> accumulator
                = (map, element) -> {
                      map.merge(keyMapper.apply(element), valueMapper.apply(element), mergeFunction);
                      return map;
                  };
        return new CollectorImpl<>(mapSupplier, accumulator, mapMerger(mergeFunction), CH_STRICT);
    }

    /**
     * Accumulate elements into a {@code ConcurrentMap} whose keys and values
     * are the result of applying mapping functions to the input elements.
     * If the mapped keys contains duplicates (according to
     * {@link Object#equals(Object)}), an {@code IllegalStateException} is
     * thrown when the collection operation is performed.  If the mapped keys
     * may have duplicates, use
     * {@link #toConcurrentMap(Function, Function, BinaryOperator)} instead.
     *
     * @apiNote
     * It is common for either the key or the value to be the input elements.
     * In this case, the utility method
     * {@link java.util.function.Function#identity()} may be helpful.
     * For example, the following produces a {@code Map} mapping
     * students to their grade point average:
     * <pre>{@code
     *     Map<Student, Double> studentToGPA
     *         students.stream().collect(toMap(Functions.identity(),
     *                                         student -> computeGPA(student)));
     * }</pre>
     * And the following produces a {@code Map} mapping a unique identifier to
     * students:
     * <pre>{@code
     *     Map<String, Student> studentIdToStudent
     *         students.stream().collect(toConcurrentMap(Student::getId,
     *                                                   Functions.identity());
     * }</pre>
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param keyMapper the mapping function to produce keys
     * @param valueMapper the mapping function to produce values
     * @return a concurrent {@code Collector} which collects elements into a
     * {@code ConcurrentMap} whose keys are the result of applying a key mapping
     * function to the input elements, and whose values are the result of
     * applying a value mapping function to the input elements
     *
     * @see #toMap(Function, Function)
     * @see #toConcurrentMap(Function, Function, BinaryOperator)
     * @see #toConcurrentMap(Function, Function, BinaryOperator, Supplier)
     */
    public static <T, K, U>
    Collector<T, ConcurrentMap<K,U>> toConcurrentMap(Function<? super T, ? extends K> keyMapper,
                                                     Function<? super T, ? extends U> valueMapper) {
        return toConcurrentMap(keyMapper, valueMapper, throwingMerger(), ConcurrentHashMap::new);
    }

    /**
     * Accumulate elements into a {@code ConcurrentMap} whose keys and values
     * are the result of applying mapping functions to the input elements. If
     * the mapped keys contains duplicates (according to {@link Object#equals(Object)}),
     * the value mapping function is applied to each equal element, and the
     * results are merged using the provided merging function.
     *
     * @apiNote
     * There are multiple ways to deal with collisions between multiple elements
     * mapping to the same key.  There are some predefined merging functions,
     * such as {@link #throwingMerger()}, {@link #firstWinsMerger()}, and
     * {@link #lastWinsMerger()}, that implement common policies, or you can
     * implement custom policies easily.  For example, if you have a stream
     * of {@code Person}, and you want to produce a "phone book" mapping name to
     * address, but it is possible that two persons have the same name, you can
     * do as follows to gracefully deals with these collisions, and produce a
     * {@code Map} mapping names to a concatenated list of addresses:
     * <pre>{@code
     *     Map<String, String> phoneBook
     *         people.stream().collect(toConcurrentMap(Person::getName,
     *                                                 Person::getAddress,
     *                                                 (s, a) -> s + ", " + a));
     * }</pre>
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param mergeFunction a merge function, used to resolve collisions between
     *                      values associated with the same key, as supplied
     *                      to {@link Map#merge(Object, Object, BiFunction)}
     * @return a concurrent {@code Collector} which collects elements into a
     * {@code ConcurrentMap} whose keys are the result of applying a key mapping
     * function to the input elements, and whose values are the result of
     * applying a value mapping function to all input elements equal to the key
     * and combining them using the merge function
     *
     * @see #toConcurrentMap(Function, Function)
     * @see #toConcurrentMap(Function, Function, BinaryOperator, Supplier)
     * @see #toMap(Function, Function, BinaryOperator)
     */
    public static <T, K, U>
    Collector<T, ConcurrentMap<K,U>> toConcurrentMap(Function<? super T, ? extends K> keyMapper,
                                                     Function<? super T, ? extends U> valueMapper,
                                                     BinaryOperator<U> mergeFunction) {
        return toConcurrentMap(keyMapper, valueMapper, mergeFunction, ConcurrentHashMap::new);
    }

    /**
     * Accumulate elements into a {@code ConcurrentMap} whose keys and values
     * are the result of applying mapping functions to the input elements. If
     * the mapped keys contains duplicates (according to {@link Object#equals(Object)}),
     * the value mapping function is applied to each equal element, and the
     * results are merged using the provided merging function.  The
     * {@code ConcurrentMap} is created by a provided supplier function.
     *
     * <p>This is a {@link Collector.Characteristics#CONCURRENT concurrent} and
     * {@link Collector.Characteristics#UNORDERED unordered} Collector.
     *
     * @param <T> the type of the input elements
     * @param <K> the output type of the key mapping function
     * @param <U> the output type of the value mapping function
     * @param <M> the type of the resulting {@code ConcurrentMap}
     * @param keyMapper a mapping function to produce keys
     * @param valueMapper a mapping function to produce values
     * @param mergeFunction a merge function, used to resolve collisions between
     *                      values associated with the same key, as supplied
     *                      to {@link Map#merge(Object, Object, BiFunction)}
     * @param mapSupplier a function which returns a new, empty {@code Map} into
     *                    which the results will be inserted
     * @return a concurrent {@code Collector} which collects elements into a
     * {@code ConcurrentMap} whose keys are the result of applying a key mapping
     * function to the input elements, and whose values are the result of
     * applying a value mapping function to all input elements equal to the key
     * and combining them using the merge function
     *
     * @see #toConcurrentMap(Function, Function)
     * @see #toConcurrentMap(Function, Function, BinaryOperator)
     * @see #toMap(Function, Function, BinaryOperator, Supplier)
     */
    public static <T, K, U, M extends ConcurrentMap<K, U>>
    Collector<T, M> toConcurrentMap(Function<? super T, ? extends K> keyMapper,
                                    Function<? super T, ? extends U> valueMapper,
                                    BinaryOperator<U> mergeFunction,
                                    Supplier<M> mapSupplier) {
        BiFunction<M, T, M> accumulator = (map, element) -> {
            map.merge(keyMapper.apply(element), valueMapper.apply(element), mergeFunction);
            return map;
        };
        return new CollectorImpl<>(mapSupplier, accumulator, mapMerger(mergeFunction), CH_CONCURRENT);
    }

    /**
     * Returns a {@code Collector} which applies an {@code int}-producing
     * mapping function to each input element, and returns summary statistics
     * for the resulting values.
     *
     * @param <T> the type of the input elements
     * @param mapper a mapping function to apply to each element
     * @return a {@code Collector} implementing the summary-statistics reduction
     *
     * @see #toDoubleSummaryStatistics(ToDoubleFunction)
     * @see #toLongSummaryStatistics(ToLongFunction)
     */
    public static <T>
    Collector<T, IntSummaryStatistics> toIntSummaryStatistics(ToIntFunction<? super T> mapper) {
        return new CollectorImpl<>(IntSummaryStatistics::new,
                                   (r, t) -> { r.accept(mapper.applyAsInt(t)); return r; },
                                   (l, r) -> { l.combine(r); return l; }, CH_STRICT);
    }

    /**
     * Returns a {@code Collector} which applies an {@code long}-producing
     * mapping function to each input element, and returns summary statistics
     * for the resulting values.
     *
     * @param <T> the type of the input elements
     * @param mapper the mapping function to apply to each element
     * @return a {@code Collector} implementing the summary-statistics reduction
     *
     * @see #toDoubleSummaryStatistics(ToDoubleFunction)
     * @see #toIntSummaryStatistics(ToIntFunction)
     */
    public static <T>
    Collector<T, LongSummaryStatistics> toLongSummaryStatistics(ToLongFunction<? super T> mapper) {
        return new CollectorImpl<>(LongSummaryStatistics::new,
                                   (r, t) -> { r.accept(mapper.applyAsLong(t)); return r; },
                                   (l, r) -> { l.combine(r); return l; }, CH_STRICT);
    }

    /**
     * Returns a {@code Collector} which applies an {@code double}-producing
     * mapping function to each input element, and returns summary statistics
     * for the resulting values.
     *
     * @param <T> the type of the input elements
     * @param mapper a mapping function to apply to each element
     * @return a {@code Collector} implementing the summary-statistics reduction
     *
     * @see #toLongSummaryStatistics(ToLongFunction)
     * @see #toIntSummaryStatistics(ToIntFunction)
     */
    public static <T>
    Collector<T, DoubleSummaryStatistics> toDoubleSummaryStatistics(ToDoubleFunction<? super T> mapper) {
        return new CollectorImpl<>(DoubleSummaryStatistics::new,
                                   (r, t) -> { r.accept(mapper.applyAsDouble(t)); return r; },
                                   (l, r) -> { l.combine(r); return l; }, CH_STRICT);
    }

    /**
     * Implementation class used by partitioningBy.
     */
    private static final class Partition<T>
            extends AbstractMap<Boolean, T>
            implements Map<Boolean, T> {
        T forTrue;
        T forFalse;

        Partition(T forTrue, T forFalse) {
            this.forTrue = forTrue;
            this.forFalse = forFalse;
        }

        @Override
        public Set<Map.Entry<Boolean, T>> entrySet() {
            return new AbstractSet<Map.Entry<Boolean, T>>() {
                @Override
                public Iterator<Map.Entry<Boolean, T>> iterator() {

                    return new Iterator<Map.Entry<Boolean, T>>() {
                        int state = 0;

                        @Override
                        public boolean hasNext() {
                            return state < 2;
                        }

                        @Override
                        public Map.Entry<Boolean, T> next() {
                            if (state >= 2)
                                throw new NoSuchElementException();
                            return (state++ == 0)
                                   ? new SimpleImmutableEntry<>(false, forFalse)
                                   : new SimpleImmutableEntry<>(true, forTrue);
                        }
                    };
                }

                @Override
                public int size() {
                    return 2;
                }
            };
        }
    }
}
