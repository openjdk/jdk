package org.openjdk;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = "-XX:TieredStopAtLevel=1")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(Threads.MAX)
@State(Scope.Benchmark)
public class SecondarySuperCache {

    // This test targets C1 specifically, to enter the interesting code path
    // without heavily optimizing compiler like C2 optimizing based on profiles,
    // or folding the instanceof checks.

    interface IA {}
    interface IB {}
    interface I extends IA, IB {}
    public class C1 implements I {}
    public class C2 implements I {}

    I c1, c2;

    @Setup
    public void setup() {
        c1 = new C1();
        c2 = new C2();
    }

    @Benchmark
    public void contended(Blackhole bh) {
        bh.consume(c1 instanceof IA);
        bh.consume(c2 instanceof IA);
        bh.consume(c1 instanceof IB);
        bh.consume(c2 instanceof IB);
    }

    @Benchmark
    public void uncontended(Blackhole bh) {
        bh.consume(c1 instanceof IA);
        bh.consume(c1 instanceof IA);
        bh.consume(c2 instanceof IB);
        bh.consume(c2 instanceof IB);
    }

}
