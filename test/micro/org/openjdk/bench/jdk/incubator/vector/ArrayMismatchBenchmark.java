package org.openjdk.bench.jdk.incubator.vector;


import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.LongVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.BenchmarkParams;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
@Fork(jvmArgsPrepend = {"--add-modules=jdk.incubator.vector"})
public class ArrayMismatchBenchmark {

    @Param({"9", "257", "100000"})
    int size;

    @Param({"0.5", "1.0"})
    double prefix;

    byte[] byteData1;
    byte[] byteData2;

    int[] intData1;
    int[] intData2;

    long[] longData1;
    long[] longData2;

    float[] floatData1;
    float[] floatData2;

    static final VectorSpecies<Byte> BYTE_SPECIES_PREFERRED = ByteVector.SPECIES_PREFERRED;
    static final VectorSpecies<Integer> INT_SPECIES_PREFERRED = IntVector.SPECIES_PREFERRED;
    static final VectorSpecies<Float> FLOAT_SPECIES_PREFERRED = FloatVector.SPECIES_PREFERRED;
    static final VectorSpecies<Long> LONG_SPECIES_PREFERRED = LongVector.SPECIES_PREFERRED;

    static final Random random = new Random();

    private float[] createRandomFloats(int size) {
        float[] array = new float[size];
        for (int i = 0; i < size; i++)
            array[i] = random.nextFloat();
        return array;
    }

    @Setup
    public void setup(BenchmarkParams params) {
        int common = (int) (prefix * size);

        if (params.getBenchmark().endsWith("Byte")) {
            byteData1 = new byte[size];
            byteData2 = new byte[size];
            random.nextBytes(byteData1);
            random.nextBytes(byteData2);

            byte[] commonBytes = new byte[common];
            random.nextBytes(commonBytes);

            System.arraycopy(commonBytes, 0, byteData1, 0, common);
            System.arraycopy(commonBytes, 0, byteData2, 0, common);
        } else if (params.getBenchmark().endsWith("Int")) {
            intData1 = random.ints(size).toArray();
            intData2 = random.ints(size).toArray();

            int[] commonInts = random.ints(common).toArray();
            System.arraycopy(commonInts, 0, intData1, 0, common);
            System.arraycopy(commonInts, 0, intData2, 0, common);
        } else if (params.getBenchmark().endsWith("Float")) {
            floatData1 = createRandomFloats(size);
            floatData2 = createRandomFloats(size);

            float[] commonFloats = createRandomFloats(common);
            System.arraycopy(commonFloats, 0, floatData1, 0, common);
            System.arraycopy(commonFloats, 0, floatData2, 0, common);
        } else if (params.getBenchmark().endsWith("Long")) {
            longData1 = random.longs(size).toArray();
            longData2 = random.longs(size).toArray();

            long[] commonLongs = random.longs(common).toArray();
            System.arraycopy(commonLongs, 0, longData1, 0, common);
            System.arraycopy(commonLongs, 0, longData2, 0, common);
        }
    }

    @Benchmark
    public int mismatchIntrinsicByte() {
        return Arrays.mismatch(byteData1, byteData2);
    }

    @Benchmark
    public int mismatchVectorByte() {
        int length = Math.min(byteData1.length, byteData2.length);
        int index = 0;
        for (; index < BYTE_SPECIES_PREFERRED.loopBound(length); index += BYTE_SPECIES_PREFERRED.length()) {
            ByteVector vector1 = ByteVector.fromArray(BYTE_SPECIES_PREFERRED, byteData1, index);
            ByteVector vector2 = ByteVector.fromArray(BYTE_SPECIES_PREFERRED, byteData2, index);
            VectorMask<Byte> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (byteData1[i] != byteData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicInt() {
        return Arrays.mismatch(intData1, intData2);
    }

    @Benchmark
    public int mismatchVectorInt() {
        int length = Math.min(intData1.length, intData2.length);
        int index = 0;
        for (; index < INT_SPECIES_PREFERRED.loopBound(length); index += INT_SPECIES_PREFERRED.length()) {
            IntVector vector1 = IntVector.fromArray(INT_SPECIES_PREFERRED, intData1, index);
            IntVector vector2 = IntVector.fromArray(INT_SPECIES_PREFERRED, intData2, index);
            VectorMask<Integer> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (intData1[i] != intData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicFloat() {
        return Arrays.mismatch(floatData1, floatData2);
    }

    @Benchmark
    public int mismatchVectorFloat() {
        int length = Math.min(floatData1.length, floatData2.length);
        int index = 0;
        for (; index < FLOAT_SPECIES_PREFERRED.loopBound(length); index += FLOAT_SPECIES_PREFERRED.length()) {
            FloatVector vector1 = FloatVector.fromArray(FLOAT_SPECIES_PREFERRED, floatData1, index);
            FloatVector vector2 = FloatVector.fromArray(FLOAT_SPECIES_PREFERRED, floatData2, index);
            VectorMask<Float> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (floatData1[i] != floatData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

    @Benchmark
    public int mismatchIntrinsicLong() {
        return Arrays.mismatch(longData1, longData2);
    }

    @Benchmark
    public int mismatchVectorLong() {
        int length = Math.min(longData1.length, longData2.length);
        int index = 0;
        for (; index < LONG_SPECIES_PREFERRED.loopBound(length); index += LONG_SPECIES_PREFERRED.length()) {
            LongVector vector1 = LongVector.fromArray(LONG_SPECIES_PREFERRED, longData1, index);
            LongVector vector2 = LongVector.fromArray(LONG_SPECIES_PREFERRED, longData2, index);
            VectorMask<Long> mask = vector1.compare(VectorOperators.NE, vector2);
            if (mask.anyTrue()) {
                return index + mask.firstTrue();
            }
        }
        // process the tail
        int mismatch = -1;
        for (int i = index; i < length; ++i) {
            if (longData1[i] != longData2[i]) {
                mismatch = i;
                break;
            }
        }
        return mismatch;
    }

}

