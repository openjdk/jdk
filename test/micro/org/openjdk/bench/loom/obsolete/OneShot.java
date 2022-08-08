/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.loom.obsolete;

import jdk.internal.vm.Continuation;
import jdk.internal.vm.ContinuationScope;

import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.*;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class OneShot {
    static final ContinuationScope SCOPE = new ContinuationScope() { };

    static class Arg {
        volatile int field;
    }

    /**
     * A recursive task that optionally yields when the stack gets to a specific
     * depth. If continued after yielding, it runs to completion.
     */
    static class Yielder implements Runnable {
        private final int paramCount;
        private final int maxDepth;
        private final boolean yieldAtLimit;

        private Yielder(int paramCount, int maxDepth, boolean yieldAtLimit) {
            if (paramCount < 1 || paramCount > 3)
                throw new IllegalArgumentException();
            this.paramCount = paramCount;
            this.maxDepth = maxDepth;
            this.yieldAtLimit = yieldAtLimit;
        }

        @Override
        public void run() {
            switch (paramCount) {
                case 1: run1(maxDepth); break;
                case 2: run2(maxDepth, new Arg()); break;
                case 3: run3(maxDepth, new Arg(), new Arg()); break;
                default: throw new Error("should not happen");
            }
        }

        private void run1(int depth) {
            if (depth > 0) {
                run1(depth - 1);
            } if (depth == 0) {
                if (yieldAtLimit) Continuation.yield(SCOPE);
            }
        }

        private void run2(int depth, Arg arg2) {
            if (depth > 0) {
                run2(depth - 1, arg2);
            } if (depth == 0) {
                if (yieldAtLimit) Continuation.yield(SCOPE);
            } else {
                // never executed
                arg2.field = 0;
            }
        }

        private void run3(int depth, Arg arg2, Arg arg3) {
            if (depth > 0) {
                run3(depth - 1, arg2, arg3);
            } if (depth == 0) {
                if (yieldAtLimit) Continuation.yield(SCOPE);
            } else {
                // never executed
                arg2.field = 0;
                arg3.field = 0;
            }
        }

        static Continuation continuation(int paramCount, int maxDepth,
                                         boolean yieldAtLimit) {
            Runnable task = new Yielder(paramCount, maxDepth, yieldAtLimit);
            return new Continuation(SCOPE, task);
        }
    }

    /**
     * A recursive task that optionally yields before and/or after each call.
     */
    static class Stepper implements Runnable {
        private final int paramCount;
        private final int maxDepth;
        private final boolean yieldBefore;
        private final boolean yieldAfter;

        private Stepper(int paramCount,
                        int maxDepth,
                        boolean yieldBefore,
                        boolean yieldAfter) {
            this.paramCount = paramCount;
            this.maxDepth = maxDepth;
            this.yieldBefore = yieldBefore;
            this.yieldAfter = yieldAfter;
        }

        public void run() {
            switch (paramCount) {
                case 1: run1(maxDepth); break;
                case 2: run2(maxDepth, new Arg()); break;
                case 3: run3(maxDepth, new Arg(), new Arg()); break;
                default: throw new Error("should not happen");
            }
        }

        private void run1(int depth) {
            if (depth > 0) {
                if (yieldBefore) Continuation.yield(SCOPE);
                run1(depth - 1);
                if (yieldAfter) Continuation.yield(SCOPE);
            }
        }

        private void run2(int depth, Arg arg2) {
            if (depth > 0) {
                if (yieldBefore) Continuation.yield(SCOPE);
                run2(depth - 1, arg2);
                if (yieldAfter) Continuation.yield(SCOPE);
            } else if (depth < 0) {
                // never executed
                arg2.field = 0;
            }
        }

        private void run3(int depth, Arg arg2, Arg arg3) {
            if (depth > 0) {
                if (yieldBefore) Continuation.yield(SCOPE);
                run3(depth - 1, arg2, arg3);
                if (yieldAfter) Continuation.yield(SCOPE);
            } else if (depth < 0) {
                // never executed
                arg2.field = 0;
                arg3.field = 0;
            }
        }

        static Continuation continuation(int paramCount, int maxDepth,
                                         boolean yieldBefore, boolean yieldAfter) {
            Runnable task = new Stepper(paramCount, maxDepth, yieldBefore, yieldAfter);
            return new Continuation(SCOPE, task);
        }
    }

    @Param({"1", "2", "3"})
    public int paramCount;

    @Param({"5", "10", "20", "100"})
    public int stackDepth;

    /**
     * Creates and run continuation that does not yield.
     */
    @Benchmark
    public void noYield() {
        Continuation cont = Yielder.continuation(paramCount, stackDepth, false);
        cont.run();
        if (!cont.isDone())
            throw new RuntimeException("continuation not done???");
    }

    /**
     * Creates and runs a continuation that yields at a given stack depth.
     */
    @Benchmark
    public void yield() {
        Continuation cont = Yielder.continuation(paramCount, stackDepth, true);
        cont.run();
        if (cont.isDone())
            throw new RuntimeException("continuation done???");
    }

    /**
     * Creates and runs a continuation that yields at a given stack depth, it is
     * then continued to run to completion.
     */
    @Benchmark
    public void yieldThenContinue() {
        Continuation cont = Yielder.continuation(paramCount, stackDepth, true);
        cont.run();
        cont.run();  // continue
        if (!cont.isDone())
            throw new RuntimeException("continuation not done???");
    }

    /**
     * Creates and runs a continuation to the given stack depth, yielding before
     * each call.
     */
    @Benchmark
    public void yieldBeforeEachCall() {
        Continuation cont = Stepper.continuation(paramCount, stackDepth, true, false);
        while (!cont.isDone()) {
            cont.run();
        }
    }

    /**
     * Creates and runs a continuation to the given stack depth, yielding after
     * each call.
     */
    @Benchmark
    public void yieldAfterEachCall() {
        Continuation cont = Stepper.continuation(paramCount, stackDepth, false, true);
        while (!cont.isDone()) {
            cont.run();
        }
    }

    /**
     * Creates and runs a continuation to the given stack depth, yielding before
     * and after each call.
     */
    @Benchmark
    public void yieldBeforeAndAfterEachCall() {
        Continuation cont = Stepper.continuation(paramCount, stackDepth, true, true);
        while (!cont.isDone()) {
            cont.run();
        }
    }
}
