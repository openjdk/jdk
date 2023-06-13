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
@State(Scope.Benchmark)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class Oscillation {
    static final ContinuationScope SCOPE = new ContinuationScope() { };

    /**
     * A task that oscillates between a minimum and maximum stack depth, yielding
     * at the maximum stack until continued.
     */
    static class Wave implements Runnable {
        private final int min;
        private final int max;

        private enum Mode { GROW, SHRINK }
        private Mode mode;

        Wave(int min, int max) {
            if (min < 0)
                throw new IllegalArgumentException("negative min");
            if (max <= min)
                throw new IllegalArgumentException("max must be greater than min");
            this.min = min;
            this.max = max;
            this.mode = Mode.GROW;
        }

        private void run(int depth) {
            if (depth == max) {
                Continuation.yield(SCOPE);
                mode = Mode.SHRINK;
            } else if (depth == min) {
                while (true) {
                    mode = Mode.GROW;
                    run(depth + 1);
                }
            } else if (mode == Mode.GROW) {
                run(depth + 1);
            }
        }

        @Override
        public void run() {
            run(0);
        }
    }

    @Param({"2", "3", "4"})
    public int minDepth;

    @Param({"5", "6", "7", "8"})
    public int maxDepth;

    @Param({"10", "100", "1000"})
    public int repeat;

    /**
     * Creates and runs a continuation that oscillates between a minimum and
     * maximum stack depth, yielding at the maximum stack until continued.
     *
     * Useful to measure freeze and thaw, also to compare full stack vs. lazy copy.
     */
    @Benchmark
    public void oscillate() {
        Continuation cont = new Continuation(SCOPE, new Wave(minDepth, maxDepth));
        for (int i=0; i<repeat; i++) {
            cont.run();
        }
    }
}
