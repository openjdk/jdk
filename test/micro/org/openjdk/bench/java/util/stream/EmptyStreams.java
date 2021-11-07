/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Spliterators;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Benchmark for checking that the new empty stream
 * implementations are faster than the old way of creating
 * empty streams from empty spliterators.
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Fork(3)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class EmptyStreams {
    @Param({"0", "1", "10", "100"})
    private int length;

    @Param({"minimal", "basic", "complex", "crossover"})
    private String typeOfStreamDecoration;

    @Param({"ArrayList", "ConcurrentLinkedQueue", "ConcurrentSkipListSet", "CopyOnWriteArrayList", "ConcurrentHashMap"})
    private String typeOfCollection;

    private static final Map<Integer/*length*/, Map<String/*typeOfCollection*/, Collection<Integer>>> collectionData =
            Map.ofEntries(
                    makeMapEntries(0),
                    makeMapEntries(1),
                    makeMapEntries(3),
                    makeMapEntries(5),
                    makeMapEntries(10),
                    makeMapEntries(20),
                    makeMapEntries(100)
            );

    private static Map.Entry<Integer, Map<String, Collection<Integer>>> makeMapEntries(int length) {
        return Map.entry(length, Map.of(
                "ArrayList", makeCollection(length, ArrayList::new),
                "ConcurrentLinkedQueue", makeCollection(length, ConcurrentLinkedQueue::new),
                "ConcurrentSkipListSet", makeCollection(length, ConcurrentSkipListSet::new),
                "CopyOnWriteArrayList", makeCollection(length, CopyOnWriteArrayList::new),
                "ConcurrentHashMap", makeCollection(length, ConcurrentHashMap::newKeySet)
        ));
    }

    private static Collection<Integer> makeCollection(int length, Supplier<Collection<Integer>> supplier) {
        return IntStream.range(0, length).boxed().collect(Collectors.toCollection(supplier));
    }

    private static Stream<Integer> createStream(int length, String typeOfCollection) {
        return collectionData.get(length).get(typeOfCollection).stream();
    }

    private static Optional<Integer> decorateStream(Stream<Integer> stream, String typeOfStreamDecoration) {
        return streamDecorators.get(typeOfStreamDecoration).apply(stream);
    }

    private static final Map<String, Function<Stream<Integer>, Optional<Integer>>> streamDecorators =
            Map.of(
                    // with no additional intermediate operations
                    "minimal", stream ->
                            stream.max(Integer::compare),
                    // basic filter/map
                    "basic", stream -> stream
                            .filter(Objects::nonNull)
                            .map(Function.identity())
                            .max(Integer::compare),
                    // more complex, including sorted and distinct, both of which
                    // would cause a fallback to the old empty stream approach
                    "complex", stream -> stream
                            .filter(Objects::nonNull)
                            .map(Function.identity())
                            .filter(Objects::nonNull)
                            .sorted() // causes it to change to old empty stream
                            .distinct()
                            .max(Integer::compare),
                    // we crossover from an Object Stream to an IntStream, LongStream,
                    // etc. but always with operations that keep using the empty streams
                    "crossover", stream -> stream
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .filter(s -> s.length() > 0)
                            .mapToInt(Integer::parseInt)
                            .map(i -> i * 2)
                            .mapToLong(i -> i + 1000)
                            .mapToDouble(i -> i * 3.5)
                            .boxed()
                            .mapToLong(Double::intValue)
                            .mapToInt(d -> (int) d)
                            .boxed()
                            .max(Integer::compare)
            );

    private static OptionalInt addStreamFilters(Stream<String> stream) {
        return stream
                .filter(Objects::nonNull)
                .filter(s -> s.length() > 0)
                .mapToInt(Integer::parseInt)
                .map(i -> i * 2)
                .mapToLong(i -> i + 1000)
                .mapToDouble(i -> i * 3.5)
                .boxed()
                .mapToLong(Double::intValue)
                .mapToInt(d -> (int) d)
                .max();
    }

    @Benchmark
    public void mixOfCollectionsAndSizesAndStreams() {
        decorateStream(createStream(length, typeOfCollection), typeOfStreamDecoration);
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamSupport() {
        return addStreamFilters(
                StreamSupport.stream(
                        Spliterators.emptySpliterator(), false));
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamEmpty() {
        return addStreamFilters(Stream.empty());
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithStreamOf() {
        return addStreamFilters(Stream.of());
    }

    @Benchmark
    public OptionalInt emptyStreamCreatedWithArrayListStream() {
        return addStreamFilters(new ArrayList<String>().stream());
    }
}