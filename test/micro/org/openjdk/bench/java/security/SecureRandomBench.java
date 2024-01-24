package org.openjdk.bench.java.security;

import org.openjdk.jmh.annotations.*;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 3)
public class SecureRandomBench {

    @Benchmark
    public SecureRandom newSecureRandom() throws Exception {
        return new SecureRandom();
    }
}
