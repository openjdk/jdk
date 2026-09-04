/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.intrinsics;

/*
 * @test
 * @library /test/lib
 * @requires vm.opt.final.UseVectorizedMismatchIntrinsic == true
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.util
 * @run main compiler.intrinsics.VectorizedMismatchTest
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import jdk.internal.misc.Unsafe;
import jdk.internal.util.ArraysSupport;
import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class VectorizedMismatchTest {

    static abstract class Test {
        static final int fill = -1;
        static final int mismatch = 0;

        static final Test tests[] = {
            new BooleanTest(), new ByteTest(), new ShortTest(), new CharTest(), new IntTest(),
            new FloatTest(), new LongTest(), new DoubleTest(), new LoopUnswitch(), new LoopHoist()
        };

        public static void main(String[] args) {
            for (int i = 0; i < 20_000; i++) {
                for (Test t : tests) {
                    t.test();
                }
            }
        }

        abstract void test();
    }

    /* ==================================================================================== */

    static class BooleanTest extends Test {
        boolean[] array = new boolean[128];
        long offset = Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_BOOLEAN_INDEX_SCALE;

        {
            Arrays.fill(array, fill != 0);
        }

        void testConstantLengthMatch(int length) {
            boolean[] obja = array;
            boolean[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected boolean arrays of length " + length
                        + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            boolean[] obja = array;
            boolean[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = mismatch != 0;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected boolean arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength64() {
            testConstantLengthMatch(64);
            testConstantLengthMismatch(64);
        }

        void testConstantLength128() {
            testConstantLengthMatch(128);
            testConstantLengthMismatch(128);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength64();
            testConstantLength128();
        }
    }

    /* ==================================================================================== */

    static class ByteTest extends Test {
        byte[] array = new byte[128];
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE;

        {
            Arrays.fill(array, (byte) fill);
        }

        void testConstantLengthMatch(int length) {
            byte[] obja = array;
            byte[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException(
                        "Expected byte arrays of length " + length + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            byte[] obja = array;
            byte[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (byte) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected byte arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength64() {
            testConstantLengthMatch(64);
            testConstantLengthMismatch(64);
        }

        void testConstantLength128() {
            testConstantLengthMatch(128);
            testConstantLengthMismatch(128);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength64();
            testConstantLength128();
        }
    }

    /* ==================================================================================== */

    static class ShortTest extends Test {
        short[] array = new short[64];
        long offset = Unsafe.ARRAY_SHORT_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_SHORT_INDEX_SCALE;

        {
            Arrays.fill(array, (short) fill);
        }

        void testConstantLengthMatch(int length) {
            short[] obja = array;
            short[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected short arrays of length " + length
                        + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            short[] obja = array;
            short[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (short) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected short arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength32() {
            testConstantLengthMatch(32);
            testConstantLengthMismatch(32);
        }

        void testConstantLength64() {
            testConstantLengthMatch(64);
            testConstantLengthMismatch(64);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength32();
            testConstantLength64();
        }
    }

    /* ==================================================================================== */

    static class CharTest extends Test {
        char[] array = new char[64];
        long offset = Unsafe.ARRAY_CHAR_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_CHAR_INDEX_SCALE;

        {
            Arrays.fill(array, (char) fill);
        }

        void testConstantLengthMatch(int length) {
            char[] obja = array;
            char[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException(
                        "Expected char arrays of length " + length + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            char[] obja = array;
            char[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (char) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected char arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength32() {
            testConstantLengthMatch(32);
            testConstantLengthMismatch(32);
        }

        void testConstantLength64() {
            testConstantLengthMatch(64);
            testConstantLengthMismatch(64);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength32();
            testConstantLength64();
        }
    }

    /* ==================================================================================== */

    static class IntTest extends Test {
        int[] array = new int[32];
        long offset = Unsafe.ARRAY_INT_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_INT_INDEX_SCALE;

        {
            Arrays.fill(array, (int) fill);
        }

        void testConstantLengthMatch(int length) {
            int[] obja = array;
            int[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException(
                        "Expected int arrays of length " + length + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            int[] obja = array;
            int[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (int) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected int arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength16() {
            testConstantLengthMatch(16);
            testConstantLengthMismatch(16);
        }

        void testConstantLength32() {
            testConstantLengthMatch(32);
            testConstantLengthMismatch(32);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength16();
            testConstantLength32();
        }
    }

    /* ==================================================================================== */

    static class FloatTest extends Test {
        float[] array = new float[32];
        long offset = Unsafe.ARRAY_FLOAT_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_FLOAT_INDEX_SCALE;

        {
            Arrays.fill(array, (float) fill);
        }

        void testConstantLengthMatch(int length) {
            float[] obja = array;
            float[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected float arrays of length " + length
                        + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            float[] obja = array;
            float[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (float) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected float arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength16() {
            testConstantLengthMatch(16);
            testConstantLengthMismatch(16);
        }

        void testConstantLength32() {
            testConstantLengthMatch(32);
            testConstantLengthMismatch(32);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength16();
            testConstantLength32();
        }
    }

    /* ==================================================================================== */

    static class LongTest extends Test {
        long[] array = new long[16];
        long offset = Unsafe.ARRAY_LONG_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_LONG_INDEX_SCALE;

        {
            Arrays.fill(array, (long) fill);
        }

        void testConstantLengthMatch(int length) {
            long[] obja = array;
            long[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException(
                        "Expected long arrays of length " + length + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            long[] obja = array;
            long[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (long) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected long arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength8() {
            testConstantLengthMatch(8);
            testConstantLengthMismatch(8);
        }

        void testConstantLength16() {
            testConstantLengthMatch(16);
            testConstantLengthMismatch(16);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength8();
            testConstantLength16();
        }
    }

    /* ==================================================================================== */

    static class DoubleTest extends Test {
        double[] array = new double[16];
        long offset = Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_DOUBLE_INDEX_SCALE;

        {
            Arrays.fill(array, (double) fill);
        }

        void testConstantLengthMatch(int length) {
            double[] obja = array;
            double[] objb = array.clone();
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != -1 && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected double arrays of length " + length
                        + " to match but got " + result);
            }
        }

        void testConstantLengthMismatch(int length) {
            double[] obja = array;
            double[] objb = array.clone();
            int mismatchPos = length - 1;
            objb[mismatchPos] = (double) mismatch;
            int result = ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, length,
                    scale);
            if (result != mismatchPos && result != -(1 + length % Long.BYTES)) {
                throw new RuntimeException("Expected double arrays of length " + length
                        + " to mismatch at " + mismatchPos + " but got " + result);
            }
        }

        void testConstantLength0() {
            testConstantLengthMatch(0);
        }

        void testConstantLength1() {
            testConstantLengthMatch(1);
            testConstantLengthMismatch(1);
        }

        void testConstantLength8() {
            testConstantLengthMatch(8);
            testConstantLengthMismatch(8);
        }

        void testConstantLength16() {
            testConstantLengthMatch(16);
            testConstantLengthMismatch(16);
        }

        void test() {
            testConstantLength0();
            testConstantLength1();
            testConstantLength8();
            testConstantLength16();
        }
    }

    /* ==================================================================================== */

    static class LoopUnswitch extends Test {
        byte[] byteA = new byte[32];
        byte[] byteB = new byte[32];
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE;

        int testLoopUnswitch(int length) {

            int acc = 0;
            for (int i = 0; i < 32; i++) {
                acc += ArraysSupport.vectorizedMismatch(byteA, offset, byteB, offset, length,
                        scale);
            }
            return acc;
        }

        void test() {
            testLoopUnswitch(32);
        }
    }

    /* ==================================================================================== */

    static class LoopHoist extends Test {
        byte[] byteA = new byte[128];
        byte[] byteB = new byte[128];
        long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
        int scale = ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE;

        int testLoopHoist(int length, int stride) {

            int acc = 0;

            for (int i = 0; i < 32; i += stride) {
                acc += ArraysSupport.vectorizedMismatch(byteA, offset, byteB, offset, length,
                        scale);
            }
            return acc;
        }

        void test() {
            testLoopHoist(128, 2);
        }
    }

    /* ==================================================================================== */

    static class ClassInitTest {
        static final int LENGTH = 64;
        static final int RESULT;
        static {
            byte[] arr1 = new byte[LENGTH];
            byte[] arr2 = new byte[LENGTH];
            for (int i = 0; i < 20_000; i++) {
                test(arr1, arr2);
            }
            RESULT = test(arr1, arr2);
        }

        static int test(byte[] obja, byte[] objb) {
            long offset = Unsafe.ARRAY_BYTE_BASE_OFFSET;
            int scale = ArraysSupport.LOG2_ARRAY_BYTE_INDEX_SCALE;
            // LENGTH is not considered a constant
            return ArraysSupport.vectorizedMismatch(obja, offset, objb, offset, LENGTH, scale);
        }
    }

    int testConstantBeingInitialized() {
        return ClassInitTest.RESULT; // trigger class initialization
    }

    /* ==================================================================================== */

    // Default to 1/4 of the CPUs, and allow users to override.
    static final int MAX_PARALLELISM = Integer.getInteger("maxParallelism",
            Math.max(1, Runtime.getRuntime().availableProcessors() / 4));

    private static List<String> mix(List<String> o, String... mix) {
        List<String> n = new ArrayList<>(o);
        for (String m : mix) {
            n.add(m);
        }
        return n;
    }

    public static void main(String[] args) throws Exception {
        List<String> baseConfig = List.of("-XX:CompileCommand=quiet",
                "-XX:CompileCommand=compileonly,*::test*", "-Xbatch",
                "--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED",
                "--add-exports=java.base/jdk.internal.util=ALL-UNNAMED");
        List<String> c1BaseConfig = Stream.concat(baseConfig.stream(),
                Stream.of("-XX:+TieredCompilation", "-XX:TieredStopAtLevel=3")).toList();
        List<String> c2BaseConfig = Stream
                .concat(baseConfig.stream(), Stream.of("-XX:-TieredCompilation")).toList();

        List<List<String>> configs = new ArrayList<>();
        configs.add(new ArrayList<>(c1BaseConfig));
        configs.add(new ArrayList<>(c2BaseConfig));

        if (Platform.isX64()) {
            for (List<String> config : configs) {
                config.add("-XX:UseAVX=3");
            }

            List<String> zeroAVX3ThresholdConfig = new ArrayList<>(c2BaseConfig);
            zeroAVX3ThresholdConfig.add("-XX:+UnlockDiagnosticVMOptions");
            zeroAVX3ThresholdConfig.add("-XX:UseAVX=3");
            zeroAVX3ThresholdConfig.add("-XX:AVX3Threshold=0");
            configs.add(zeroAVX3ThresholdConfig);
        }

        ArrayList<Fork> forks = new ArrayList<>();
        int jobs = 0;

        for (List<String> c : configs) {
            ProcessBuilder pb = ProcessTools.createTestJavaProcessBuilder(
                    mix(c, "compiler.intrinsics.VectorizedMismatchTest$Test"));
            Process p = pb.start();
            forks.add(new Fork(p, new OutputAnalyzer(p)));
            jobs++;

            // Wait for the completion of other jobs
            while (jobs >= MAX_PARALLELISM) {
                Fork f = findDone(forks);
                if (f != null) {
                    OutputAnalyzer oa = f.oa();
                    oa.shouldHaveExitValue(0);
                    forks.remove(f);
                    jobs--;
                } else {
                    // Nothing is done, wait a little.
                    Thread.sleep(200);
                }
            }
        }

        // Drain the rest
        for (Fork f : forks) {
            OutputAnalyzer oa = f.oa();
            oa.shouldHaveExitValue(0);
        }
    }

    private static record Fork(Process p, OutputAnalyzer oa) { }

    private static Fork findDone(List<Fork> forks) {
        for (Fork f : forks) {
            if (!f.p().isAlive()) {
                return f;
            }
        }
        return null;
    }
}
