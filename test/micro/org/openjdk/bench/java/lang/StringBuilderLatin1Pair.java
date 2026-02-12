/*
 * Copyright (c) 2026, Alibaba Group Holding Limited. All Rights Reserved.
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
 */

/*
 * Simple standalone benchmark to demonstrate the performance benefit
 * of combining consecutive Latin1 char appends.
 * 
 * Run with: java StringBuilderLatin1PairBenchmark.java
 */
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

public class StringBuilderLatin1PairBenchmark {

    // Number of iterations for warmup and measurement
    private static final int WARMUP_ITERATIONS = 100_000;
    private static final int MEASUREMENT_ITERATIONS = 1_000_000;

    public static void main(String[] args) throws Exception {
        System.out.println("StringBuilder Latin1 Char Pair Optimization Benchmark");
        System.out.println("=".repeat(60));
        System.out.println("Java version: " + System.getProperty("java.version"));
        System.out.println("JVM: " + System.getProperty("java.vm.name"));
        System.out.println();

        // Warmup
        System.out.println("Warming up (" + WARMUP_ITERATIONS + " iterations)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            baselineTwoAppends();
            optimizedAppendLatin1();
            baselineFourAppends();
            optimizedTwoAppendLatin1();
        }
        System.out.println("Warmup complete.\n");

        // Measurement
        System.out.println("Measurement results (" + MEASUREMENT_ITERATIONS + " iterations):");
        System.out.println("-".repeat(60));

        // Test 1: Two char appends
        long time1 = measure("baseline: Two append(char)", 
                             StringBuilderLatin1PairBenchmark::baselineTwoAppends);
        long time2 = measure("optimized: appendLatin1(c1,c2)", 
                             StringBuilderLatin1PairBenchmark::optimizedAppendLatin1);
        printComparison(time1, time2);

        // Test 2: Four char appends
        long time3 = measure("baseline: Four append(char)", 
                             StringBuilderLatin1PairBenchmark::baselineFourAppends);
        long time4 = measure("optimized: Two appendLatin1", 
                             StringBuilderLatin1PairBenchmark::optimizedTwoAppendLatin1);
        printComparison(time3, time4);

        // Test 3: Eight char appends
        long time5 = measure("baseline: Eight append(char)", 
                             StringBuilderLatin1PairBenchmark::baselineEightAppends);
        long time6 = measure("optimized: Four appendLatin1", 
                             StringBuilderLatin1PairBenchmark::optimizedFourAppendLatin1);
        printComparison(time5, time6);

        // Test 4: With existing content
        long time7 = measure("baseline: String + two append(char)", 
                             StringBuilderLatin1PairBenchmark::baselineStringPlusTwoAppends);
        long time8 = measure("optimized: String + appendLatin1", 
                             StringBuilderLatin1PairBenchmark::optimizedStringPlusAppendLatin1);
        printComparison(time7, time8);

        // Verify correctness
        System.out.println("-".repeat(60));
        System.out.println("Correctness verification:");
        String r1 = baselineTwoAppends();
        String r2 = optimizedAppendLatin1();
        System.out.println("  baseline result: \"" + r1 + "\"");
        System.out.println("  optimized result: \"" + r2 + "\"");
        System.out.println("  Results match: " + r1.equals(r2));

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Summary:");
        System.out.println("  The appendLatin1(char, char) method reduces:");
        System.out.println("    - Method call overhead (2 calls -> 1 call)");
        System.out.println("    - Capacity checks (2 checks -> 1 check)");
        System.out.println("    - Field writes (count field updated once instead of twice)");
    }

    // Baseline: Two consecutive append(char) calls
    public static String baselineTwoAppends() {
        StringBuilder sb = new StringBuilder();
        sb.append('a');
        sb.append('b');
        return sb.toString();
    }

    // Optimized: Single appendLatin1 call (via reflection to simulate)
    public static String optimizedAppendLatin1() {
        StringBuilder sb = new StringBuilder();
        appendLatin1(sb, 'a', 'b');
        return sb.toString();
    }

    // Baseline: Four consecutive append(char) calls
    public static String baselineFourAppends() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b').append('c').append('d');
        return sb.toString();
    }

    // Optimized: Two appendLatin1 calls
    public static String optimizedTwoAppendLatin1() {
        StringBuilder sb = new StringBuilder();
        appendLatin1(sb, 'a', 'b');
        appendLatin1(sb, 'c', 'd');
        return sb.toString();
    }

    // Baseline: Eight consecutive append(char) calls
    public static String baselineEightAppends() {
        StringBuilder sb = new StringBuilder();
        sb.append('a').append('b').append('c').append('d');
        sb.append('e').append('f').append('g').append('h');
        return sb.toString();
    }

    // Optimized: Four appendLatin1 calls
    public static String optimizedFourAppendLatin1() {
        StringBuilder sb = new StringBuilder();
        appendLatin1(sb, 'a', 'b');
        appendLatin1(sb, 'c', 'd');
        appendLatin1(sb, 'e', 'f');
        appendLatin1(sb, 'g', 'h');
        return sb.toString();
    }

    // Baseline: String + two append(char)
    public static String baselineStringPlusTwoAppends() {
        StringBuilder sb = new StringBuilder();
        sb.append("prefix");
        sb.append('a');
        sb.append('b');
        return sb.toString();
    }

    // Optimized: String + appendLatin1
    public static String optimizedStringPlusAppendLatin1() {
        StringBuilder sb = new StringBuilder();
        sb.append("prefix");
        appendLatin1(sb, 'a', 'b');
        return sb.toString();
    }

    // Helper method to invoke appendLatin1 via reflection
    private static final Method APPEND_LATIN1_METHOD;
    static {
        try {
            APPEND_LATIN1_METHOD = StringBuilder.class.getSuperclass()
                .getDeclaredMethod("appendLatin1", char.class, char.class);
            APPEND_LATIN1_METHOD.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void appendLatin1(StringBuilder sb, char c1, char c2) {
        try {
            APPEND_LATIN1_METHOD.invoke(sb, c1, c2);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Measurement helper
    @FunctionalInterface
    interface BenchmarkTask {
        String run();
    }

    private static long measure(String name, BenchmarkTask task) {
        // Force GC before measurement
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}

        long startTime = System.nanoTime();
        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            task.run();
        }
        long endTime = System.nanoTime();
        long totalTimeNs = endTime - startTime;
        long avgTimeNs = totalTimeNs / MEASUREMENT_ITERATIONS;

        System.out.printf("  %-35s: %6d ns/op%n", name, avgTimeNs);
        return avgTimeNs;
    }

    private static void printComparison(long baseline, long optimized) {
        double improvement = ((double)(baseline - optimized) / baseline) * 100;
        System.out.printf("    -> Improvement: %.1f%%%n", improvement);
    }
}
