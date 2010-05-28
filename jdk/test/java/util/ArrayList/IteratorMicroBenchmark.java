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
 * This is not a regression test, but a micro-benchmark.
 * Be patient; this runs for half an hour!
 *
 * I have run this as follows:
 *
 * for f in -client -server; do mergeBench dolphin . jr -dsa -da $f IteratorMicroBenchmark.java; done
 *
 *
 * @author Martin Buchholz
 */

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

public class IteratorMicroBenchmark {
    abstract static class Job {
        private final String name;
        public Job(String name) { this.name = name; }
        public String name() { return name; }
        public abstract void work() throws Throwable;
    }

    private static void collectAllGarbage() {
        final java.util.concurrent.CountDownLatch drained
            = new java.util.concurrent.CountDownLatch(1);
        try {
            System.gc();        // enqueue finalizable objects
            new Object() { protected void finalize() {
                drained.countDown(); }};
            System.gc();        // enqueue detector
            drained.await();    // wait for finalizer queue to drain
            System.gc();        // cleanup finalized objects
        } catch (InterruptedException e) { throw new Error(e); }
    }

    /**
     * Runs each job for long enough that all the runtime compilers
     * have had plenty of time to warm up, i.e. get around to
     * compiling everything worth compiling.
     * Returns array of average times per job per run.
     */
    private static long[] time0(Job ... jobs) throws Throwable {
        final long warmupNanos = 10L * 1000L * 1000L * 1000L;
        long[] nanoss = new long[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            collectAllGarbage();
            long t0 = System.nanoTime();
            long t;
            int j = 0;
            do { jobs[i].work(); j++; }
            while ((t = System.nanoTime() - t0) < warmupNanos);
            nanoss[i] = t/j;
        }
        return nanoss;
    }

    private static void time(Job ... jobs) throws Throwable {

        long[] warmup = time0(jobs); // Warm up run
        long[] nanoss = time0(jobs); // Real timing run
        long[] milliss = new long[jobs.length];
        double[] ratios = new double[jobs.length];

        final String nameHeader   = "Method";
        final String millisHeader = "Millis";
        final String ratioHeader  = "Ratio";

        int nameWidth   = nameHeader.length();
        int millisWidth = millisHeader.length();
        int ratioWidth  = ratioHeader.length();

        for (int i = 0; i < jobs.length; i++) {
            nameWidth = Math.max(nameWidth, jobs[i].name().length());

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
        for (int i = 0; i < jobs.length; i++)
            System.out.printf(format, jobs[i].name(), milliss[i], ratios[i]);
    }

    private static String keywordValue(String[] args, String keyword) {
        for (String arg : args)
            if (arg.startsWith(keyword))
                return arg.substring(keyword.length() + 1);
        return null;
    }

    private static int intArg(String[] args, String keyword, int defaultValue) {
        String val = keywordValue(args, keyword);
        return val == null ? defaultValue : Integer.parseInt(val);
    }

    private static Pattern patternArg(String[] args, String keyword) {
        String val = keywordValue(args, keyword);
        return val == null ? null : Pattern.compile(val);
    }

    private static Job[] filter(Pattern filter, Job[] jobs) {
        if (filter == null) return jobs;
        Job[] newJobs = new Job[jobs.length];
        int n = 0;
        for (Job job : jobs)
            if (filter.matcher(job.name()).find())
                newJobs[n++] = job;
        // Arrays.copyOf not available in JDK 5
        Job[] ret = new Job[n];
        System.arraycopy(newJobs, 0, ret, 0, n);
        return ret;
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

    /**
     * Usage: [iterations=N] [size=N] [filter=REGEXP]
     */
    public static void main(String[] args) throws Throwable {
        final int iterations = intArg(args, "iterations", 100000);
        final int size       = intArg(args, "size", 1000);
        final Pattern filter = patternArg(args, "filter");

        final ConcurrentSkipListMap<Integer,Integer> m
            = new ConcurrentSkipListMap<Integer,Integer>();
        final Vector<Integer> v = new Vector<Integer>(size);
        final ArrayList<Integer> al = new ArrayList<Integer>(size);

        // Populate collections with random data
        final Random rnd = new Random();
        for (int i = 0; i < size; i++) {
            m.put(rnd.nextInt(size), rnd.nextInt(size));
            v.add(rnd.nextInt(size));
        }
        al.addAll(v);

        // Also test "short" collections
        final int shortSize = 5;
        final Vector<Integer> sv = new Vector<Integer>(v.subList(0, shortSize));
        final ArrayList<Integer> sal = new ArrayList<Integer>(sv);

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
        final Check check      = new Check();
        final Check shortCheck = new Check();

        Job[] jobs = {
//          new Job("Vector iterate desugared") {
//              public void work() throws Throwable {
//                  for (int i = 0; i < iterations; i++) {
//                      int sum = 0;
//                      for (Iterator<Integer> it = v.iterator(); it.hasNext();)
//                          sum += it.next();
//                      check.sum(sum);}}},
            new Job("array loop") {
                public void work() throws Throwable {
                    Integer[] a = al.toArray(new Integer[0]);
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        int size = a.length;
                        for (int j = 0; j < size; ++j)
                            sum += a[j];
                        check.sum(sum);}}},
            new Job("Vector get loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        int size = v.size();
                        for (int j = 0; j < size; ++j)
                            sum += v.get(j);
                        check.sum(sum);}}},
            new Job("Vector iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : v)
                            sum += n;
                        check.sum(sum);}}},
            new Job("Vector descending listIterator loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        ListIterator<Integer> it = v.listIterator(al.size());
                        while (it.hasPrevious())
                            sum += it.previous();
                        check.sum(sum);}}},
            new Job("Vector Enumeration loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        Enumeration<Integer> it = v.elements();
                        while (it.hasMoreElements())
                            sum += it.nextElement();
                        check.sum(sum);}}},
            new Job("Vector subList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : asSubList(v))
                            sum += n;
                        check.sum(sum);}}},
            new Job("Vector subList subList subList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : asSubList(asSubList(asSubList(v))))
                            sum += n;
                        check.sum(sum);}}},
            new Job("Vector backwards wrapper ListIterator for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : backwards(v))
                            sum += n;
                        check.sum(sum);}}},
            new Job("Vector backwards wrapper subList ListIterator for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : backwards(asSubList(v)))
                            sum += n;
                        check.sum(sum);}}},
