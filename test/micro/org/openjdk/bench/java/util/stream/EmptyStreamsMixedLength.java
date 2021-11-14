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
import java.util.Random;
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
 * Benchmark for checking that having mixed types for the 
 * streams does not make performance worse. We test this by
 * having collections of length 0 .. 3.
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2)//, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xmx16g", "-Xms16g", "-XX:+AlwaysPreTouch", "-XX:NewRatio=1", "-XX:SurvivorRatio=1"})
@Warmup(iterations = 20, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class EmptyStreamsMixedLength {
    private static final int MAXIMUM_STREAM_LENGTH = 3;
    private static byte[] lengths = new byte[64 * 1024];
    static {
        int[] ints = new Random(0).ints(lengths.length, 0, MAXIMUM_STREAM_LENGTH + 1)
                .toArray();
        for (int i = 0; i < lengths.length; i++) {
            lengths[i] = (byte) ints[i];
        }
    }

    private int length_pos = 0;

    private int nextLength() {
        int nextIndex = length_pos++;
        if (length_pos == lengths.length) length_pos = 0;
        return lengths[nextIndex];
    }

    @Param({"ArrayList", "ConcurrentLinkedQueue", "ConcurrentSkipListSet", "CopyOnWriteArrayList", "ConcurrentHashMap"})
    private String a_typeOfCollection;

    @Param({"minimal", "basic", "complex", "crossover"})
    private String b_typeOfStreamDecoration;

    @Param({"old", "new"})
    private String c_streamCreation;

    private static final Map<Integer/*length*/, Map<String/*typeOfCollection*/, Collection<Integer>>> collectionData =
            Map.ofEntries(
                    makeMapEntries(0),
                    makeMapEntries(1),
                    makeMapEntries(2),
                    makeMapEntries(3)
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

    private Stream<Integer> createStream() {
        Collection<Integer> collection = collectionData.get(nextLength()).get(a_typeOfCollection);
        return switch (c_streamCreation) {
            case "old" -> StreamSupport.stream(collection.spliterator(), false);
            case "new" -> collection.stream();
            default -> throw new AssertionError();
        };
    }

    private Optional<Integer> decorateStream(Stream<Integer> stream) {
        return streamDecorators.get(b_typeOfStreamDecoration).apply(stream);
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
        decorateStream(createStream());
    }
}