package org.openjdk.bench.java.util.stream;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Benchmark for checking that having mixed types for the
 * streams does not make performance worse. We test this by
 * having collections of differing lengths within each run,
 * with different percentage of empty collections, ranging
 * from 0% to 100%. We also pre-create all the Function
 * instances necessary for creating the streams and to
 * decorate them.
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 1)
@Warmup(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 3, timeUnit = TimeUnit.SECONDS)
@State(Scope.Thread)
public class EmptyStreamsMixedLength {
    @Param({"20", "50", "80", "90", "99", "1", "10"})
    private int a_percentageOfEmptyStreams;

    @Param({"ArrayList", "ConcurrentLinkedQueue", "ConcurrentSkipListSet", "CopyOnWriteArrayList", "ConcurrentHashMap"})
    private String b_typeOfCollection;

    @Param({"minimal", "basic", "complex", "crossover"})
    private String c_typeOfStreamDecoration;

    @Param({"old", "new", "guarded"})
    private String d_streamCreation;

    protected static final int NUMBER_OF_COLLECTIONS = 16 * 1024;
    private List<Collection<Integer>> collections;
    private int length_pos;
    private Function<Collection<Integer>, Stream<Integer>> streamCreator;
    private Function<Stream<Integer>, Optional<Integer>> streamDecorator;

    @Setup
    public void setupExperiment() {
        System.out.println("EmptyStreamsMixedLength.setupExperiment");
        collections = new ArrayList<>();
        length_pos = 0;
        var collectionSupplier = makeCollectionSupplier();
        List<Integer> lengths = new ArrayList<>();
        for (int i = 0; i < NUMBER_OF_COLLECTIONS; i++) {
            var collection = collectionSupplier.get();
            if (i >= NUMBER_OF_COLLECTIONS * a_percentageOfEmptyStreams / 100) {
                int numberOfElements = ThreadLocalRandom.current().nextInt(1, 11);
                ThreadLocalRandom.current().ints(numberOfElements).forEach(collection::add);
            }
            collections.add(collection);
        }
        Collections.shuffle(collections, new Random(42));
        var lengthDistribution = collections.stream()
                .collect(Collectors.groupingBy(Collection::size, TreeMap::new, Collectors.counting()));
        var empties = lengthDistribution.getOrDefault(0, 0L);
        System.out.printf("%d/%d (%.0f%%) are empty, lengthDistribution = %s%n",
                empties,
                NUMBER_OF_COLLECTIONS,
                100.0 * empties / NUMBER_OF_COLLECTIONS,
                lengthDistribution);
        this.streamCreator = makeStreamCreator();
        this.streamDecorator = makeStreamDecorator();
    }

    @TearDown
    public void cleanupExperiment() {
        collections = null;
        streamCreator = null;
        streamDecorator = null;
    }

    private Supplier<Collection<Integer>> makeCollectionSupplier() {
        return switch (b_typeOfCollection) {
            case "ArrayList" -> ArrayList::new;
            case "ConcurrentLinkedQueue" -> ConcurrentLinkedQueue::new;
            case "ConcurrentSkipListSet" -> ConcurrentSkipListSet::new;
            case "CopyOnWriteArrayList" -> CopyOnWriteArrayList::new;
            case "ConcurrentHashMap" -> ConcurrentHashMap::newKeySet;
            default -> throw new AssertionError("Incorrect collection type : " + b_typeOfCollection);
        };
    }

    private Function<Collection<Integer>, Stream<Integer>> makeStreamCreator() {
        return collection ->
                switch (d_streamCreation) {
                    case "old" -> createOld(collection);
                    case "new" -> createNew(collection);
                    case "guarded" -> collection.isEmpty() ? null : createOld(collection);
                    default -> throw new AssertionError("Unknown stream creation : " + d_streamCreation);
                };
    }

    private Function<Stream<Integer>, Optional<Integer>> makeStreamDecorator() {
        return switch (c_typeOfStreamDecoration) {
            // with no additional intermediate operations
            case "minimal" -> stream ->
                    stream.max(Integer::compare);
            // basic filter/map
            case "basic" -> stream ->
                    stream
                            .filter(Objects::nonNull)
                            .map(Function.identity())
                            .max(Integer::compare);
            // more complex, including sorted and distinct
            case "complex" -> stream ->
                    stream
                            .filter(Objects::nonNull)
                            .map(Function.identity())
                            .filter(Objects::nonNull)
                            .sorted() // causes it to change to old empty stream
                            .distinct()
                            .max(Integer::compare);
            // we crossover from an Object Stream to an IntStream, LongStream,
            // etc. but always with operations that keep using the empty streams
            case "crossover" -> stream ->
                    stream
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
                            .max(Integer::compare);
            default -> throw new AssertionError("Unknown stream decoration: " + c_typeOfStreamDecoration);
        };
    }

    private Stream<Integer> createOld(Collection<Integer> collection) {
        return StreamSupport.stream(collection.spliterator(), false);
    }

    private Stream<Integer> createNew(Collection<Integer> collection) {
        if (collection.isEmpty()) return StreamSupport.emptyStream(collection.spliterator());
        else return createOld(collection);
    }

    private Collection<Integer> nextCollection() {
        int index = length_pos++;
        if (length_pos == NUMBER_OF_COLLECTIONS) length_pos = 0;
        return collections.get(index);
    }

    @Benchmark
    public void mixOfCollectionsAndSizeMixesAndStreams() {
        var collection = nextCollection();
        var stream = streamCreator.apply(collection);
        Optional<Integer> max = stream != null ? streamDecorator.apply(stream) : Optional.empty();
    }
}