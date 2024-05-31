/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
 */
package org.openjdk.bench.java.lang.reflect;

import java.lang.reflect.Constructor;
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
public class ConstructorBenchmark {
    Constructor<?> emptyParametersConstructor;
    Constructor<?> oneParameterConstructor;
    Constructor<?> emptyExceptionsConstructor;
    Constructor<?> oneExceptionConstructor;

    public ConstructorBenchmark() {
        try {
            emptyParametersConstructor = Object.class.getConstructor();
            oneParameterConstructor = String.class.getConstructor(String.class);

            emptyExceptionsConstructor = emptyParametersConstructor;
            oneExceptionConstructor = String.class.getConstructor(byte[].class, String.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Benchmark
    public void getExceptionTypes() throws Exception {
        oneExceptionConstructor.getExceptionTypes();
    }

    @Benchmark
    public void getExceptionTypesEmpty() throws Exception {
        emptyExceptionsConstructor.getExceptionTypes();
    }

    @Benchmark
    public void getParameterTypesEmpty() throws Exception {
        emptyParametersConstructor.getParameterTypes();
    }

    @Benchmark
    public void getParameterTypes() throws Exception {
        oneParameterConstructor.getParameterTypes();
    }
}
