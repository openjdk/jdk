package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

/**
 * Tests transformations in SubNode::Ideal.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 3 , jvmArgsAppend = {"-XX:-TieredCompilation", "-Xbatch", "-Xcomp"})
public class SubIdeal {

    private static final int C0 = 1234567;

    private static final int C1 = 1234567;

    private final int size = 100_000_000;

    private int[] ints_a;

    @Setup
    public void init() {
        ints_a = new int[size];
        for (int i = 0; i < size; i++) {
            ints_a[i] = i;
        }
    }

    @Benchmark
    public void baseline() {
        for (int i = 0; i < size; i++) {
            sink(ints_a[i]);
        }
    }

    @Benchmark
    public void test() {
        for (int i = 0; i < size; i++) {
            sink(helper(ints_a[i]));
        }
    }

    // Convert "c0 - (x + c1)" into "(c0 - c1) - x".
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static int helper(int x) {
        return C0 - (x + C1);
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private static void sink(int v) {
    }
}
