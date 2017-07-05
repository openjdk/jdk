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

import java.util.Collections;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * A <a href="package-summary.html#Reduction">reduction operation</a> that
 * supports folding input elements into a cumulative result.  The result may be
 * a value or may be a mutable result container.  Examples of operations
 * accumulating results into a mutable result container include: accumulating
 * input elements into a {@code Collection}; concatenating strings into a
 * {@code StringBuilder}; computing summary information about elements such as
 * sum, min, max, or average; computing "pivot table" summaries such as "maximum
 * valued transaction by seller", etc.  Reduction operations can be performed
 * either sequentially or in parallel.
 *
 * <p>The following are examples of using the predefined {@code Collector}
 * implementations in {@link Collectors} with the {@code Stream} API to perform
 * mutable reduction tasks:
 * <pre>{@code
 *     // Accumulate elements into a List
 *     List<String> list = stream.collect(Collectors.toList());
 *
 *     // Accumulate elements into a TreeSet
 *     Set<String> list = stream.collect(Collectors.toCollection(TreeSet::new));
 *
 *     // Convert elements to strings and concatenate them, separated by commas
 *     String joined = stream.map(Object::toString)
 *                           .collect(Collectors.toStringJoiner(", "))
 *                           .toString();
 *
 *     // Find highest-paid employee
 *     Employee highestPaid = employees.stream()
 *                                     .collect(Collectors.maxBy(Comparators.comparing(Employee::getSalary)));
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
 *                                                   Collectors.maxBy(Comparators.comparing(Employee::getSalary))));
 *
 *     // Partition students into passing and failing
 *     Map<Boolean, List<Student>> passingFailing =
 *         students.stream()
 *                 .collect(Collectors.partitioningBy(s -> s.getGrade() >= PASS_THRESHOLD);
 *
 * }</pre>
 *
 * <p>A {@code Collector} is specified by three functions that work together to
 * manage a result or result container.  They are: creation of an initial
 * result, incorporating a new data element into a result, and combining two
 * results into one. The last function -- combining two results into one -- is
 * used during parallel operations, where subsets of the input are accumulated
 * in parallel, and then the subresults merged into a combined result. The
 * result may be a mutable container or a value.  If the result is mutable, the
 * accumulation and combination functions may either mutate their left argument
 * and return that (such as adding elements to a collection), or return a new
 * result, in which case it should not perform any mutation.
 *
 * <p>Collectors also have a set of characteristics, including
 * {@link Characteristics#CONCURRENT} and
 * {@link Characteristics#STRICTLY_MUTATIVE}.  These characteristics provide
 * hints that can be used by a reduction implementation to provide better
 * performance.
 *
 * <p>Libraries that implement reduction based on {@code Collector}, such as
 * {@link Stream#collect(Collector)}, must adhere to the following constraints:
 * <ul>
 *     <li>The first argument passed to the accumulator function, and both
 *     arguments passed to the combiner function, must be the result of a
 *     previous invocation of {@link #resultSupplier()}, {@link #accumulator()},
 *     or {@link #combiner()}.</li>
 *     <li>The implementation should not do anything with the result of any of
 *     the result supplier, accumulator, or combiner functions other than to
 *     pass them again to the accumulator or combiner functions, or return them
 *     to the caller of the reduction operation.</li>
 *     <li>If a result is passed to the accumulator or combiner function, and
 *     the same object is not returned from that function, it is never used
 *     again.</li>
 *     <li>Once a result is passed to the combiner function, it is never passed
 *     to the accumulator function again.</li>
 *     <li>For non-concurrent collectors, any result returned from the result
 *     supplier, accumulator, or combiner functions must be serially
 *     thread-confined.  This enables collection to occur in parallel without
 *     the {@code Collector} needing to implement any additional synchronization.
 *     The reduction implementation must manage that the input is properly
 *     partitioned, that partitions are processed in isolation, and combining
 *     happens only after accumulation is complete.</li>
 *     <li>For concurrent collectors, an implementation is free to (but not
 *     required to) implement reduction concurrently.  A concurrent reduction
 *     is one where the accumulator function is called concurrently from
 *     multiple threads, using the same concurrently-modifiable result container,
 *     rather than keeping the result isolated during accumulation.
 *     A concurrent reduction should only be applied if the collector has the
 *     {@link Characteristics#UNORDERED} characteristics or if the
 *     originating data is unordered.</li>
 * </ul>
 *
 * @apiNote
 * Performing a reduction operation with a {@code Collector} should produce a
 * result equivalent to:
 * <pre>{@code
 *     BiFunction<R,T,R> accumulator = collector.accumulator();
 *     R result = collector.resultSupplier().get();
 *     for (T t : data)
 *         result = accumulator.apply(result, t);
 *     return result;
 * }</pre>
 *
 * <p>However, the library is free to partition the input, perform the reduction
 * on the partitions, and then use the combiner function to combine the partial
 * results to achieve a parallel reduction.  Depending on the specific reduction
 * operation, this may perform better or worse, depending on the relative cost
 * of the accumulator and combiner functions.
 *
 * <p>An example of an operation that can be easily modeled by {@code Collector}
 * is accumulating elements into a {@code TreeSet}. In this case, the {@code
 * resultSupplier()} function is {@code () -> new Treeset<T>()}, the
 * {@code accumulator} function is
 * {@code (set, element) -> { set.add(element); return set; }}, and the combiner
 * function is {@code (left, right) -> { left.addAll(right); return left; }}.
 * (This behavior is implemented by
 * {@code Collectors.toCollection(TreeSet::new)}).
 *
 * TODO Associativity and commutativity
 *
 * @see Stream#collect(Collector)
 * @see Collectors
 *
 * @param <T> the type of input element to the collect operation
 * @param <R> the result type of the collect operation
 * @since 1.8
 */
public interface Collector<T, R> {
    /**
     * A function that creates and returns a new result that represents
     * "no values".  If the accumulator or combiner functions may mutate their
     * arguments, this must be a new, empty result container.
     *
     * @return a function which, when invoked, returns a result representing
     * "no values"
     */
    Supplier<R> resultSupplier();

    /**
     * A function that folds a new value into a cumulative result.  The result
     * may be a mutable result container or a value.  The accumulator function
     * may modify a mutable container and return it, or create a new result and
     * return that, but if it returns a new result object, it must not modify
     * any of its arguments.
     *
     * <p>If the collector has the {@link Characteristics#STRICTLY_MUTATIVE}
     * characteristic, then the accumulator function <em>must</em> always return
     * its first argument, after possibly mutating its state.
     *
     * @return a function which folds a new value into a cumulative result
     */
    BiFunction<R, T, R> accumulator();

    /**
     * A function that accepts two partial results and merges them.  The
     * combiner function may fold state from one argument into the other and
     * return that, or may return a new result object, but if it returns
     * a new result object, it must not modify the state of either of its
     * arguments.
     *
     * <p>If the collector has the {@link Characteristics#STRICTLY_MUTATIVE}
     * characteristic, then the combiner function <em>must</em> always return
     * its first argument, after possibly mutating its state.
     *
     * @return a function which combines two partial results into a cumulative
     * result
     */
    BinaryOperator<R> combiner();

    /**
     * Returns a {@code Set} of {@code Collector.Characteristics} indicating
     * the characteristics of this Collector.  This set should be immutable.
     *
     * @return an immutable set of collector characteristics
     */
    Set<Characteristics> characteristics();

    /**
     * Characteristics indicating properties of a {@code Collector}, which can
     * be used to optimize reduction implementations.
     */
    enum Characteristics {
        /**
         * Indicates that this collector is <em>concurrent</em>, meaning that
         * the result container can support the accumulator function being
         * called concurrently with the same result container from multiple
         * threads. Concurrent collectors must also always have the
         * {@code STRICTLY_MUTATIVE} characteristic.
         *
         * <p>If a {@code CONCURRENT} collector is not also {@code UNORDERED},
         * then it should only be evaluated concurrently if applied to an
         * unordered data source.
         */
        CONCURRENT,

        /**
         * Indicates that the result container has no intrinsic order, such as
         * a {@link Set}.
         */
        UNORDERED,

        /**
         * Indicates that this collector operates by strict mutation of its
         * result container. This means that the {@link #accumulator()} and
         * {@link #combiner()} functions will always modify the state of and
         * return their first argument, rather than returning a different result
         * container.
         */
        STRICTLY_MUTATIVE
    }
}
