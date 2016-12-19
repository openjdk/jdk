/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @summary micro-benchmark correctness mode
 * @run main RemoveMicroBenchmark iterations=1 size=8 warmup=0
 */

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.function.Supplier;

/**
 * Usage: [iterations=N] [size=N] [filter=REGEXP] [warmup=SECONDS]
 *
 * To run this in micro-benchmark mode, simply run as a normal java program.
 * Be patient; this program runs for a very long time.
 * For faster runs, restrict execution using command line args.
 *
 * @author Martin Buchholz
 */
public class RemoveMicroBenchmark {
    abstract static class Job {
        private final String name;
        public Job(String name) { this.name = name; }
        public String name() { return name; }
        public abstract void work() throws Throwable;
    }

    final int iterations;
    final int size;             // number of elements in collections
    final double warmupSeconds;
    final long warmupNanos;
    final Pattern filter;       // select subset of Jobs to run
    final boolean reverse;      // reverse order of Jobs
    final boolean shuffle;      // randomize order of Jobs

    RemoveMicroBenchmark(String[] args) {
        iterations    = intArg(args, "iterations", 10_000);
        size          = intArg(args, "size", 1000);
        warmupSeconds = doubleArg(args, "warmup", 7.0);
        filter        = patternArg(args, "filter");
        reverse       = booleanArg(args, "reverse");
        shuffle       = booleanArg(args, "shuffle");

        warmupNanos = (long) (warmupSeconds * (1000L * 1000L * 1000L));
    }

    // --------------- GC finalization infrastructure ---------------

    /** No guarantees, but effective in practice. */
    static void forceFullGc() {
        CountDownLatch finalizeDone = new CountDownLatch(1);
        WeakReference<?> ref = new WeakReference<Object>(new Object() {
            protected void finalize() { finalizeDone.countDown(); }});
        try {
            for (int i = 0; i < 10; i++) {
                System.gc();
                if (finalizeDone.await(1L, TimeUnit.SECONDS) && ref.get() == null) {
                    System.runFinalization(); // try to pick up stragglers
                    return;
                }
            }
        } catch (InterruptedException unexpected) {
            throw new AssertionError("unexpected InterruptedException");
        }
        throw new AssertionError("failed to do a \"full\" gc");
    }

    /**
     * Runs each job for long enough that all the runtime compilers
     * have had plenty of time to warm up, i.e. get around to
     * compiling everything worth compiling.
     * Returns array of average times per job per run.
     */
    long[] time0(List<Job> jobs) throws Throwable {
        final int size = jobs.size();
        long[] nanoss = new long[size];
        for (int i = 0; i < size; i++) {
            if (warmupNanos > 0) forceFullGc();
            Job job = jobs.get(i);
            long totalTime;
            int runs = 0;
            long startTime = System.nanoTime();
            do { job.work(); runs++; }
            while ((totalTime = System.nanoTime() - startTime) < warmupNanos);
            nanoss[i] = totalTime/runs;
        }
        return nanoss;
    }

    void time(List<Job> jobs) throws Throwable {
        if (warmupNanos > 0) time0(jobs); // Warm up run
        final int size = jobs.size();
        final long[] nanoss = time0(jobs); // Real timing run
        final long[] milliss = new long[size];
        final double[] ratios = new double[size];

        final String nameHeader   = "Method";
        final String millisHeader = "Millis";
        final String ratioHeader  = "Ratio";

        int nameWidth   = nameHeader.length();
        int millisWidth = millisHeader.length();
        int ratioWidth  = ratioHeader.length();

        for (int i = 0; i < size; i++) {
            nameWidth = Math.max(nameWidth, jobs.get(i).name().length());

            milliss[i] = nanoss[i]/(1000L * 1000L);
            millisWidth = Math.max(millisWidth,
                                   String.format("%d", milliss[i]).length());

            ratios[i] = (double) nanoss[i] / (double) nanoss[0];
            ratioWidth = Math.max(ratioWidth,
                                  String.format("%.3f", ratios[i]).length());
        }

        String format = String.format("%%-%ds %%%dd %%%d.3f%%n",
                                      nameWidth, millisWidth, ratioWidth);
        String headerFormat = String.format("%%-%ds %%%ds %%%ds%%n",
                                            nameWidth, millisWidth, ratioWidth);
        System.out.printf(headerFormat, "Method", "Millis", "Ratio");

        // Print out absolute and relative times, calibrated against first job
        for (int i = 0; i < size; i++)
            System.out.printf(format, jobs.get(i).name(), milliss[i], ratios[i]);
    }

    private static String keywordValue(String[] args, String keyword) {
        for (String arg : args)
            if (arg.startsWith(keyword))
                return arg.substring(keyword.length() + 1);
        return null;
    }

