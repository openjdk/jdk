package org.openjdk.bench.java.lang;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@Warmup(iterations = 3, time = 5)
@Measurement(iterations = 4, time = 5)
@Fork(2)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
public class VectorBitConversion {
    @Param({"2048"})
    public int size;

    @Param("0")
    private int seed;
    private Random r = new Random(seed);

    private double[] doubles;
    private float[] floats;
    private int[] ints;
    private long[] longs;
    private double[] resultDoubles;
    private float[] resultFloats;
    private int[] resultInts;
    private long[] resultLongs;

    @Setup
    public void init() {
        resultDoubles = new double[size];
        resultFloats = new float[size];
        resultInts = new int[size];
        resultLongs = new long[size];

        doubles = new double[size];
        for (int i = 0; i < size; i++) {
            doubles[i] = r.nextDouble();
        }
        floats = new float[size];
        for (int i = 0; i < size; i++) {
            floats[i] = r.nextFloat();
        }
        ints = new int[size];
        for (int i = 0; i < size; i++) {
            ints[i] = r.nextInt();
        }
        longs = new long[size];
        for (int i = 0; i < size; i++) {
            longs[i] = r.nextLong();
        }
    }

    @Benchmark
    public long[] doubleToLongBits() {
        for (int i = 0; i < doubles.length; i++) {
            final double aDouble = doubles[i];
            final long bits = Double.doubleToLongBits(aDouble);
            resultLongs[i] = bits;
        }
        return resultLongs;
    }

    @Benchmark
    public long[] doubleToRawLongBits() {
        for (int i = 0; i < doubles.length; i++) {
            final double v = doubles[i];
            final long bits = Double.doubleToRawLongBits(v);
            resultLongs[i] = bits;
        }
        return resultLongs;
    }

    @Benchmark
    public int[] floatToIntBits() {
        for (int i = 0; i < floats.length; i++) {
            final float aFloat = floats[i];
            final int bits = Float.floatToIntBits(aFloat);
            resultInts[i] = bits;
        }
        return resultInts;
    }

    @Benchmark
    public int[] floatToRawIntBits() {
        for (int i = 0; i < floats.length; i++) {
            final float aFloat = floats[i];
            final int bits = Float.floatToRawIntBits(aFloat);
            resultInts[i] = bits;
        }
        return resultInts;
    }

    @Benchmark
    public float[] intBitsToFloat() {
        for (int i = 0; i < ints.length; i++) {
            final int bits = ints[i];
            final float v = Float.intBitsToFloat(bits);
            resultFloats[i] = v;
        }
        return resultFloats;
    }

    @Benchmark
    public double[] longBitsToDouble() {
        for (int i = 0; i < longs.length; i++) {
            final long bits = longs[i];
            final double v = Double.longBitsToDouble(bits);
            resultDoubles[i] = v;
        }
        return resultDoubles;
    }
}
