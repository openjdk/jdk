/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1)
public class AllocationsMerge {
    private int SIZE = 10 * 1024;
    private int opaque_value1 = 3342;
    private int opaque_value2 = 4342;

    private boolean[] conds = new boolean[SIZE];
    private int[] xs = new int[SIZE];
    private int[] ys = new int[SIZE];

    @Setup
    public void init() {
        Random r = new Random(1024);

        for (int i=0; i<SIZE; i++) {
            conds[i] = i % 2 == 0;
            xs[i] = r.nextInt();
            ys[i] = r.nextInt();
        }
    }

    static class Point {
        int x, y;
        Point(int x, int y) { this.x = x; this.y = y; }
    }

    static class Picture {
        public int id;
        public Point position;

        public Picture(int id, int x, int y) {
            this.id = id;
            this.position = new Point(x, y);
        }

        public Picture(int id, Point p) {
            this.id = id;
            this.position = p;
        }
    }

    @Benchmark
    public void SimpleMerge(Blackhole bh) {
        for (int i=0; i<SIZE; i++) {
            bh.consume(run_SimpleMerge(conds[i], xs[i], ys[i]));
        }
    }

    private int run_SimpleMerge(boolean cond, int x, int y) {
        Point p = new Point(x, y);

        if (cond) {
            p = new Point(y, x);
        }

        return p.x * p.y;
    }

    @Benchmark
    public void NestedObjectsObject(Blackhole bh) {
        for (int i=0; i<SIZE; i++) {
            bh.consume(run_NestedObjectsObject(conds[i], xs[i], ys[i]));
        }
    }

    private Point run_NestedObjectsObject(boolean cond, int x, int y) {
        Picture p = new Picture(x, x, y);

        if (cond) {
            p = new Picture(y, y, x);
        }

        return p.position;
    }

    @Benchmark
    public void MergeAndIterative(Blackhole bh) {
        for (int i=0; i<SIZE; i++) {
            bh.consume(run_MergeAndIterative(conds[i], xs[i], ys[i]));
        }
    }

    private int run_MergeAndIterative(boolean cond, int x, int y) {
        Point p = new Point(x, y);

        if (cond) {
            p = new Point(y, x);
        }

        Picture pic = new Picture(2022, p);

        return pic.position.x + pic.position.y;
    }

    @Benchmark
    public void IfElseInLoop(Blackhole bh) {
        for (int i=0; i<SIZE; i++) {
            bh.consume(run_IfElseInLoop());
        }
    }

    private int run_IfElseInLoop() {
        int res = 0;

        for (int i=this.opaque_value1; i<this.opaque_value2; i++) {
            Point obj = new Point(i, i);

            if (i % 2 == 1) {
                obj = new Point(i, i+1);
            } else {
                obj = new Point(i-1, i);
            }

            res += obj.x;
        }

        return res;
    }

    @Benchmark
    public void TrapAfterMerge(Blackhole bh) {
        for (int i=0; i<SIZE; i++) {
            bh.consume(run_TrapAfterMerge(conds[i], xs[i], ys[i]));
        }
    }

    private int run_TrapAfterMerge(boolean cond, int x, int y) {
        Point p = new Point(x, x);

        if (cond) {
            p = new Point(y, y);
        }

        for (int i=this.opaque_value1; i<this.opaque_value2; i+=x) {
            x++;
        }

        return p.x + x;
    }

    @Fork(jvmArgsPrepend = {"-XX:+ReduceAllocationMerges"})
    public static class WithAllocationsMergeEnabled extends AllocationsMerge { }
}