    private static int intArg(String[] args, String keyword, int defaultValue) {
        String val = keywordValue(args, keyword);
        return (val == null) ? defaultValue : Integer.parseInt(val);
    }

    private static double doubleArg(String[] args, String keyword, double defaultValue) {
        String val = keywordValue(args, keyword);
        return (val == null) ? defaultValue : Double.parseDouble(val);
    }

    private static Pattern patternArg(String[] args, String keyword) {
        String val = keywordValue(args, keyword);
        return (val == null) ? null : Pattern.compile(val);
    }

    private static boolean booleanArg(String[] args, String keyword) {
        String val = keywordValue(args, keyword);
        if (val == null || val.equals("false")) return false;
        if (val.equals("true")) return true;
        throw new IllegalArgumentException(val);
    }

    private static List<Job> filter(Pattern filter, List<Job> jobs) {
        if (filter == null) return jobs;
        ArrayList<Job> newJobs = new ArrayList<>();
        for (Job job : jobs)
            if (filter.matcher(job.name()).find())
                newJobs.add(job);
        return newJobs;
    }

    private static void deoptimize(int sum) {
        if (sum == 42)
            System.out.println("the answer");
    }

    private static <T> List<T> asSubList(List<T> list) {
        return list.subList(0, list.size());
    }

    private static <T> Iterable<T> backwards(final List<T> list) {
        return new Iterable<T>() {
            public Iterator<T> iterator() {
                return new Iterator<T>() {
                    final ListIterator<T> it = list.listIterator(list.size());
                    public boolean hasNext() { return it.hasPrevious(); }
                    public T next()          { return it.previous(); }
                    public void remove()     {        it.remove(); }};}};
    }

    // Checks for correctness *and* prevents loop optimizations
    class Check {
        private int sum;
        public void sum(int sum) {
            if (this.sum == 0)
                this.sum = sum;
            if (this.sum != sum)
                throw new AssertionError("Sum mismatch");
        }
    }
    volatile Check check = new Check();

    public static void main(String[] args) throws Throwable {
        new RemoveMicroBenchmark(args).run();
    }

    void run() throws Throwable {
//         System.out.printf(
//             "iterations=%d size=%d, warmup=%1g, filter=\"%s\"%n",
//             iterations, size, warmupSeconds, filter);

        final ArrayList<Integer> al = new ArrayList<Integer>(size);

        // Populate collections with random data
        final ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (int i = 0; i < size; i++)
            al.add(rnd.nextInt(size));

        ArrayList<Job> jobs = new ArrayList<>();

        List.<Collection<Integer>>of(
            new ArrayList<>(),
            new LinkedList<>(),
            new Vector<>(),
            new ArrayDeque<>(),
            new PriorityQueue<>(),
            new ArrayBlockingQueue<>(al.size()),
            new ConcurrentLinkedQueue<>(),
            new ConcurrentLinkedDeque<>(),
            new LinkedBlockingQueue<>(),
            new LinkedBlockingDeque<>(),
            new LinkedTransferQueue<>(),
            new PriorityBlockingQueue<>())
            .stream().forEach(
                x -> {
                    String klazz = x.getClass().getSimpleName();
                    jobs.addAll(collectionJobs(klazz, () -> x, al));
                    if (x instanceof Queue) {
                        Queue<Integer> queue = (Queue<Integer>) x;
                        jobs.addAll(queueJobs(klazz, () -> queue, al));
                    }
                    if (x instanceof Deque) {
                        Deque<Integer> deque = (Deque<Integer>) x;
                        jobs.addAll(dequeJobs(klazz, () -> deque, al));
                    }
                    if (x instanceof BlockingQueue) {
                        BlockingQueue<Integer> q = (BlockingQueue<Integer>) x;
                        jobs.addAll(blockingQueueJobs(klazz, () -> q, al));
                    }
                    if (x instanceof BlockingDeque) {
                        BlockingDeque<Integer> q = (BlockingDeque<Integer>) x;
                        jobs.addAll(blockingDequeJobs(klazz, () -> q, al));
                    }
                    if (x instanceof List) {
                        List<Integer> list = (List<Integer>) x;
                        jobs.addAll(
                            collectionJobs(
                                klazz + " subList",
                                () -> list.subList(0, x.size()),
                                al));
                    }
                });

        if (reverse) Collections.reverse(jobs);
        if (shuffle) Collections.shuffle(jobs);

        time(filter(filter, jobs));
    }

    Collection<Integer> universeRecorder(int[] sum) {
        return new ArrayList<>() {
            public boolean contains(Object x) {
                sum[0] += (Integer) x;
                return true;
            }};
    }

