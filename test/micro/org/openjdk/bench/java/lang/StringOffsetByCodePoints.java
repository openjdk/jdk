package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Fork(3)
public class StringOffsetByCodePoints {

    @Benchmark
    public int offsetByCodePoints(Data data) {
        return data.value.offsetByCodePoints(data.index, data.offset);
    }

    @State(Scope.Benchmark)
    public static class Data {
        String value = "abc";
        int index = 0;
        int offset = 1;
    }
}
