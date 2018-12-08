/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.microbenchmarks.graal;

import java.util.Arrays;

import org.graalvm.compiler.phases.schedule.SchedulePhase;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Warmup;

import org.graalvm.compiler.microbenchmarks.graal.util.MethodSpec;
import org.graalvm.compiler.microbenchmarks.graal.util.ScheduleState;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.phases.schedule.SchedulePhase.SchedulingStrategy;

@Warmup(iterations = 20)
@Measurement(iterations = 10)
public class SchedulePhaseBenchmark extends GraalBenchmark {

    @MethodSpec(declaringClass = String.class, name = "equals")
    public static class StringEquals extends ScheduleState {
    }

    @Benchmark
    public void stringEquals(StringEquals s) {
        s.schedule.apply(s.graph);
    }

    @Benchmark
    public void cfgCompute1(StringEquals s) {
        ControlFlowGraph.compute(s.graph, true, false, false, false);
    }

    @Benchmark
    public void cfgCompute2(StringEquals s) {
        ControlFlowGraph.compute(s.graph, true, true, false, false);
    }

    @Benchmark
    public void cfgCompute3(StringEquals s) {
        ControlFlowGraph.compute(s.graph, true, true, true, false);
    }

    @Benchmark
    public void cfgCompute4(StringEquals s) {
        ControlFlowGraph.compute(s.graph, true, true, true, true);
    }

    public static int[] intersectionSnippet(int[] in1, int[] in2) {
        int[] result = new int[Math.min(in1.length, in2.length)];
        int next = 0;
        for (int i1 : in1) {
            for (int i2 : in2) {
                if (i2 == i1) {
                    result[next++] = i1;
                    break;
                }
            }
        }
        if (next < result.length) {
            result = Arrays.copyOf(result, next);
        }
        return result;
    }

    // Checkstyle: stop method name check
    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class IntersectionState_LATEST_OPTIMAL extends ScheduleState {
        public IntersectionState_LATEST_OPTIMAL() {
            super(SchedulingStrategy.LATEST);
        }
    }

    @Benchmark
    public void intersection_LATEST_OPTIMAL(IntersectionState_LATEST_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class IntersectionState_LATEST_OUT_OF_LOOPS_OPTIMAL extends ScheduleState {
        public IntersectionState_LATEST_OUT_OF_LOOPS_OPTIMAL() {
            super(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
        }
    }

    @Benchmark
    public void intersection_LATEST_OUT_OF_LOOPS_OPTIMAL(IntersectionState_LATEST_OUT_OF_LOOPS_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class IntersectionState_EARLIEST_OPTIMAL extends ScheduleState {
        public IntersectionState_EARLIEST_OPTIMAL() {
            super(SchedulingStrategy.EARLIEST);
        }
    }

    @Benchmark
    public void intersection_EARLIEST_OPTIMAL(IntersectionState_EARLIEST_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhaseBenchmark.class, name = "intersectionSnippet")
    public static class IntersectionState_EARLIEST_WITH_GUARD_ORDER_OPTIMAL extends ScheduleState {
        public IntersectionState_EARLIEST_WITH_GUARD_ORDER_OPTIMAL() {
            super(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        }
    }

    @Benchmark
    public void intersection_EARLIEST_WITH_GUARD_ORDER_OPTIMAL(IntersectionState_EARLIEST_WITH_GUARD_ORDER_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }
    // Checkstyle: resume method name check

    // Checkstyle: stop method name check
    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class ScheduleEarliestIterative_LATEST_OPTIMAL extends ScheduleState {
        public ScheduleEarliestIterative_LATEST_OPTIMAL() {
            super(SchedulingStrategy.LATEST);
        }
    }

    @Benchmark
    public void scheduleEarliestIterative_LATEST_OPTIMAL(ScheduleEarliestIterative_LATEST_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class ScheduleEarliestIterative_LATEST_OUT_OF_LOOPS_OPTIMAL extends ScheduleState {
        public ScheduleEarliestIterative_LATEST_OUT_OF_LOOPS_OPTIMAL() {
            super(SchedulingStrategy.LATEST_OUT_OF_LOOPS);
        }
    }

    @Benchmark
    public void scheduleEarliestIterative_LATEST_OUT_OF_LOOPS_OPTIMAL(ScheduleEarliestIterative_LATEST_OUT_OF_LOOPS_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class ScheduleEarliestIterative_EARLIEST_OPTIMAL extends ScheduleState {
        public ScheduleEarliestIterative_EARLIEST_OPTIMAL() {
            super(SchedulingStrategy.EARLIEST);
        }
    }

    @Benchmark
    public void scheduleEarliestIterative_EARLIEST_OPTIMAL(ScheduleEarliestIterative_EARLIEST_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }

    @MethodSpec(declaringClass = SchedulePhase.Instance.class, name = "scheduleEarliestIterative")
    public static class ScheduleEarliestIterative_EARLIEST_WITH_GUARD_ORDER_OPTIMAL extends ScheduleState {
        public ScheduleEarliestIterative_EARLIEST_WITH_GUARD_ORDER_OPTIMAL() {
            super(SchedulingStrategy.EARLIEST_WITH_GUARD_ORDER);
        }
    }

    @Benchmark
    public void scheduleEarliestIterative_EARLIEST_WITH_GUARD_ORDER_OPTIMAL(ScheduleEarliestIterative_EARLIEST_WITH_GUARD_ORDER_OPTIMAL s) {
        s.schedule.apply(s.graph);
    }
    // Checkstyle: resume method name check
}
