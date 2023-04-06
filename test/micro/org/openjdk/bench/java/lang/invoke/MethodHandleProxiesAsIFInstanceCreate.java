/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.invoke;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Benchmark evaluates the performance of MethodHandleProxies.asInterfaceInstance creation performance
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(3)
public class MethodHandleProxiesAsIFInstanceCreate {

    /**
     * Implementation notes:
     *   - asInterfaceInstance() can only target static MethodHandle (adapters needed to call instance method?)
     *   - baselineCompute will quickly degrade to GC test, if escape analysis is unable to spare the allocation
     *   - testCreate* will always be slower if allocation is not eliminated; baselineAllocCompute makes sure allocation is present
     *   - lambda* compares lambda performance with asInterfaceInstance performance
     */

    public int i;

    private static final Lookup LOOKUP = lookup();
    private static final MethodType MT_Doable = methodType(Doable.class);
    private static final MethodType MT_int_int = methodType(int.class, int.class);
    private MethodHandle target;

    @Setup
    public void setup() throws Throwable {
        target = LOOKUP.findStatic(MethodHandleProxiesAsIFInstanceCreate.class, "doWork", MT_int_int);
    }

    @Benchmark
    public Doable lambdaCreate() throws Throwable {
        return (Doable) LambdaMetafactory.metafactory(LOOKUP, "doWork", MT_Doable, MT_int_int, target, MT_int_int).getTarget().invokeExact();
    }

    @Benchmark
    public Doable interfaceInstanceCreate() {
        return MethodHandleProxies.asInterfaceInstance(Doable.class, target);
    }

    @Benchmark
    public Doable lambdaCreateCall() throws Throwable {
        Doable doable = (Doable) LambdaMetafactory.metafactory(LOOKUP, "doWork", MT_Doable, MT_int_int, target, MT_int_int).getTarget().invokeExact();
        i = doable.doWork(i);
        return doable;
    }

    @Benchmark
    public Doable interfaceInstanceCreateCall() {
        Doable doable = MethodHandleProxies.asInterfaceInstance(Doable.class, target);
        i = doable.doWork(i);
        return doable;
    }

    public static int doWork(int i) {
        return i + 1;
    }

    public interface Doable {
        int doWork(int i);
    }

}
