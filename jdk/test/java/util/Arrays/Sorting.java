/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 6880672 6896573 6899694
 * @summary Exercise Arrays.sort
 * @build Sorting
 * @run main Sorting -shortrun
 *
 * @author Vladimir Yaroslavskiy
 * @author Jon Bentley
 * @author Josh Bloch
 */

import java.util.Arrays;
import java.util.Random;
import java.io.PrintStream;

public class Sorting {
    private static final PrintStream out = System.out;
    private static final PrintStream err = System.err;

    // Array lengths used in a long run (default)
    private static final int[] LONG_RUN_LENGTHS = {
        1, 2, 3, 5, 8, 13, 21, 34, 55, 100, 1000, 10000, 100000, 1000000 };

    // Array lengths used in a short run
    private static final int[] SHORT_RUN_LENGTHS = {
        1, 2, 3, 21, 55, 1000, 10000 };

    // Random initial values used in a long run (default)
    private static final long[] LONG_RUN_RANDOMS = {666, 0xC0FFEE, 999};

    // Random initial values used in a short run
    private static final long[] SHORT_RUN_RANDOMS = {666};

    public static void main(String[] args) {
        boolean shortRun = args.length > 0 && args[0].equals("-shortrun");
        long start = System.currentTimeMillis();

        if (shortRun) {
            testAndCheck(SHORT_RUN_LENGTHS, SHORT_RUN_RANDOMS);
        } else {
            testAndCheck(LONG_RUN_LENGTHS, LONG_RUN_RANDOMS);
        }
        long end = System.currentTimeMillis();

        out.format("\nPASSED in %d sec.\n", Math.round((end - start) / 1E3));
    }

