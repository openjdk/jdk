package org.openjdk.bench.java.math;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.function.BinaryOperator;

/**
 * Benchmark for checking performance difference between
 * sequential and parallel multiply methods in BigInteger,
 * using a large Fibonacci calculation of up to n = 100 million.
 *
 * @author Heinz Kabutz, heinz@javaspecialists.eu
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(value = 2)
@Warmup(iterations = 2)
@Measurement(iterations = 2) // only 2 iterations because each one takes very long
@State(Scope.Thread)
public class BigIntegerParallelMultiply {
    private static BigInteger fibonacci(int n, BinaryOperator<BigInteger> multiplyOperator) {
        if (n == 0) return BigInteger.ZERO;
        if (n == 1) return BigInteger.ONE;

        int half = (n + 1) / 2;
        BigInteger f0 = fibonacci(half - 1, multiplyOperator);
        BigInteger f1 = fibonacci(half, multiplyOperator);
        if (n % 2 == 1) {
            BigInteger b0 = multiplyOperator.apply(f0, f0);
            BigInteger b1 = multiplyOperator.apply(f1, f1);
            return b0.add(b1);
        } else {
            BigInteger b0 = f0.shiftLeft(1).add(f1);
            return multiplyOperator.apply(b0, f1);
        }
    }

    @Param({"1000000", "10000000", "100000000"})
    private int n;

    @Benchmark
    public void multiply() {
        fibonacci(n, BigInteger::multiply);
    }

    @Benchmark
    public void parallelMultiply() {
        fibonacci(n, BigInteger::parallelMultiply);
    }
}