    Collection<Integer> emptyRecorder(int[] sum) {
        return new ArrayList<>() {
            public boolean contains(Object x) {
                sum[0] += (Integer) x;
                return false;
            }};
    }

    List<Job> collectionJobs(
        String description,
        Supplier<Collection<Integer>> supplier,
        ArrayList<Integer> al) {
        return List.of(
            new Job(description + " .removeIf") {
                public void work() throws Throwable {
                    Collection<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        x.removeIf(n -> { sum[0] += n; return true; });
                        check.sum(sum[0]);}}},
            new Job(description + " .removeAll") {
                public void work() throws Throwable {
                    Collection<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    Collection<Integer> universe = universeRecorder(sum);
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        x.removeAll(universe);
                        check.sum(sum[0]);}}},
            new Job(description + " .retainAll") {
                public void work() throws Throwable {
                    Collection<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    Collection<Integer> empty = emptyRecorder(sum);
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        x.retainAll(empty);
                        check.sum(sum[0]);}}},
            new Job(description + " Iterator.remove") {
                public void work() throws Throwable {
                    Collection<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        Iterator<Integer> it = x.iterator();
                        while (it.hasNext()) {
                            sum[0] += it.next();
                            it.remove();
                        }
                        check.sum(sum[0]);}}},
            new Job(description + " clear") {
                public void work() throws Throwable {
                    Collection<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        x.forEach(e -> sum[0] += e);
                        x.clear();
                        check.sum(sum[0]);}}});
    }

    List<Job> queueJobs(
        String description,
        Supplier<Queue<Integer>> supplier,
        ArrayList<Integer> al) {
        return List.of(
            new Job(description + " poll()") {
                public void work() throws Throwable {
                    Queue<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        for (Integer e; (e = x.poll()) != null; )
                            sum[0] += e;
                        check.sum(sum[0]);}}});
    }

    List<Job> dequeJobs(
        String description,
        Supplier<Deque<Integer>> supplier,
        ArrayList<Integer> al) {
        return List.of(
            new Job(description + " descendingIterator().remove") {
                public void work() throws Throwable {
                    Deque<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        Iterator<Integer> it = x.descendingIterator();
                        while (it.hasNext()) {
                            sum[0] += it.next();
                            it.remove();
                        }
                        check.sum(sum[0]);}}},
            new Job(description + " pollFirst()") {
                public void work() throws Throwable {
                    Deque<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        for (Integer e; (e = x.pollFirst()) != null; )
                            sum[0] += e;
                        check.sum(sum[0]);}}},
            new Job(description + " pollLast()") {
                public void work() throws Throwable {
                    Deque<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        for (Integer e; (e = x.pollLast()) != null; )
                            sum[0] += e;
                        check.sum(sum[0]);}}});
    }

    List<Job> blockingQueueJobs(
        String description,
        Supplier<BlockingQueue<Integer>> supplier,
        ArrayList<Integer> al) {
        return List.of(
            new Job(description + " drainTo(sink)") {
                public void work() throws Throwable {
                    BlockingQueue<Integer> x = supplier.get();
                    ArrayList<Integer> sink = new ArrayList<>();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        sink.clear();
                        x.addAll(al);
                        x.drainTo(sink);
                        sink.forEach(e -> sum[0] += e);
                        check.sum(sum[0]);}}},
            new Job(description + " drainTo(sink, n)") {
                public void work() throws Throwable {
                    BlockingQueue<Integer> x = supplier.get();
                    ArrayList<Integer> sink = new ArrayList<>();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        sink.clear();
                        x.addAll(al);
                        x.drainTo(sink, al.size());
                        sink.forEach(e -> sum[0] += e);
                        check.sum(sum[0]);}}});
    }

    List<Job> blockingDequeJobs(
        String description,
        Supplier<BlockingDeque<Integer>> supplier,
        ArrayList<Integer> al) {
        return List.of(
            new Job(description + " timed pollFirst()") {
                public void work() throws Throwable {
                    BlockingDeque<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        for (Integer e; (e = x.pollFirst(0L, TimeUnit.DAYS)) != null; )
                            sum[0] += e;
                        check.sum(sum[0]);}}},
            new Job(description + " timed pollLast()") {
                public void work() throws Throwable {
                    BlockingDeque<Integer> x = supplier.get();
                    int[] sum = new int[1];
                    for (int i = 0; i < iterations; i++) {
                        sum[0] = 0;
                        x.addAll(al);
                        for (Integer e; (e = x.pollLast(0L, TimeUnit.DAYS)) != null; )
                            sum[0] += e;
                        check.sum(sum[0]);}}});
    }
}
