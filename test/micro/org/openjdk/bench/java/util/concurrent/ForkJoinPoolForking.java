/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.util.concurrent;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

/**
 * Benchmark assesses ForkJoinPool forking infrastructure.
 *
 * @author Aleksey Shipilev (aleksey.shipilev@oracle.com)
 */
@OutputTimeUnit(TimeUnit.MINUTES)
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(3)
public class ForkJoinPoolForking {

    /**
     * Implementation notes:
     *
     * This test harnesses forking infrastructure within FJP.
     * As such, no slack is given for allocating any humble number of tasks: the goal is to fork a lot.
     * The approximate number of tasks is (SIZE / THRESHOLD).
     *
     * Raw baseline gives the idea for compute bound for this benchmark.
     * FJP could be faster than baseline, because the baseline is single-threaded.
     */

    @Param("10000000")
    private int size;

    /** Encapsulate all the state depended on only by actual test. This avoids running baselines for every parameter value. */
    @State(Scope.Benchmark)
    public static class TestState {

        @Param("0")
        private int workers;

        @Param({"1", "2", "3", "4", "5", "6", "7", "8"})
        private int threshold;

        private ForkJoinPool fjpSync;
        private ForkJoinPool fjpAsync;

        @Setup
        public void setup() {
            if (workers == 0) {
                workers = Runtime.getRuntime().availableProcessors();
            }
            fjpSync = new ForkJoinPool(workers, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, false);
            fjpAsync = new ForkJoinPool(workers, ForkJoinPool.defaultForkJoinWorkerThreadFactory, null, true);
        }


        @TearDown
        public void teardown() {
            fjpSync.shutdownNow();
            fjpAsync.shutdownNow();
        }
    }
    private Problem problem;

    @Setup
    public void setup() {
        problem = new Problem(size);
    }

    @Benchmark
    public Long testExplicit_Sync(TestState state) throws ExecutionException, InterruptedException {
        return state.fjpSync.invoke(new ExplicitTask(problem, 0, problem.size(), state.threshold));
    }

    @Benchmark
    public Long testExplicit_Async(TestState state) throws ExecutionException, InterruptedException {
        return state.fjpAsync.invoke(new ExplicitTask(problem, 0, problem.size(), state.threshold));
    }

    @Benchmark
    public Long testStandard_Sync(TestState state) throws ExecutionException, InterruptedException {
        return state.fjpSync.invoke(new StandardTask(problem, 0, problem.size(), state.threshold));
    }

    @Benchmark
    public Long testStandard_Async(TestState state) throws ExecutionException, InterruptedException {
        return state.fjpAsync.invoke(new StandardTask(problem, 0, problem.size(), state.threshold));
    }

    private static class ExplicitTask extends RecursiveTask<Long> {
        private final Problem problem;
        private final int l;
        private final int r;
        private final int thresh;

        public ExplicitTask(Problem p, int l, int r, int thresh) {
            this.problem = p;
            this.l = l;
            this.r = r;
            this.thresh = thresh;
        }

        @Override
        protected Long compute() {
            if (r - l <= thresh) {
                return problem.solve(l, r);
            }

            int mid = (l + r) >>> 1;
            ForkJoinTask<Long> t1 = new ExplicitTask(problem, l, mid, thresh);
            ForkJoinTask<Long> t2 = new ExplicitTask(problem, mid, r, thresh);

            t1.fork();
            t2.fork();

            long res = 0;
            res += t2.join();
            res += t1.join();
            return res;
        }
    }

    private static class StandardTask extends RecursiveTask<Long> {
        private final Problem problem;
        private final int l;
        private final int r;
        private final int thresh;

        public StandardTask(Problem p, int l, int r, int thresh) {
            this.problem = p;
            this.l = l;
            this.r = r;
            this.thresh = thresh;
        }

        @Override
        protected Long compute() {
            if (r - l <= thresh) {
                return problem.solve(l, r);
            }

            int mid = (l + r) >>> 1;
            ForkJoinTask<Long> t1 = new StandardTask(problem, l, mid, thresh);
            ForkJoinTask<Long> t2 = new StandardTask(problem, mid, r, thresh);

            ForkJoinTask.invokeAll(t1, t2);
            long res = 0;
            res += t1.join();
            res += t2.join();
            return res;
        }
    }
}
