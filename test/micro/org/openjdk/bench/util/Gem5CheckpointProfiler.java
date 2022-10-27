/*
 * Copyright (c) 2022, Rivos Inc. All rights reserved.
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

package org.openjdk.bench.util;

import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.profile.InternalProfiler;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.Result;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static java.util.concurrent.TimeUnit.NANOSECONDS;;

public class Gem5CheckpointProfiler implements InternalProfiler {

    static final Object gem5Instance;
    static final Method gem5CheckpointMethod;

    static {
        try {
            Class<?> gem5Class = Class.forName("gem5.Ops");
            gem5CheckpointMethod = getGem5CheckpointMethod(gem5Class);
            gem5Instance = getGem5Instance(gem5Class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Gem5 Ops", e);
        }
    }

    private static Method getGem5CheckpointMethod(Class<?> gem5Class) throws Exception {
        // void checkpoint(long ns_delay, long ns_period)
        return gem5Class.getMethod("checkpoint", long.class, long.class);
    }

    @SuppressWarnings("unchecked")
    private static Object getGem5Instance(Class<?> gem5Class) throws Exception {
        return ((Map<String, Object>)gem5Class.getField("callTypes").get(null)).get("default");
    }

    /**
     * Human-readable one-line description of the profiler.
     * @return description
     */
    public String getDescription() {
        return "gem5 checkpointing";
    }

    /**
     * Run this code before starting the next benchmark iteration.
     *
     * @param benchmarkParams benchmark parameters used for current launch
     * @param iterationParams iteration parameters used for current launch
     */
    public void beforeIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams) {
        try {
            // artificially delay by 1us to not capture the setting up of the iterations
            gem5CheckpointMethod.invoke(gem5Instance, Long.valueOf(1000), Long.valueOf(iterationParams.getTime().convertTo(NANOSECONDS)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to ivoke Gem5 checkpointing", e);
        }
    }

    /**
     * Run this code after a benchmark iteration finished
     *
     * @param benchmarkParams benchmark parameters used for current launch
     * @param iterationParams iteration parameters used for current launch
     * @param result iteration result
     * @return profiler results
     */
    public Collection<? extends Result> afterIteration(BenchmarkParams benchmarkParams, IterationParams iterationParams, IterationResult result) {
        return Collections.emptyList();
    }

    private static final native void m5_checkpoint(long ns_delay, long ns_period);
}
