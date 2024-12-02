package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 1, jvmArgsAppend = {"-XX:+UseParallelGC", "-Xmx1g", "-Xms1g"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
public class FluidSBBench {
    static final String PREFIX = "a";
    String foo = "aaaaa aaaaa aaaaa aaaaa aaaaa";

    @Benchmark
    public String fluid() {
        return new StringBuilder().append(PREFIX).append(foo).toString();
    }

    @Benchmark
    public String nonFluid() {
        final StringBuilder sb = new StringBuilder();
        sb.append(PREFIX);
        sb.append(foo);
        return sb.toString();
    }
}