//          new Job("Vector iterate for loop invokeinterface") {
//              public void work() throws Throwable {
//                  final List<Integer> l = v;
//                  for (int i = 0; i < iterations; i++) {
//                      int sum = 0;
//                      for (Integer n : l)
//                          sum += n;
//                      check.sum(sum);}}},
//          new Job("Vector subList iterate for loop invokeinterface") {
//              public void work() throws Throwable {
//                  final List<Integer> l = v;
//                  for (int i = 0; i < iterations; i++) {
//                      int sum = 0;
//                      for (Integer n : asSubList(l))
//                          sum += n;
//                      check.sum(sum);}}},
            new Job("Short Vector get loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        int size = sv.size();
                        for (int j = 0; j < size; ++j)
                            sum += sv.get(j);
                        shortCheck.sum(sum);}}},
            new Job("Short Vector iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        for (Integer n : sv)
                            sum += n;
                        shortCheck.sum(sum);}}},
            new Job("Short Vector sublist iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        for (Integer n : asSubList(sv))
                            sum += n;
                        shortCheck.sum(sum);}}},
            new Job("ArrayList get loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        int size = al.size();
                        for (int j = 0; j < size; ++j)
                            sum += al.get(j);
                        check.sum(sum);}}},
            new Job("ArrayList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : al)
                            sum += n;
                        check.sum(sum);}}},
            new Job("ArrayList descending listIterator loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        ListIterator<Integer> it = al.listIterator(al.size());
                        while (it.hasPrevious())
                            sum += it.previous();
                        check.sum(sum);}}},
            new Job("ArrayList listIterator loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        ListIterator<Integer> it = al.listIterator();
                        while (it.hasNext())
                            sum += it.next();
                        check.sum(sum);}}},
            new Job("ArrayList subList get loop") {
                public void work() throws Throwable {
                    List<Integer> sl = asSubList(al);
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        int size = sl.size();
                        for (int j = 0; j < size; ++j)
                            sum += sl.get(j);
                        check.sum(sum);}}},
            new Job("ArrayList subList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : asSubList(al))
                            sum += n;
                        check.sum(sum);}}},
            new Job("ArrayList subList subList subList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : asSubList(asSubList(asSubList(al))))
                            sum += n;
                        check.sum(sum);}}},
            new Job("ArrayList backwards wrapper ListIterator for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : backwards(al))
                            sum += n;
                        check.sum(sum);}}},
            new Job("ArrayList backwards wrapper subList ListIterator for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Integer n : backwards(asSubList(al)))
                            sum += n;
                        check.sum(sum);}}},
//          new Job("ArrayList iterate desugared") {
//              public void work() throws Throwable {
//                  for (int i = 0; i < iterations; i++) {
//                      int sum = 0;
//                      for (Iterator<Integer> it = al.iterator(); it.hasNext();)
//                          sum += it.next();
//                      check.sum(sum);}}},
            new Job("Short ArrayList get loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        int size = sal.size();
                        for (int j = 0; j < size; ++j)
                            sum += sal.get(j);
                        shortCheck.sum(sum);}}},
            new Job("Short ArrayList iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        for (Integer n : sal)
                            sum += n;
                        shortCheck.sum(sum);}}},
            new Job("Short ArrayList sublist iterate for loop") {
                public void work() throws Throwable {
                    for (int i = 0; i < (iterations * size / shortSize); i++) {
                        int sum = 0;
                        for (Integer n : asSubList(sal))
                            sum += n;
                        shortCheck.sum(sum);}}},
            new Job("Vector ArrayList alternating iteration") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        Iterator<Integer> it1 = v.iterator();
                        Iterator<Integer> it2 = al.iterator();
                        while (it1.hasNext())
                            sum += it1.next() + it2.next();
                        check.sum(sum/2);}}},
            new Job("Vector ArrayList alternating invokeVirtual iteration") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        List<Iterator<Integer>> its
                            = new ArrayList<Iterator<Integer>>(2);
                        its.add(v.iterator());
                        its.add(al.iterator());
                        for (int k = 0; its.get(k).hasNext(); k = (k == 0) ? 1 : 0)
                            sum += its.get(k).next();
                        check.sum(sum/2);}}},
            new Job("ConcurrentSkipListMap entrySet iterate") {
                public void work() throws Throwable {
                    for (int i = 0; i < iterations; i++) {
                        int sum = 0;
                        for (Map.Entry<Integer,Integer> e : m.entrySet())
                            sum += e.getKey();
                        deoptimize(sum);}}}
        };

        time(filter(filter, jobs));
    }
}
