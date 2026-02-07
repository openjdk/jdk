package org.openjdk.bench.java.util;

import org.openjdk.jmh.annotations.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark comparing HashMap constructor performance against manual iteration.
 *
 * Tests HashMap.<init>(Map) performance across different source map types, with and without
 * call site poisoning to simulate real-world megamorphic conditions.
 *
 * The setup poisons polymorphic call sites by using five different map types
 * in both the constructor and manual iteration patterns to ensure megamorphic behavior.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = {"-XX:+UseParallelGC", "-Xmx3g"})
public class HashMapConstructorBenchmark {

    private static final int POISON_ITERATIONS = 40000;

    @Param({"0", "5", "25"})
    private int mapSize;

    @Param({"true", "false"})
    private boolean poisonCallSites;

    @Param({"HashMap", "TreeMap", "ConcurrentHashMap", "UnmodifiableMap(HashMap)", "UnmodifiableMap(TreeMap)"})
    private String inputType;

    private HashMap<String, Integer> inputHashMap;
    private TreeMap<String, Integer> inputTreeMap;
    private LinkedHashMap<String, Integer> inputLinkedHashMap;
    private ConcurrentHashMap<String, Integer> inputConcurrentHashMap;
    private WeakHashMap<String, Integer> inputWeakHashMap;
    private Map<String, Integer> inputUnmodifiableMap;
    private Map<String, Integer> inputUnmodifiableTreeMap;

    private Map<String, Integer> sourceMap;

    @Setup(Level.Trial)
    public void setup() {
        // Create test data with identical contents
        inputHashMap = new HashMap<>();
        inputTreeMap = new TreeMap<>();
        inputLinkedHashMap = new LinkedHashMap<>();
        inputConcurrentHashMap = new ConcurrentHashMap<>();
        inputWeakHashMap = new WeakHashMap<>();

        for (int i = 0; i < mapSize; i++) {
            String key = "key" + i;
            Integer value = i;
            inputHashMap.put(key, value);
            inputTreeMap.put(key, value);
            inputLinkedHashMap.put(key, value);
            inputConcurrentHashMap.put(key, value);
            inputWeakHashMap.put(key, value);
        }

        // Create wrapper maps for poisoning
        inputUnmodifiableMap = Collections.unmodifiableMap(new HashMap<>(inputHashMap));
        inputUnmodifiableTreeMap = Collections.unmodifiableMap(new TreeMap<>(inputTreeMap));

        // Set source map based on inputType parameter
        sourceMap = switch (inputType) {
            case "HashMap" -> inputHashMap;
            case "TreeMap" -> inputTreeMap;
            case "ConcurrentHashMap" -> inputConcurrentHashMap;
            case "UnmodifiableMap(HashMap)" -> inputUnmodifiableMap;
            case "UnmodifiableMap(TreeMap)" -> inputUnmodifiableTreeMap;
            default -> throw new IllegalArgumentException("Unknown inputType: " + inputType);
        };

        if (poisonCallSites) {
            poisonCallSites();
        }
    }

    private void poisonCallSites() {
        @SuppressWarnings("unchecked")
        Map<String, Integer>[] sources = new Map[] { inputHashMap, inputTreeMap, inputLinkedHashMap,
                inputConcurrentHashMap, inputWeakHashMap };

        // Poison HashMap.<init>(Map) call site
        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<String, Integer> source = sources[i % sources.length];
            HashMap<String, Integer> temp = new HashMap<>(source);
            if (temp.size() != mapSize)
                throw new RuntimeException();
        }

        // Poison entrySet iteration call sites
        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<String, Integer> source = sources[i % sources.length];
            HashMap<String, Integer> temp = new HashMap<>(source.size());
            for (Map.Entry<String, Integer> entry : source.entrySet()) {
                temp.put(entry.getKey(), entry.getValue());
            }
            if (temp.size() != mapSize)
                throw new RuntimeException();
        }

        // Poison UnmodifiableMap call sites
        @SuppressWarnings("unchecked")
        Map<String, Integer>[] umSources = new Map[]{
            Collections.unmodifiableMap(inputHashMap),
            Collections.unmodifiableMap(inputTreeMap),
            Collections.unmodifiableMap(inputLinkedHashMap),
            Collections.unmodifiableMap(inputConcurrentHashMap),
            Collections.unmodifiableMap(inputWeakHashMap)
        };

        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<String, Integer> source = umSources[i % umSources.length];
            HashMap<String, Integer> temp = new HashMap<>(source);
            if (temp.size() != mapSize)
                throw new RuntimeException();
        }

        for (int i = 0; i < POISON_ITERATIONS; i++) {
            Map<String, Integer> source = umSources[i % umSources.length];
            HashMap<String, Integer> temp = new HashMap<>(source.size());
            for (Map.Entry<String, Integer> entry : source.entrySet()) {
                temp.put(entry.getKey(), entry.getValue());
            }
            if (temp.size() != mapSize) throw new RuntimeException();
        }
    }

    /**
     * Benchmark using HashMap's built-in constructor that takes a Map parameter.
     * Performance varies based on source map type and call site polymorphism.
     */
    @Benchmark
    public HashMap<String, Integer> hashMapConstructor() {
        return new HashMap<>(sourceMap);
    }

    /**
     * Benchmark using manual iteration over entrySet with individual put() calls.
     * This approach bypasses bulk operations and their polymorphic call sites.
     */
    @Benchmark
    public HashMap<String, Integer> manualEntrySetLoop() {
        HashMap<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : sourceMap.entrySet()) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
