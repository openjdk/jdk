/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 */
package org.openjdk.bench.java.lang.reflect;

import java.lang.reflect.Executable;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmark measuring the speed of Method/Constructor.getExceptionTypes() and
 * getParameterTypes(), in cases where the result array is length zero.
 */
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)
public class MethodBenchmark {
    Executable objectConstructor;
    Executable hashCodeMethod;

    public MethodBenchmark() {
        try {
            hashCodeMethod = Object.class.getDeclaredMethod("hashCode");
            objectConstructor = Object.class.getConstructor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Benchmark
    public void constructorExceptionsEmpty(Blackhole bh) throws Exception {
        bh.consume(objectConstructor.getExceptionTypes());
    }

    @Benchmark
    public void constructorParametersEmpty(Blackhole bh) throws Exception {
        bh.consume(objectConstructor.getParameterTypes());
    }

    @Benchmark
    public void methodExceptionsEmpty(Blackhole bh) throws Exception {
        bh.consume(hashCodeMethod.getExceptionTypes());
    }

    @Benchmark
    public void methodParametersEmpty(Blackhole bh) throws Exception {
        bh.consume(hashCodeMethod.getParameterTypes());
    }
}