    private static void testAndCheck(int[] lengths, long[] randoms) {
        testEmptyAndNullIntArray();
        testEmptyAndNullLongArray();
        testEmptyAndNullShortArray();
        testEmptyAndNullCharArray();
        testEmptyAndNullByteArray();
        testEmptyAndNullFloatArray();
        testEmptyAndNullDoubleArray();

        for (long random : randoms) {
            reset(random);

            for (int length : lengths) {
                testAndCheckWithCheckSum(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testAndCheckWithScrambling(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testAndCheckFloat(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testAndCheckDouble(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testAndCheckRange(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testAndCheckSubArray(length, random);
            }
            reset(random);

            for (int length : lengths) {
                testStable(length, random);
            }
        }
    }

    private static void testEmptyAndNullIntArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new int[] {});
        Arrays.sort(new int[] {}, 0, 0);

        try {
            Arrays.sort((int[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((int[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(int[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(int[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullLongArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new long[] {});
        Arrays.sort(new long[] {}, 0, 0);

        try {
            Arrays.sort((long[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((long[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(long[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(long[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullShortArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new short[] {});
        Arrays.sort(new short[] {}, 0, 0);

        try {
            Arrays.sort((short[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((short[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(short[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(short[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullCharArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new char[] {});
        Arrays.sort(new char[] {}, 0, 0);

        try {
            Arrays.sort((char[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((char[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(char[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(char[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullByteArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new byte[] {});
        Arrays.sort(new byte[] {}, 0, 0);

        try {
            Arrays.sort((byte[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((byte[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(byte[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(byte[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullFloatArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new float[] {});
        Arrays.sort(new float[] {}, 0, 0);

        try {
            Arrays.sort((float[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((float[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(float[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(float[]) shouldn't catch null array");
    }

    private static void testEmptyAndNullDoubleArray() {
        ourDescription = "Check empty and null array";
        Arrays.sort(new double[] {});
        Arrays.sort(new double[] {}, 0, 0);

        try {
            Arrays.sort((double[]) null);
        } catch (NullPointerException expected) {
            try {
                Arrays.sort((double[]) null, 0, 0);
            } catch (NullPointerException expected2) {
                return;
            }
            failed("Arrays.sort(double[],fromIndex,toIndex) shouldn't " +
                "catch null array");
        }
        failed("Arrays.sort(double[]) shouldn't catch null array");
    }

    private static void testAndCheckSubArray(int length, long random) {
        ourDescription = "Check sorting of subarray";
        int[] golden = new int[length];
        boolean newLine = false;

        for (int m = 1; m < length / 2; m *= 2) {
            newLine = true;
            int fromIndex = m;
            int toIndex = length - m;

            prepareSubArray(golden, fromIndex, toIndex, m);
            int[] test = golden.clone();

            for (TypeConverter converter : TypeConverter.values()) {
                out.println("Test 'subarray': " + converter +
                   " length = " + length + ", m = " + m);
                Object convertedGolden = converter.convert(golden);
                Object convertedTest = converter.convert(test);
                // outArray(test);
                sortSubArray(convertedTest, fromIndex, toIndex);
                // outArray(test);
                checkSubArray(convertedTest, fromIndex, toIndex, m);
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void testAndCheckRange(int length, long random) {
        ourDescription = "Check range check";
        int[] golden = new int[length];

        for (int m = 1; m < 2 * length; m *= 2) {
            for (int i = 1; i <= length; i++) {
                golden[i - 1] = i % m + m % i;
            }
            for (TypeConverter converter : TypeConverter.values()) {
                out.println("Test 'range': " + converter +
                   ", length = " + length + ", m = " + m);
                Object convertedGolden = converter.convert(golden);
                checkRange(convertedGolden, m);
            }
        }
        out.println();
    }

    private static void testStable(int length, long random) {
        ourDescription = "Check if sorting is stable";
        Pair[] a = build(length);

        out.println("Test 'stable': " + "random = " +  random +
            ", length = " + length);
        Arrays.sort(a);
        checkSorted(a);
        checkStable(a);
    }

    private static void checkSorted(Pair[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i].getKey() > a[i + 1].getKey()) {
                failed(i, "" + a[i].getKey(), "" + a[i + 1].getKey());
            }
        }
    }

    private static void checkStable(Pair[] a) {
        for (int i = 0; i < a.length / 4; ) {
            int key1 = a[i].getKey();
            int value1 = a[i++].getValue();
            int key2 = a[i].getKey();
            int value2 = a[i++].getValue();
            int key3 = a[i].getKey();
            int value3 = a[i++].getValue();
            int key4 = a[i].getKey();
            int value4 = a[i++].getValue();

            if (!(key1 == key2 && key2 == key3 && key3 == key4)) {
                failed("On position " + i + " must keys are different " +
                    key1 + ", " + key2 + ", " + key3 + ", " + key4);
            }
            if (!(value1 < value2 && value2 < value3 && value3 < value4)) {
                failed("Sorting is not stable at position " + i +
                    ". Second values have been changed: " +  value1 + ", " +
                    value2 + ", " + value3 + ", " + value4);
            }
        }
    }

    private static Pair[] build(int length) {
        Pair[] a = new Pair[length * 4];

        for (int i = 0; i < a.length; ) {
            int key = ourRandom.nextInt();
            a[i++] = new Pair(key, 1);
            a[i++] = new Pair(key, 2);
            a[i++] = new Pair(key, 3);
            a[i++] = new Pair(key, 4);
        }
        return a;
    }

    private static final class Pair implements Comparable<Pair> {
        Pair(int key, int value) {
            myKey = key;
            myValue = value;
        }

        int getKey() {
            return myKey;
        }

        int getValue() {
            return myValue;
        }

        public int compareTo(Pair pair) {
            if (myKey < pair.myKey) {
                return -1;
            }
            if (myKey > pair.myKey) {
                return 1;
            }
            return 0;
        }

        @Override
        public String toString() {
            return "(" + myKey + ", " + myValue + ")";
        }

        private int myKey;
        private int myValue;
    }

    private static void testAndCheckWithCheckSum(int length, long random) {
        ourDescription = "Check sorting with check sum";
        int[] golden = new int[length];

        for (int m = 1; m < 2 * length; m *= 2) {
            for (UnsortedBuilder builder : UnsortedBuilder.values()) {
                builder.build(golden, m);
                int[] test = golden.clone();

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'check sum': " + converter + " " +
                        builder + "random = " +  random + ", length = " +
                        length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    Object convertedTest = converter.convert(test);
                    sort(convertedTest);
                    checkWithCheckSum(convertedTest, convertedGolden);
                }
            }
        }
        out.println();
    }

    private static void testAndCheckWithScrambling(int length, long random) {
        ourDescription = "Check sorting with scrambling";
        int[] golden = new int[length];

        for (int m = 1; m <= 7; m++) {
            if (m > length) {
                break;
            }
            for (SortedBuilder builder : SortedBuilder.values()) {
                builder.build(golden, m);
                int[] test = golden.clone();
                scramble(test);

                for (TypeConverter converter : TypeConverter.values()) {
                    out.println("Test 'scrambling': " + converter + " " +
                       builder + "random = " +  random + ", length = " +
                       length + ", m = " + m);
                    Object convertedGolden = converter.convert(golden);
                    Object convertedTest = converter.convert(test);
                    sort(convertedTest);
                    compare(convertedTest, convertedGolden);
                }
            }
        }
        out.println();
    }

    private static void testAndCheckFloat(int length, long random) {
        ourDescription = "Check float sorting";
        float[] golden = new float[length];
        final int MAX = 10;
        boolean newLine = false;

        for (int a = 0; a <= MAX; a++) {
            for (int g = 0; g <= MAX; g++) {
                for (int z = 0; z <= MAX; z++) {
                    for (int n = 0; n <= MAX; n++) {
                        for (int p = 0; p <= MAX; p++) {
                            if (a + g + z + n + p > length) {
                                continue;
                            }
                            if (a + g + z + n + p < length) {
                                continue;
                            }
                            for (FloatBuilder builder : FloatBuilder.values()) {
                                out.println("Test 'float': random = " + random +
                                   ", length = " + length + ", a = " + a +
                                   ", g = " + g + ", z = " + z + ", n = " + n +
                                   ", p = " + p);
                                builder.build(golden, a, g, z, n, p);
                                float[] test = golden.clone();
                                scramble(test);
                                // outArray(test);
                                sort(test);
                                // outArray(test);
                                compare(test, golden, a, n, g);
                            }
                            newLine = true;
                        }
                    }
                }
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void testAndCheckDouble(int length, long random) {
        ourDescription = "Check double sorting";
        double[] golden = new double[length];
        final int MAX = 10;
        boolean newLine = false;

        for (int a = 0; a <= MAX; a++) {
            for (int g = 0; g <= MAX; g++) {
                for (int z = 0; z <= MAX; z++) {
                    for (int n = 0; n <= MAX; n++) {
                        for (int p = 0; p <= MAX; p++) {
                            if (a + g + z + n + p > length) {
                                continue;
                            }
                            if (a + g + z + n + p < length) {
                                continue;
                            }
                            for (DoubleBuilder builder : DoubleBuilder.values()) {
                                out.println("Test 'double': random = " + random +
                                   ", length = " + length + ", a = " + a + ", g = " +
                                   g + ", z = " + z + ", n = " + n + ", p = " + p);
                                builder.build(golden, a, g, z, n, p);
                                double[] test = golden.clone();
                                scramble(test);
                                // outArray(test);
                                sort(test);
                                // outArray(test);
                                compare(test, golden, a, n, g);
                            }
                            newLine = true;
                        }
                    }
                }
            }
        }
        if (newLine) {
            out.println();
        }
    }

    private static void prepareSubArray(int[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            a[i] = 0xBABA;
        }
        for (int i = fromIndex; i < toIndex; i++) {
            a[i] = -i + m;
        }
        for (int i = toIndex; i < a.length; i++) {
            a[i] = 0xDEDA;
        }
    }

    private static void scramble(int[] a) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, ourRandom.nextInt(a.length), ourRandom.nextInt(a.length));
        }
    }

    private static void scramble(float[] a) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, ourRandom.nextInt(a.length), ourRandom.nextInt(a.length));
        }
    }

    private static void scramble(double[] a) {
        for (int i = 0; i < a.length * 7; i++) {
            swap(a, ourRandom.nextInt(a.length), ourRandom.nextInt(a.length));
        }
    }

    private static void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static void swap(float[] a, int i, int j) {
        float t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static void swap(double[] a, int i, int j) {
        double t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    private static enum TypeConverter {
        INT {
            Object convert(int[] a) {
                return a.clone();
            }
        },
        LONG {
            Object convert(int[] a) {
                long[] b = new long[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (long) a[i];
                }
                return b;
            }
        },
        BYTE {
            Object convert(int[] a) {
                byte[] b = new byte[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (byte) a[i];
                }
                return b;
            }
        },
        SHORT {
            Object convert(int[] a) {
                short[] b = new short[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (short) a[i];
                }
                return b;
            }
        },
        CHAR {
            Object convert(int[] a) {
                char[] b = new char[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (char) a[i];
                }
                return b;
            }
        },
        FLOAT {
            Object convert(int[] a) {
                float[] b = new float[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (float) a[i];
                }
                return b;
            }
        },
        DOUBLE {
            Object convert(int[] a) {
                double[] b = new double[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = (double) a[i];
                }
                return b;
            }
        },
        INTEGER {
            Object convert(int[] a) {
                Integer[] b = new Integer[a.length];

                for (int i = 0; i < a.length; i++) {
                    b[i] = new Integer(a[i]);
                }
                return b;
            }
        };

        abstract Object convert(int[] a);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 9; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static enum FloatBuilder {
        SIMPLE {
            void build(float[] x, int a, int g, int z, int n, int p) {
                int fromIndex = 0;
                float negativeValue = -ourRandom.nextFloat();
                float positiveValue =  ourRandom.nextFloat();

                writeValue(x, negativeValue, fromIndex, n);
                fromIndex += n;

                writeValue(x, -0.0f, fromIndex, g);
                fromIndex += g;

                writeValue(x, 0.0f, fromIndex, z);
                fromIndex += z;

                writeValue(x, positiveValue, fromIndex, p);
                fromIndex += p;

                writeValue(x, Float.NaN, fromIndex, a);
            }
        };

        abstract void build(float[] x, int a, int g, int z, int n, int p);
    }

    private static enum DoubleBuilder {
        SIMPLE {
            void build(double[] x, int a, int g, int z, int n, int p) {
                int fromIndex = 0;
                double negativeValue = -ourRandom.nextFloat();
                double positiveValue =  ourRandom.nextFloat();

                writeValue(x, negativeValue, fromIndex, n);
                fromIndex += n;

                writeValue(x, -0.0d, fromIndex, g);
                fromIndex += g;

                writeValue(x, 0.0d, fromIndex, z);
                fromIndex += z;

                writeValue(x, positiveValue, fromIndex, p);
                fromIndex += p;

                writeValue(x, Double.NaN, fromIndex, a);
            }
        };

        abstract void build(double[] x, int a, int g, int z, int n, int p);
    }

    private static void writeValue(float[] a, float value, int fromIndex, int count) {
        for (int i = fromIndex; i < fromIndex + count; i++) {
            a[i] = value;
        }
    }

    private static void compare(float[] a, float[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; i++) {
            if (a[i] == a[i]) {
                failed("On position " + i + " must be NaN instead of " + a[i]);
            }
        }
        final int NEGATIVE_ZERO = Float.floatToIntBits(-0.0f);

        for (int i = numNeg; i < numNeg + numNegZero; i++) {
            if (NEGATIVE_ZERO != Float.floatToIntBits(a[i])) {
                failed("On position " + i + " must be -0.0f instead of " + a[i]);
            }
        }
        for (int i = 0; i < a.length - numNaN; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void writeValue(double[] a, double value, int fromIndex, int count) {
        for (int i = fromIndex; i < fromIndex + count; i++) {
            a[i] = value;
        }
    }

    private static void compare(double[] a, double[] b, int numNaN, int numNeg, int numNegZero) {
        for (int i = a.length - numNaN; i < a.length; i++) {
            if (a[i] == a[i]) {
                failed("On position " + i + " must be NaN instead of " + a[i]);
            }
        }
        final long NEGATIVE_ZERO = Double.doubleToLongBits(-0.0d);

        for (int i = numNeg; i < numNeg + numNegZero; i++) {
            if (NEGATIVE_ZERO != Double.doubleToLongBits(a[i])) {
                failed("On position " + i + " must be -0.0d instead of " + a[i]);
            }
        }
        for (int i = 0; i < a.length - numNaN; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static enum SortedBuilder {
        REPEATED {
            void build(int[] a, int m) {
                int period = a.length / m;
                int i = 0;
                int k = 0;

                while (true) {
                    for (int t = 1; t <= period; t++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = k;
                    }
                    if (i >= a.length) {
                        return;
                    }
                    k++;
                }
            }
        },

        ORGAN_PIPES {
            void build(int[] a, int m) {
                int i = 0;
                int k = m;

                while (true) {
                    for (int t = 1; t <= m; t++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = k;
                    }
                }
            }
        };

        abstract void build(int[] a, int m);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static enum UnsortedBuilder {
        RANDOM {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextInt();
                }
            }
        },
        ASCENDING {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m + i;
                }
            }
        },
        DESCENDING {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = a.length - m - i;
                }
            }
        },
        ALL_EQUAL {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = m;
                }
            }
        },
        SAW {
            void build(int[] a, int m) {
                int incCount = 1;
                int decCount = a.length;
                int i = 0;
                int period = m;
                m--;
                while (true) {
                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = incCount++;
                    }
                    period += m;

                    for (int k = 1; k <= period; k++) {
                        if (i >= a.length) {
                            return;
                        }
                        a[i++] = decCount--;
                    }
                    period += m;
                }
            }
        },
        REPEATED {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = i % m;
                }
            }
        },
        DUPLICATED {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextInt(m);
                }
            }
        },
        ORGAN_PIPES {
            void build(int[] a, int m) {
                int middle = a.length / (m + 1);

                for (int i = 0; i < middle; i++) {
                    a[i] = i;
                }
                for (int i = middle; i < a.length; i++) {
                    a[i] = a.length - i - 1;
                }
            }
        },
        STAGGER {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = (i * m + i) % a.length;
                }
            }
        },
        PLATEAU {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = Math.min(i, m);
                }
            }
        },
        SHUFFLE {
            void build(int[] a, int m) {
                for (int i = 0; i < a.length; i++) {
                    a[i] = ourRandom.nextBoolean() ? (ourFirst += 2) : (ourSecond += 2);
                }
            }
        };

        abstract void build(int[] a, int m);

        @Override public String toString() {
            String name = name();

            for (int i = name.length(); i < 12; i++) {
                name += " ";
            }
            return name;
        }
    }

    private static void compare(Object test, Object golden) {
        if (test instanceof int[]) {
            compare((int[]) test, (int[]) golden);
        } else if (test instanceof long[]) {
            compare((long[]) test, (long[]) golden);
        } else if (test instanceof short[]) {
            compare((short[]) test, (short[]) golden);
        } else if (test instanceof byte[]) {
            compare((byte[]) test, (byte[]) golden);
        } else if (test instanceof char[]) {
            compare((char[]) test, (char[]) golden);
        } else if (test instanceof float[]) {
            compare((float[]) test, (float[]) golden);
        } else if (test instanceof double[]) {
            compare((double[]) test, (double[]) golden);
        } else if (test instanceof Integer[]) {
            compare((Integer[]) test, (Integer[]) golden);
        } else {
            failed("Unknow type of array: " + test + " of class " +
                test.getClass().getName());
        }
    }

    private static void checkWithCheckSum(Object test, Object golden) {
        checkSorted(test);
        checkCheckSum(test, golden);
    }

    private static void failed(String message) {
        err.format("\n*** TEST FAILED - %s\n\n%s\n\n", ourDescription, message);
        throw new RuntimeException("Test failed - see log file for details");
    }

    private static void failed(int index, String value1, String value2) {
        failed("Array is not sorted at " + index + "-th position: " +
            value1 + " and " + value2);
    }

    private static void checkSorted(Object object) {
        if (object instanceof int[]) {
            checkSorted((int[]) object);
        } else if (object instanceof long[]) {
            checkSorted((long[]) object);
        } else if (object instanceof short[]) {
            checkSorted((short[]) object);
        } else if (object instanceof byte[]) {
            checkSorted((byte[]) object);
        } else if (object instanceof char[]) {
            checkSorted((char[]) object);
        } else if (object instanceof float[]) {
            checkSorted((float[]) object);
        } else if (object instanceof double[]) {
            checkSorted((double[]) object);
        } else if (object instanceof Integer[]) {
            checkSorted((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void compare(Integer[] a, Integer[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].intValue() != b[i].intValue()) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(int[] a, int[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(long[] a, long[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(short[] a, short[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(char[] a, char[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(float[] a, float[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void compare(double[] a, double[] b) {
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                failed(i, "" + a[i], "" + b[i]);
            }
        }
    }

    private static void checkSorted(Integer[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i].intValue() > a[i + 1].intValue()) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(int[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(long[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(short[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(byte[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(char[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(float[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkSorted(double[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }
    }

    private static void checkCheckSum(Object test, Object golden) {
        if (checkSum(test) != checkSum(golden)) {
            failed("It seems that original and sorted arrays are not identical");
        }
    }

    private static int checkSum(Object object) {
        if (object instanceof int[]) {
            return checkSum((int[]) object);
        } else if (object instanceof long[]) {
            return checkSum((long[]) object);
        } else if (object instanceof short[]) {
            return checkSum((short[]) object);
        } else if (object instanceof byte[]) {
            return checkSum((byte[]) object);
        } else if (object instanceof char[]) {
            return checkSum((char[]) object);
        } else if (object instanceof float[]) {
            return checkSum((float[]) object);
        } else if (object instanceof double[]) {
            return checkSum((double[]) object);
        } else if (object instanceof Integer[]) {
            return checkSum((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
            return -1;
        }
    }

    private static int checkSum(Integer[] a) {
        int checkXorSum = 0;

        for (Integer e : a) {
            checkXorSum ^= e.intValue();
        }
        return checkXorSum;
    }

    private static int checkSum(int[] a) {
        int checkXorSum = 0;

        for (int e : a) {
            checkXorSum ^= e;
        }
        return checkXorSum;
    }

    private static int checkSum(long[] a) {
        long checkXorSum = 0;

        for (long e : a) {
            checkXorSum ^= e;
        }
        return (int) checkXorSum;
    }

    private static int checkSum(short[] a) {
        short checkXorSum = 0;

        for (short e : a) {
            checkXorSum ^= e;
        }
        return (int) checkXorSum;
    }

    private static int checkSum(byte[] a) {
        byte checkXorSum = 0;

        for (byte e : a) {
            checkXorSum ^= e;
        }
        return (int) checkXorSum;
    }

    private static int checkSum(char[] a) {
        char checkXorSum = 0;

        for (char e : a) {
            checkXorSum ^= e;
        }
        return (int) checkXorSum;
    }

    private static int checkSum(float[] a) {
        int checkXorSum = 0;

        for (float e : a) {
            checkXorSum ^= (int) e;
        }
        return checkXorSum;
    }

    private static int checkSum(double[] a) {
        int checkXorSum = 0;

        for (double e : a) {
            checkXorSum ^= (int) e;
        }
        return checkXorSum;
    }

    private static void sort(Object object) {
        if (object instanceof int[]) {
            Arrays.sort((int[]) object);
        } else if (object instanceof long[]) {
            Arrays.sort((long[]) object);
        } else if (object instanceof short[]) {
            Arrays.sort((short[]) object);
        } else if (object instanceof byte[]) {
            Arrays.sort((byte[]) object);
        } else if (object instanceof char[]) {
            Arrays.sort((char[]) object);
        } else if (object instanceof float[]) {
            Arrays.sort((float[]) object);
        } else if (object instanceof double[]) {
            Arrays.sort((double[]) object);
        } else if (object instanceof Integer[]) {
            Arrays.sort((Integer[]) object);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void sortSubArray(Object object, int fromIndex, int toIndex) {
        if (object instanceof int[]) {
            Arrays.sort((int[]) object, fromIndex, toIndex);
        } else if (object instanceof long[]) {
            Arrays.sort((long[]) object, fromIndex, toIndex);
        } else if (object instanceof short[]) {
            Arrays.sort((short[]) object, fromIndex, toIndex);
        } else if (object instanceof byte[]) {
            Arrays.sort((byte[]) object, fromIndex, toIndex);
        } else if (object instanceof char[]) {
            Arrays.sort((char[]) object, fromIndex, toIndex);
        } else if (object instanceof float[]) {
            Arrays.sort((float[]) object, fromIndex, toIndex);
        } else if (object instanceof double[]) {
            Arrays.sort((double[]) object, fromIndex, toIndex);
        } else if (object instanceof Integer[]) {
            Arrays.sort((Integer[]) object, fromIndex, toIndex);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkSubArray(Object object, int fromIndex, int toIndex, int m) {
        if (object instanceof int[]) {
            checkSubArray((int[]) object, fromIndex, toIndex, m);
        } else if (object instanceof long[]) {
            checkSubArray((long[]) object, fromIndex, toIndex, m);
        } else if (object instanceof short[]) {
            checkSubArray((short[]) object, fromIndex, toIndex, m);
        } else if (object instanceof byte[]) {
            checkSubArray((byte[]) object, fromIndex, toIndex, m);
        } else if (object instanceof char[]) {
            checkSubArray((char[]) object, fromIndex, toIndex, m);
        } else if (object instanceof float[]) {
            checkSubArray((float[]) object, fromIndex, toIndex, m);
        } else if (object instanceof double[]) {
            checkSubArray((double[]) object, fromIndex, toIndex, m);
        } else if (object instanceof Integer[]) {
            checkSubArray((Integer[]) object, fromIndex, toIndex, m);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkSubArray(Integer[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i].intValue() != 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i].intValue() > a[i + 1].intValue()) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i].intValue() != 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(int[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(byte[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (byte) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (byte) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(long[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (long) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (long) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(char[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (char) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (char) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(short[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (short) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (short) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(float[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (float) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (float) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkSubArray(double[] a, int fromIndex, int toIndex, int m) {
        for (int i = 0; i < fromIndex; i++) {
            if (a[i] != (double) 0xBABA) {
                failed("Range sort changes left element on position " + i +
                    ": " + a[i] + ", must be " + 0xBABA);
            }
        }

        for (int i = fromIndex; i < toIndex - 1; i++) {
            if (a[i] > a[i + 1]) {
                failed(i, "" + a[i], "" + a[i + 1]);
            }
        }

        for (int i = toIndex; i < a.length; i++) {
            if (a[i] != (double) 0xDEDA) {
                failed("Range sort changes right element on position " + i +
                    ": " + a[i] + ", must be " + 0xDEDA);
            }
        }
    }

    private static void checkRange(Object object, int m) {
        if (object instanceof int[]) {
            checkRange((int[]) object, m);
        } else if (object instanceof long[]) {
            checkRange((long[]) object, m);
        } else if (object instanceof short[]) {
            checkRange((short[]) object, m);
        } else if (object instanceof byte[]) {
            checkRange((byte[]) object, m);
        } else if (object instanceof char[]) {
            checkRange((char[]) object, m);
        } else if (object instanceof float[]) {
            checkRange((float[]) object, m);
        } else if (object instanceof double[]) {
            checkRange((double[]) object, m);
        } else if (object instanceof Integer[]) {
            checkRange((Integer[]) object, m);
        } else {
            failed("Unknow type of array: " + object + " of class " +
                object.getClass().getName());
        }
    }

    private static void checkRange(Integer[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(int[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(long[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(byte[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(short[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(char[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(float[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void checkRange(double[] a, int m) {
        try {
            Arrays.sort(a, m + 1, m);

            failed("Sort does not throw IllegalArgumentException " +
                " as expected: fromIndex = " + (m + 1) +
                " toIndex = " + m);
        }
        catch (IllegalArgumentException iae) {
            try {
                Arrays.sort(a, -m, a.length);

                failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                    " as expected: fromIndex = " + (-m));
            }
            catch (ArrayIndexOutOfBoundsException aoe) {
                try {
                    Arrays.sort(a, 0, a.length + m);

                    failed("Sort does not throw ArrayIndexOutOfBoundsException " +
                        " as expected: toIndex = " + (a.length + m));
                }
                catch (ArrayIndexOutOfBoundsException aie) {
                    return;
                }
            }
        }
    }

    private static void prepareRandom(int[] a) {
        for (int i = 0; i < a.length; i++) {
            a[i] = ourRandom.nextInt();
        }
    }

    private static void reset(long seed) {
        ourRandom = new Random(seed);
        ourFirst = 0;
        ourSecond = 0;
    }

    private static void outArray(Object[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(int[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(float[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static void outArray(double[] a) {
        for (int i = 0; i < a.length; i++) {
            out.print(a[i] + " ");
        }
        out.println();
    }

    private static int ourFirst;
    private static int ourSecond;
    private static Random ourRandom;
    private static String ourDescription;
}
