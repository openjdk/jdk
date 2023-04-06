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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodType.methodType;

/**
 * Benchmark evaluates the call performance of MethodHandleProxies.asInterfaceInstance
 * return value, compared to
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class MethodHandleProxiesAsIFInstanceCall {
    /**
     * Avoids elimination of computation, set up to random value
     */
    public int i;

    private static final Lookup LOOKUP = lookup();
    private static final MethodType MT_Doable = methodType(Doable.class);
    private static final MethodType MT_int_int = methodType(int.class, int.class);

    // intentionally constant-folded
    private static final MethodHandle constantTarget;
    private static final Doable constantDoable;
    private static final Doable constantHandle;
    private static final Doable constantInterfaceInstance;
    private static final Doable constantLambda;

    // part of state object, non-constant
    private MethodHandle target;
    private Doable doable;
    private Doable handle;
    private Doable interfaceInstance;
    private Doable lambda;

    static {
        try {
            constantTarget = LOOKUP.findStatic(MethodHandleProxiesAsIFInstanceCall.class, "doWork", MT_int_int);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new ExceptionInInitializerError(e);
        }
        constantDoable = new Doable() {
            @Override
            public int doWork(int i) {
                return MethodHandleProxiesAsIFInstanceCall.doWork(i);
            }
        };
        constantHandle = new Doable() {
            @Override
            public int doWork(int i) {
                try {
                    return (int) constantTarget.invokeExact((int) i);
                } catch (Error | RuntimeException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            }
        };
        constantInterfaceInstance = MethodHandleProxies.asInterfaceInstance(Doable.class, constantTarget);
        constantLambda = MethodHandleProxiesAsIFInstanceCall::doWork;
    }

    @Setup
    public void setup() throws Throwable {
        target = constantTarget;
        doable = constantDoable;
        handle = constantHandle;
        interfaceInstance = constantInterfaceInstance;
        lambda = constantLambda;
        i = ThreadLocalRandom.current().nextInt();
    }

    @Benchmark
    public void direct() {
        i = doWork(i);
    }

    @Benchmark
    public void callDoable() {
        i = doable.doWork(i);
    }

    @Benchmark
    public void callHandle() {
        i = handle.doWork(i);
    }

    @Benchmark
    public void callInterfaceInstance() {
        i = interfaceInstance.doWork(i);
    }

    @Benchmark
    public void callLambda() {
        i = lambda.doWork(i);
    }

    @Benchmark
    public void constantDoable() {
        i = constantDoable.doWork(i);
    }

    @Benchmark
    public void constantHandle() {
        i = constantHandle.doWork(i);
    }

    @Benchmark
    public void constantInterfaceInstance() {
        i = constantInterfaceInstance.doWork(i);
    }

    @Benchmark
    public void constantLambda() {
        i = constantLambda.doWork(i);
    }

    public static int doWork(int i) {
        return i + 1;
    }

    public interface Doable {
        int doWork(int i);
    }

}
