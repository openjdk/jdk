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

/**
 * <h1>java.util.stream</h1>
 *
 * Classes to support functional-style operations on streams of values, as in the following:
 *
 * <pre>{@code
 *     int sumOfWeights = blocks.stream().filter(b -> b.getColor() == RED)
 *                                       .mapToInt(b -> b.getWeight())
 *                                       .sum();
 * }</pre>
 *
 * <p>Here we use {@code blocks}, which might be a {@code Collection}, as a source for a stream,
 * and then perform a filter-map-reduce ({@code sum()} is an example of a <a href="package-summary.html#Reduction">reduction</a>
 * operation) on the stream to obtain the sum of the weights of the red blocks.
 *
 * <p>The key abstraction used in this approach is {@link java.util.stream.Stream}, as well as its primitive
 * specializations {@link java.util.stream.IntStream}, {@link java.util.stream.LongStream},
 * and {@link java.util.stream.DoubleStream}.  Streams differ from Collections in several ways:
 *
 * <ul>
 *     <li>No storage.  A stream is not a data structure that stores elements; instead, they
 *     carry values from a source (which could be a data structure, a generator, an IO channel, etc)
 *     through a pipeline of computational operations.</li>
 *     <li>Functional in nature.  An operation on a stream produces a result, but does not modify
 *     its underlying data source.  For example, filtering a {@code Stream} produces a new {@code Stream},
 *     rather than removing elements from the underlying source.</li>
 *     <li>Laziness-seeking.  Many stream operations, such as filtering, mapping, or duplicate removal,
 *     can be implemented lazily, exposing opportunities for optimization.  (For example, "find the first
 *     {@code String} matching a pattern" need not examine all the input strings.)  Stream operations
 *     are divided into intermediate ({@code Stream}-producing) operations and terminal (value-producing)
 *     operations; all intermediate operations are lazy.</li>
 *     <li>Possibly unbounded.  While collections have a finite size, streams need not.  Operations
 *     such as {@code limit(n)} or {@code findFirst()} can allow computations on infinite streams
 *     to complete in finite time.</li>
 * </ul>
 *
 * <h2><a name="StreamPipelines">Stream pipelines</a></h2>
 *
 * <p>Streams are used to create <em>pipelines</em> of <a href="package-summary.html#StreamOps">operations</a>.  A
 * complete stream pipeline has several components: a source (which may be a {@code Collection},
 * an array, a generator function, or an IO channel); zero or more <em>intermediate operations</em>
 * such as {@code Stream.filter} or {@code Stream.map}; and a <em>terminal operation</em> such
 * as {@code Stream.forEach} or {@code java.util.stream.Stream.reduce}.  Stream operations may take as parameters
 * <em>function values</em> (which are often lambda expressions, but could be method references
 * or objects) which parameterize the behavior of the operation, such as a {@code Predicate}
 * passed to the {@code Stream#filter} method.
 *
 * <p>Intermediate operations return a new {@code Stream}.  They are lazy; executing an
 * intermediate operation such as {@link java.util.stream.Stream#filter Stream.filter} does
 * not actually perform any filtering, instead creating a new {@code Stream} that, when
 * traversed, contains the elements of the initial {@code Stream} that match the
 * given {@code Predicate}.  Consuming elements from the  stream source does not
 * begin until the terminal operation is executed.
 *
 * <p>Terminal operations consume the {@code Stream} and produce a result or a side-effect.
 * After a terminal operation is performed, the stream can no longer be used and you must
 * return to the data source, or select a new data source, to get a new stream. For example,
 * obtaining the sum of weights of all red blocks, and then of all blue blocks, requires a
 * filter-map-reduce on two different streams:
 * <pre>{@code
 *     int sumOfRedWeights  = blocks.stream().filter(b -> b.getColor() == RED)
 *                                           .mapToInt(b -> b.getWeight())
 *                                           .sum();
 *     int sumOfBlueWeights = blocks.stream().filter(b -> b.getColor() == BLUE)
 *                                           .mapToInt(b -> b.getWeight())
 *                                           .sum();
 * }</pre>
 *
 * <p>However, there are other techniques that allow you to obtain both results in a single
 * pass if multiple traversal is impractical or inefficient.  TODO provide link
 *
 * <h3><a name="StreamOps">Stream operations</a></h3>
 *
 * <p>Intermediate stream operation (such as {@code filter} or {@code sorted}) always produce a
 * new {@code Stream}, and are always<em>lazy</em>.  Executing a lazy operations does not
 * trigger processing of the stream contents; all processing is deferred until the terminal
 * operation commences.  Processing streams lazily allows for significant efficiencies; in a
 * pipeline such as the filter-map-sum example above, filtering, mapping, and addition can be
 * fused into a single pass, with minimal intermediate state.  Laziness also enables us to avoid
 * examining all the data when it is not necessary; for operations such as "find the first
 * string longer than 1000 characters", one need not examine all the input strings, just enough
 * to find one that has the desired characteristics.  (This behavior becomes even more important
 * when the input stream is infinite and not merely large.)
 *
 * <p>Intermediate operations are further divided into <em>stateless</em> and <em>stateful</em>
 * operations.  Stateless operations retain no state from previously seen values when processing
 * a new value; examples of stateless intermediate operations include {@code filter} and
 * {@code map}.  Stateful operations may incorporate state from previously seen elements in
 * processing new values; examples of stateful intermediate operations include {@code distinct}
 * and {@code sorted}.  Stateful operations may need to process the entire input before
 * producing a result; for example, one cannot produce any results from sorting a stream until
 * one has seen all elements of the stream.  As a result, under parallel computation, some
 * pipelines containing stateful intermediate operations have to be executed in multiple passes.
 * Pipelines containing exclusively stateless intermediate operations can be processed in a
 * single pass, whether sequential or parallel.
 *
 * <p>Further, some operations are deemed <em>short-circuiting</em> operations.  An intermediate
 * operation is short-circuiting if, when presented with infinite input, it may produce a
 * finite stream as a result.  A terminal operation is short-circuiting if, when presented with
 * infinite input, it may terminate in finite time.  (Having a short-circuiting operation is a
 * necessary, but not sufficient, condition for the processing of an infinite stream to
 * terminate normally in finite time.)
 *
 * Terminal operations (such as {@code forEach} or {@code findFirst}) are always eager
 * (they execute completely before returning), and produce a non-{@code Stream} result, such
 * as a primitive value or a {@code Collection}, or have side-effects.
 *
 * <h3>Parallelism</h3>
 *
 * <p>By recasting aggregate operations as a pipeline of operations on a stream of values, many
 * aggregate operations can be more easily parallelized.  A {@code Stream} can execute either
 * in serial or in parallel.  When streams are created, they are either created as sequential
 * or parallel streams; the parallel-ness of streams can also be switched by the
 * {@link java.util.stream Stream#sequential()} and {@link java.util.stream.Stream#parallel()}
 * operations.  The {@code Stream} implementations in the JDK create serial streams unless
 * parallelism is explicitly requested.  For example, {@code Collection} has methods
 * {@link java.util.Collection#stream} and {@link java.util.Collection#parallelStream},
 * which produce sequential and parallel streams respectively; other stream-bearing methods
 * such as {@link java.util.stream.IntStream#range(int, int)} produce sequential
 * streams but these can be efficiently parallelized by calling {@code parallel()} on the
 * result. The set of operations on serial and parallel streams is identical. To execute the
 * "sum of weights of blocks" query in parallel, we would do:
 *
 * <pre>{@code
 *     int sumOfWeights = blocks.parallelStream().filter(b -> b.getColor() == RED)
 *                                               .mapToInt(b -> b.getWeight())
 *                                               .sum();
 * }</pre>
 *
 * <p>The only difference between the serial and parallel versions of this example code is
 * the creation of the initial {@code Stream}.  Whether a {@code Stream} will execute in serial
 * or parallel can be determined by the {@code Stream#isParallel} method.  When the terminal
 * operation is initiated, the entire stream pipeline is either executed sequentially or in
 * parallel, determined by the last operation that affected the stream's serial-parallel
 * orientation (which could be the stream source, or the {@code sequential()} or
 * {@code parallel()} methods.)
 *
 * <p>In order for the results of parallel operations to be deterministic and consistent with
 * their serial equivalent, the function values passed into the various stream operations should
 * be <a href="#NonInteference"><em>stateless</em></a>.
 *
 * <h3><a name="Ordering">Ordering</a></h3>
 *
 * <p>Streams may or may not have an <em>encounter order</em>.  An encounter
 * order specifies the order in which elements are provided by the stream to the
 * operations pipeline.  Whether or not there is an encounter order depends on
 * the source, the intermediate  operations, and the terminal operation.
 * Certain stream sources (such as {@code List} or arrays) are intrinsically
 * ordered, whereas others (such as {@code HashSet}) are not.  Some intermediate
 * operations may impose an encounter order on an otherwise unordered stream,
 * such as {@link java.util.stream.Stream#sorted()}, and others may render an
 * ordered stream unordered (such as {@link java.util.stream.Stream#unordered()}).
 * Some terminal operations may ignore encounter order, such as
 * {@link java.util.stream.Stream#forEach}.
 *
 * <p>If a Stream is ordered, most operations are constrained to operate on the
 * elements in their encounter order; if the source of a stream is a {@code List}
 * containing {@code [1, 2, 3]}, then the result of executing {@code map(x -> x*2)}
 * must be {@code [2, 4, 6]}.  However, if the source has no defined encounter
 * order, than any of the six permutations of the values {@code [2, 4, 6]} would
 * be a valid result. Many operations can still be efficiently parallelized even
 * under ordering constraints.
 *
 * <p>For sequential streams, ordering is only relevant to the determinism
 * of operations performed repeatedly on the same source.  (An {@code ArrayList}
 * is constrained to iterate elements in order; a {@code HashSet} is not, and
 * repeated iteration might produce a different order.)
 *
 * <p>For parallel streams, relaxing the ordering constraint can enable
 * optimized implementation for some operations.  For example, duplicate
 * filtration on an ordered stream must completely process the first partition
 * before it can return any elements from a subsequent partition, even if those
 * elements are available earlier.  On the other hand, without the constraint of
 * ordering, duplicate filtration can be done more efficiently by using
 * a shared {@code ConcurrentHashSet}.  There will be cases where the stream
 * is structurally ordered (the source is ordered and the intermediate
 * operations are order-preserving), but the user does not particularly care
 * about the encounter order.  In some cases, explicitly de-ordering the stream
 * with the {@link java.util.stream.Stream#unordered()} method may result in
 * improved parallel performance for some stateful or terminal operations.
 *
 * <h3><a name="Non-Interference">Non-interference</a></h3>
 *
 * The {@code java.util.stream} package enables you to execute possibly-parallel
 * bulk-data operations over a variety of data sources, including even non-thread-safe
 * collections such as {@code ArrayList}.  This is possible only if we can
 * prevent <em>interference</em> with the data source during the execution of a
 * stream pipeline.  (Execution begins when the terminal operation is invoked, and ends
 * when the terminal operation completes.)  For most data sources, preventing interference
 * means ensuring that the data source is <em>not modified at all</em> during the execution
 * of the stream pipeline.  (Some data sources, such as concurrent collections, are
 * specifically designed to handle concurrent modification.)
 *
 * <p>Accordingly, lambda expressions (or other objects implementing the appropriate functional
 * interface) passed to stream methods should never modify the stream's data source.  An
 * implementation is said to <em>interfere</em> with the data source if it modifies, or causes
 * to be modified, the stream's data source.  The need for non-interference applies to all
 * pipelines, not just parallel ones.  Unless the stream source is concurrent, modifying a
 * stream's data source during execution of a stream pipeline can cause exceptions, incorrect
 * answers, or nonconformant results.
 *
 * <p>Further, results may be nondeterministic or incorrect if the lambda expressions passed to
 * stream operations are <em>stateful</em>.  A stateful lambda (or other object implementing the
 * appropriate functional interface) is one whose result depends on any state which might change
 * during the execution of the stream pipeline.  An example of a stateful lambda is:
 * <pre>{@code
 *     Set<Integer> seen = Collections.synchronizedSet(new HashSet<>());
 *     stream.parallel().map(e -> { if (seen.add(e)) return 0; else return e; })...
 * }</pre>
 * Here, if the mapping operation is performed in parallel, the results for the same input
 * could vary from run to run, due to thread scheduling differences, whereas, with a stateless
 * lambda expression the results would always be the same.
 *
 * <h3>Side-effects</h3>
 *
 * <h2><a name="Reduction">Reduction operations</a></h2>
 *
 * A <em>reduction</em> operation takes a stream of elements and processes them in a way
 * that reduces to a single value or summary description, such as finding the sum or maximum
 * of a set of numbers.  (In more complex scenarios, the reduction operation might need to
 * extract data from the elements before reducing that data to a single value, such as
 * finding the sum of weights of a set of blocks.  This would require extracting the weight
 * from each block before summing up the weights.)
 *
 * <p>Of course, such operations can be readily implemented as simple sequential loops, as in:
 * <pre>{@code
 *    int sum = 0;
 *    for (int x : numbers) {
 *       sum += x;
 *    }
 * }</pre>
 * However, there may be a significant advantage to preferring a {@link java.util.stream.Stream#reduce reduce operation}
 * over a mutative accumulation such as the above -- a properly constructed reduce operation is
 * inherently parallelizable so long as the
 * {@link java.util.function.BinaryOperator reduction operaterator}
 * has the right characteristics. Specifically the operator must be
 * <a href="#Associativity">associative</a>.  For example, given a
 * stream of numbers for which we want to find the sum, we can write:
 * <pre>{@code
 *    int sum = numbers.reduce(0, (x,y) -> x+y);
 * }</pre>
 * or more succinctly:
 * <pre>{@code
 *    int sum = numbers.reduce(0, Integer::sum);
 * }</pre>
 *
 * <p>(The primitive specializations of {@link java.util.stream.Stream}, such as
 * {@link java.util.stream.IntStream}, even have convenience methods for common reductions,
 * such as {@link java.util.stream.IntStream#sum() sum} and {@link java.util.stream.IntStream#max() max},
 * which are implemented as simple wrappers around reduce.)
 *
 * <p>Reduction parallellizes well since the implementation of {@code reduce} can operate on
 * subsets of the stream in parallel, and then combine the intermediate results to get the final
 * correct answer.  Even if you were to use a parallelizable form of the
 * {@link java.util.stream.Stream#forEach(Consumer) forEach()} method
 * in place of the original for-each loop above, you would still have to provide thread-safe
 * updates to the shared accumulating variable {@code sum}, and the required synchronization
 * would likely eliminate any performance gain from parallelism. Using a {@code reduce} method
 * instead removes all of the burden of parallelizing the reduction operation, and the library
 * can provide an efficient parallel implementation with no additional synchronization needed.
 *
 * <p>The "blocks" examples shown earlier shows how reduction combines with other operations
 * to replace for loops with bulk operations.  If {@code blocks} is a collection of {@code Block}
 * objects, which have a {@code getWeight} method, we can find the heaviest block with:
 * <pre>{@code
 *     OptionalInt heaviest = blocks.stream()
 *                                  .mapToInt(Block::getWeight)
 *                                  .reduce(Integer::max);
 * }</pre>
 *
 * <p>In its more general form, a {@code reduce} operation on elements of type {@code <T>}
 * yielding a result of type {@code <U>} requires three parameters:
 * <pre>{@code
 * <U> U reduce(U identity,
 *              BiFunction<U, ? super T, U> accumlator,
 *              BinaryOperator<U> combiner);
 * }</pre>
 * Here, the <em>identity</em> element is both an initial seed for the reduction, and a default
 * result if there are no elements. The <em>accumulator</em> function takes a partial result and
 * the next element, and produce a new partial result. The <em>combiner</em> function combines
 * the partial results of two accumulators to produce a new partial result, and eventually the
 * final result.
 *
 * <p>This form is a generalization of the two-argument form, and is also a generalization of
 * the map-reduce construct illustrated above.  If we wanted to re-cast the simple {@code sum}
 * example using the more general form, {@code 0} would be the identity element, while
 * {@code Integer::sum} would be both the accumulator and combiner. For the sum-of-weights
 * example, this could be re-cast as:
 * <pre>{@code
 *     int sumOfWeights = blocks.stream().reduce(0,
 *                                               (sum, b) -> sum + b.getWeight())
 *                                               Integer::sum);
 * }</pre>
 * though the map-reduce form is more readable and generally preferable.  The generalized form
 * is provided for cases where significant work can be optimized away by combining mapping and
 * reducing into a single function.
 *
 * <p>More formally, the {@code identity} value must be an <em>identity</em> for the combiner
 * function. This means that for all {@code u}, {@code combiner.apply(identity, u)} is equal
 * to {@code u}. Additionally, the {@code combiner} function must be
 * <a href="#Associativity">associative</a> and must be compatible with the {@code accumulator}
 * function; for all {@code u} and {@code t}, the following must hold:
 * <pre>{@code
 *     combiner.apply(u, accumulator.apply(identity, t)) == accumulator.apply(u, t)
 * }</pre>
 *
 * <h3><a name="MutableReduction">Mutable Reduction</a></h3>
 *
 * A <em>mutable</em> reduction operation is similar to an ordinary reduction, in that it reduces
 * a stream of values to a single value, but instead of producing a distinct single-valued result, it
 * mutates a general <em>result container</em>, such as a {@code Collection} or {@code StringBuilder},
 * as it processes the elements in the stream.
 *
 * <p>For example, if we wanted to take a stream of strings and concatenate them into a single
 * long string, we <em>could</em> achieve this with ordinary reduction:
 * <pre>{@code
 *     String concatenated = strings.reduce("", String::concat)
 * }</pre>
 *
 * We would get the desired result, and it would even work in parallel.  However, we might not
 * be happy about the performance!  Such an implementation would do a great deal of string
 * copying, and the run time would be <em>O(n^2)</em> in the number of elements.  A more
 * performant approach would be to accumulate the results into a {@link java.lang.StringBuilder}, which
 * is a mutable container for accumulating strings.  We can use the same technique to
 * parallelize mutable reduction as we do with ordinary reduction.
 *
 * <p>The mutable reduction operation is called {@link java.util.stream.Stream#collect(Collector) collect()}, as it
 * collects together the desired results into a result container such as {@code StringBuilder}.
 * A {@code collect} operation requires three things: a factory function which will construct
 * new instances of the result container, an accumulating function that will update a result
 * container by incorporating a new element, and a combining function that can take two
 * result containers and merge their contents.  The form of this is very similar to the general
 * form of ordinary reduction:
 * <pre>{@code
 * <R> R collect(Supplier<R> resultFactory,
 *               BiConsumer<R, ? super T> accumulator,
 *               BiConsumer<R, R> combiner);
 * }</pre>
 * As with {@code reduce()}, the benefit of expressing {@code collect} in this abstract way is
 * that it is directly amenable to parallelization: we can accumulate partial results in parallel
 * and then combine them.  For example, to collect the String representations of the elements
 * in a stream into an {@code ArrayList}, we could write the obvious sequential for-each form:
 * <pre>{@code
 *     ArrayList<String> strings = new ArrayList<>();
 *     for (T element : stream) {
 *         strings.add(element.toString());
 *     }
 * }</pre>
 * Or we could use a parallelizable collect form:
 * <pre>{@code
 *     ArrayList<String> strings = stream.collect(() -> new ArrayList<>(),
 *                                                (c, e) -> c.add(e.toString()),
 *                                                (c1, c2) -> c1.addAll(c2));
 * }</pre>
 * or, noting that we have buried a mapping operation inside the accumulator function, more
 * succinctly as:
 * <pre>{@code
 *     ArrayList<String> strings = stream.map(Object::toString)
 *                                       .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
 * }</pre>
 * Here, our supplier is just the {@link java.util.ArrayList#ArrayList() ArrayList constructor}, the
 * accumulator adds the stringified element to an {@code ArrayList}, and the combiner simply
 * uses {@link java.util.ArrayList#addAll addAll} to copy the strings from one container into the other.
 *
 * <p>As with the regular reduction operation, the ability to parallelize only comes if an
 * <a href="package-summary.html#Associativity">associativity</a> condition is met. The {@code combiner} is associative
 * if for result containers {@code r1}, {@code r2}, and {@code r3}:
 * <pre>{@code
 *    combiner.accept(r1, r2);
 *    combiner.accept(r1, r3);
 * }</pre>
 * is equivalent to
 * <pre>{@code
 *    combiner.accept(r2, r3);
 *    combiner.accept(r1, r2);
 * }</pre>
 * where equivalence means that {@code r1} is left in the same state (according to the meaning
 * of {@link java.lang.Object#equals equals} for the element types). Similarly, the {@code resultFactory}
 * must act as an <em>identity</em> with respect to the {@code combiner} so that for any result
 * container {@code r}:
 * <pre>{@code
 *     combiner.accept(r, resultFactory.get());
 * }</pre>
 * does not modify the state of {@code r} (again according to the meaning of
 * {@link java.lang.Object#equals equals}). Finally, the {@code accumulator} and {@code combiner} must be
 * compatible such that for a result container {@code r} and element {@code t}:
 * <pre>{@code
 *    r2 = resultFactory.get();
 *    accumulator.accept(r2, t);
 *    combiner.accept(r, r2);
 * }</pre>
 * is equivalent to:
 * <pre>{@code
 *    accumulator.accept(r,t);
 * }</pre>
 * where equivalence means that {@code r} is left in the same state (again according to the
 * meaning of {@link java.lang.Object#equals equals}).
 *
 * <p> The three aspects of {@code collect}: supplier, accumulator, and combiner, are often very
 * tightly coupled, and it is convenient to introduce the notion of a {@link java.util.stream.Collector} as
 * being an object that embodies all three aspects. There is a {@link java.util.stream.Stream#collect(Collector) collect}
 * method that simply takes a {@code Collector} and returns the resulting container.
 * The above example for collecting strings into a {@code List} can be rewritten using a
 * standard {@code Collector} as:
 * <pre>{@code
 *     ArrayList<String> strings = stream.map(Object::toString)
 *                                       .collect(Collectors.toList());
 * }</pre>
 *
 * <h3><a name="ConcurrentReduction">Reduction, Concurrency, and Ordering</a></h3>
 *
 * With some complex reduction operations, for example a collect that produces a
 * {@code Map}, such as:
 * <pre>{@code
 *     Map<Buyer, List<Transaction>> salesByBuyer
 *         = txns.parallelStream()
 *               .collect(Collectors.groupingBy(Transaction::getBuyer));
 * }</pre>
 * (where {@link java.util.stream.Collectors#groupingBy} is a utility function
 * that returns a {@link java.util.stream.Collector} for grouping sets of elements based on some key)
 * it may actually be counterproductive to perform the operation in parallel.
 * This is because the combining step (merging one {@code Map} into another by key)
 * can be expensive for some {@code Map} implementations.
 *
 * <p>Suppose, however, that the result container used in this reduction
 * was a concurrently modifiable collection -- such as a
 * {@link java.util.concurrent.ConcurrentHashMap ConcurrentHashMap}. In that case,
 * the parallel invocations of the accumulator could actually deposit their results
 * concurrently into the same shared result container, eliminating the need for the combiner to
 * merge distinct result containers. This potentially provides a boost
 * to the parallel execution performance. We call this a <em>concurrent</em> reduction.
 *
 * <p>A {@link java.util.stream.Collector} that supports concurrent reduction is marked with the
 * {@link java.util.stream.Collector.Characteristics#CONCURRENT} characteristic.
 * Having a concurrent collector is a necessary condition for performing a
 * concurrent reduction, but that alone is not sufficient. If you imagine multiple
 * accumulators depositing results into a shared container, the order in which
 * results are deposited is non-deterministic. Consequently, a concurrent reduction
 * is only possible if ordering is not important for the stream being processed.
 * The {@link java.util.stream.Stream#collect(Collector)}
 * implementation will only perform a concurrent reduction if
 * <ul>
 * <li>The stream is parallel;</li>
 * <li>The collector has the
 * {@link java.util.stream.Collector.Characteristics#CONCURRENT} characteristic,
 * and;</li>
 * <li>Either the stream is unordered, or the collector has the
 * {@link java.util.stream.Collector.Characteristics#UNORDERED} characteristic.
 * </ul>
 * For example:
 * <pre>{@code
 *     Map<Buyer, List<Transaction>> salesByBuyer
 *         = txns.parallelStream()
 *               .unordered()
 *               .collect(groupingByConcurrent(Transaction::getBuyer));
 * }</pre>
 * (where {@link java.util.stream.Collectors#groupingByConcurrent} is the concurrent companion
 * to {@code groupingBy}).
 *
 * <p>Note that if it is important that the elements for a given key appear in the
 * order they appear in the source, then we cannot use a concurrent reduction,
 * as ordering is one of the casualties of concurrent insertion.  We would then
 * be constrained to implement either a sequential reduction or a merge-based
 * parallel reduction.
 *
 * <h2><a name="Associativity">Associativity</a></h2>
 *
 * An operator or function {@code op} is <em>associative</em> if the following holds:
 * <pre>{@code
 *     (a op b) op c == a op (b op c)
 * }</pre>
 * The importance of this to parallel evaluation can be seen if we expand this to four terms:
 * <pre>{@code
 *     a op b op c op d == (a op b) op (c op d)
 * }</pre>
 * So we can evaluate {@code (a op b)} in parallel with {@code (c op d)} and then invoke {@code op} on
 * the results.
 * TODO what does associative mean for mutative combining functions?
 * FIXME: we described mutative associativity above.
 *
 * <h2><a name="StreamSources">Stream sources</a></h2>
 * TODO where does this section go?
 *
 * XXX - change to section to stream construction gradually introducing more
 *       complex ways to construct
 *     - construction from Collection
 *     - construction from Iterator
 *     - construction from array
 *     - construction from generators
 *     - construction from spliterator
 *
 * XXX - the following is quite low-level but important aspect of stream constriction
 *
 * <p>A pipeline is initially constructed from a spliterator (see {@link java.util.Spliterator}) supplied by a stream source.
 * The spliterator covers elements of the source and provides element traversal operations
 * for a possibly-parallel computation.  See methods on {@link java.util.stream.Streams} for construction
 * of pipelines using spliterators.
 *
 * <p>A source may directly supply a spliterator.  If so, the spliterator is traversed, split, or queried
 * for estimated size after, and never before, the terminal operation commences. It is strongly recommended
 * that the spliterator report a characteristic of {@code IMMUTABLE} or {@code CONCURRENT}, or be
 * <em>late-binding</em> and not bind to the elements it covers until traversed, split or queried for
 * estimated size.
 *
 * <p>If a source cannot directly supply a recommended spliterator then it may indirectly supply a spliterator
 * using a {@code Supplier}.  The spliterator is obtained from the supplier after, and never before, the terminal
 * operation of the stream pipeline commences.
 *
 * <p>Such requirements significantly reduce the scope of potential interference to the interval starting
 * with the commencing of the terminal operation and ending with the producing a result or side-effect.  See
 * <a href="package-summary.html#Non-Interference">Non-Interference</a> for
 * more details.
 *
 * XXX - move the following to the non-interference section
 *
 * <p>A source can be modified before the terminal operation commences and those modifications will be reflected in
 * the covered elements.  Afterwards, and depending on the properties of the source, further modifications
 * might not be reflected and the throwing of a {@code ConcurrentModificationException} may occur.
 *
 * <p>For example, consider the following code:
 * <pre>{@code
 *     List<String> l = new ArrayList(Arrays.asList("one", "two"));
 *     Stream<String> sl = l.stream();
 *     l.add("three");
 *     String s = sl.collect(joining(" "));
 * }</pre>
 * First a list is created consisting of two strings: "one"; and "two". Then a stream is created from that list.
 * Next the list is modified by adding a third string: "three".  Finally the elements of the stream are collected
 * and joined together.  Since the list was modified before the terminal {@code collect} operation commenced
 * the result will be a string of "one two three". However, if the list is modified after the terminal operation
 * commences, as in:
 * <pre>{@code
 *     List<String> l = new ArrayList(Arrays.asList("one", "two"));
 *     Stream<String> sl = l.stream();
 *     String s = sl.peek(s -> l.add("BAD LAMBDA")).collect(joining(" "));
 * }</pre>
 * then a {@code ConcurrentModificationException} will be thrown since the {@code peek} operation will attempt
 * to add the string "BAD LAMBDA" to the list after the terminal operation has commenced.
 */

package java.util.stream;
